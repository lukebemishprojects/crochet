package dev.lukebemish.crochet.internal.metadata.pistonmeta;

import com.google.gson.stream.JsonReader;
import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.CrochetRepositoriesPlugin;
import dev.lukebemish.crochet.internal.metadata.MetadataUtils;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.MutableVariantFilesMetadata;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@CacheableRule
public abstract class PistonMetaMetadataRule implements ComponentMetadataRule {
    public static final String MINECRAFT_DEPENDENCIES = "minecraft-dependencies";
    public static final String MINECRAFT_DEPENDENCIES_NATIVES = "minecraft-dependencies-natives";
    public static final String MINECRAFT_DATA_ARTIFACT = "minecraft-data-artifact";
    public static final String MINECRAFT_META_ARTIFACT = "minecraft-meta-artifact";
    public static final String MINECRAFT = "minecraft";

    private static final Set<String> NAMES = Set.of(MINECRAFT_DEPENDENCIES, MINECRAFT_DEPENDENCIES_NATIVES, MINECRAFT);

    @Inject
    public PistonMetaMetadataRule() {}

    @Inject
    protected abstract RepositoryResourceAccessor getRepositoryResourceAccessor();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Override
    public void execute(ComponentMetadataContext context) {
        var details = context.getDetails();
        // We have natives as a separate module because the metadata rule API does not currently support capabilities fully
        if (CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP.equals(details.getId().getGroup()) && NAMES.contains(details.getId().getName())) {
            var versionString = details.getId().getVersion();
            boolean isNatives = MINECRAFT_DEPENDENCIES_NATIVES.equals(details.getId().getName());
            boolean isDependencies = MINECRAFT_DEPENDENCIES.equals(details.getId().getName()) || isNatives;
            details.setStatusScheme(List.of("old_alpha", "old_beta", "snapshot", "release"));
            details.allVariants(v -> {
                v.withFiles(MutableVariantFilesMetadata::removeAllFiles);
            });
            details.setChanging(false);
            getRepositoryResourceAccessor().withResource(VersionManifest.VERSION_MANIFEST, versionManifestStream -> {
                try (var reader = new JsonReader(new InputStreamReader(versionManifestStream))) {
                    VersionManifest manifest = VersionManifest.GSON.fromJson(reader, VersionManifest.class);
                    var versionEntry = manifest.versions().stream()
                        .filter(v -> v.id().equals(versionString))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Version not found in piston-meta version manifest; may not exist: " + versionString));
                    details.setStatus(versionEntry.type());
                    if (!versionEntry.url().startsWith(VersionManifest.PISTON_META_URL)) {
                        throw new IllegalStateException("Version URL not from piston-meta " + versionEntry.url() + " for " + versionString);
                    }
                    var relativeVersionUrl = versionEntry.url().substring(VersionManifest.PISTON_META_URL.length());
                    getRepositoryResourceAccessor().withResource(relativeVersionUrl, versionStream -> {
                        Version version;
                        try (var versionReader = new JsonReader(new InputStreamReader(versionStream))) {
                            version = VersionManifest.GSON.fromJson(versionReader, Version.class);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        var javaVersion = version.javaVersion().majorVersion();
                        if (isDependencies) {
                            List<String> clientDeps = new ArrayList<>();
                            List<String> serverDeps = new ArrayList<>();
                            Map<String, List<String>> clientNativeDeps = new HashMap<>();
                            for (var library : version.libraries()) {
                                List<String> allow = library.rules() == null ? List.of() : library.rules().stream()
                                    .filter(r -> r.action() == Version.Rule.Action.ALLOW)
                                    .map(Version.Rule::os)
                                    .filter(Objects::nonNull)
                                    .map(Version.Rule.OsDetails::name)
                                    .filter(Objects::nonNull)
                                    .toList();
                                List<String> disallow = library.rules() == null ? List.of() : library.rules().stream()
                                    .filter(r -> r.action() == Version.Rule.Action.DISALLOW)
                                    .map(Version.Rule::os)
                                    .filter(Objects::nonNull)
                                    .map(Version.Rule.OsDetails::name)
                                    .filter(Objects::nonNull)
                                    .toList();

                                if (library.downloads() != null && library.downloads().artifact() != null) {
                                    if (allow.isEmpty() && disallow.isEmpty()) {
                                        clientDeps.add(library.name());
                                    } else {
                                        List<String> platforms = new ArrayList<>(allow.isEmpty() ? List.of("windows", "linux", "mac") : allow);
                                        platforms.removeAll(disallow);
                                        for (var platform : platforms) {
                                            clientNativeDeps.computeIfAbsent(platform, k -> new ArrayList<>()).add(library.name());
                                        }
                                    }
                                }

                                if (library.natives() != null) {
                                    library.natives().forEach((os, classifier) -> {
                                        clientNativeDeps.computeIfAbsent(os, k -> new ArrayList<>()).add(library.name() + ":" + classifier);
                                    });
                                }
                            }

                            if (!isNatives) {
                                var serverDownload = version.downloads().get("server");
                                var relativeUrl = serverDownload.url().substring(VersionManifest.PISTON_DATA_URL.length());
                                serverDeps.add(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":" + ServerDependenciesMetadataRule.MINECRAFT_SERVER_DEPENDENCIES + ":" + relativeUrl);
                            }

                            if (!isNatives) {
                                details.addVariant("clientCompileDependencies", v -> {
                                    v.withDependencies(deps -> {
                                        List<String> fullDeps = new ArrayList<>(clientDeps);
                                        clientNativeDeps.getOrDefault("osx", List.of()).forEach(dep -> {
                                            if (dep.startsWith("ca.weblite:java-objc-bridge")) {
                                                fullDeps.add(dep);
                                            }
                                        });
                                        MetadataUtils.depsOf(fullDeps, deps, false);
                                    });
                                    v.attributes(attributes -> {
                                        attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_API));
                                        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion);
                                        attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                                    });
                                });
                                details.addVariant("clientRuntimeDependencies", v -> {
                                    v.withDependencies(deps -> {
                                        MetadataUtils.depsOf(clientDeps, deps, false);
                                        deps.add(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":" + MINECRAFT_DEPENDENCIES_NATIVES, dep -> {
                                            dep.version(depVersion -> {
                                                depVersion.strictly(versionString);
                                            });
                                            dep.endorseStrictVersions();
                                        });
                                    });
                                    v.attributes(attributes -> {
                                        attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                                        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion);
                                        attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                                    });
                                });
                                details.addVariant("serverCompileDependencies", v -> {
                                    v.withDependencies(deps -> {
                                        // Server deps are just the server-jar-manifest generated dependency
                                        MetadataUtils.depsOf(serverDeps, deps, true);
                                    });
                                    v.attributes(attributes -> {
                                        attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_API));
                                        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion);
                                        attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "server");
                                    });
                                });
                                details.addVariant("serverRuntimeDependencies", v -> {
                                    v.withDependencies(deps -> {
                                        // Server deps are just the server-jar-manifest generated dependency
                                        MetadataUtils.depsOf(serverDeps, deps, true);
                                    });
                                    v.attributes(attributes -> {
                                        attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                                        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion);
                                        attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "server");
                                    });
                                });
                            } else {
                                clientNativeDeps.forEach((os, deps) -> {
                                    details.addVariant(os + "NativeDependencies", v -> {
                                        v.withDependencies(dep -> {
                                            MetadataUtils.depsOf(deps, dep, false);
                                        });
                                        v.attributes(attributes -> {
                                            attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.NATIVE_LINK));
                                            attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion);
                                            attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                                            attributes.attribute(CrochetPlugin.NEO_OPERATING_SYSTEM_ATTRIBUTE, os);
                                        });
                                    });
                                });
                            }
                        } else if (MINECRAFT.equals(details.getId().getName())) {
                            var clientArtifact = version.downloads().get("client");
                            var serverArtifact = version.downloads().get("server");
                            if (clientArtifact == null && serverArtifact == null) {
                                throw new IllegalStateException("No artifact found for " + versionString);
                            }
                            if (clientArtifact != null) {
                                details.addVariant("clientArtifact", v -> {
                                    v.attributes(attributes -> {
                                        attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                                        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, getObjects().named(LibraryElements.class, LibraryElements.JAR));
                                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category.class, Category.LIBRARY));
                                        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion);
                                        attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                                    });
                                    if (!clientArtifact.url().startsWith(VersionManifest.PISTON_DATA_URL)) {
                                        throw new IllegalStateException("Artifact URL not from piston-data " + clientArtifact.url() + " for " + versionString);
                                    }
                                    var relativePath = clientArtifact.url().substring(VersionManifest.PISTON_DATA_URL.length());
                                    v.withDependencies(deps -> {
                                        var extension = relativePath.substring(relativePath.lastIndexOf('.') + 1);
                                        deps.add(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":"+MINECRAFT_DATA_ARTIFACT+"@"+extension, dep -> {
                                            dep.version(depVersion -> {
                                                depVersion.strictly(relativePath);
                                            });
                                        });
                                        deps.add(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP + ":" + MINECRAFT_DEPENDENCIES, dep -> {
                                            dep.version(depVersion -> {
                                                depVersion.strictly(versionString);
                                            });
                                        });
                                    });
                                });
                            }
                            if (serverArtifact != null) {
                                details.addVariant("serverArtifact", v -> {
                                    v.attributes(attributes -> {
                                        attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                                        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, getObjects().named(LibraryElements.class, LibraryElements.JAR));
                                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category.class, Category.LIBRARY));
                                        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion);
                                        attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "server");
                                    });
                                    if (!serverArtifact.url().startsWith(VersionManifest.PISTON_DATA_URL)) {
                                        throw new IllegalStateException("Artifact URL not from piston-data " + serverArtifact.url() + " for " + versionString);
                                    }
                                    var relativePath = serverArtifact.url().substring(VersionManifest.PISTON_DATA_URL.length());
                                    v.withDependencies(deps -> {
                                        var extension = relativePath.substring(relativePath.lastIndexOf('.') + 1);
                                        deps.add(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":"+MINECRAFT_DATA_ARTIFACT+"@"+extension, dep -> {
                                            dep.version(depVersion -> {
                                                depVersion.strictly(relativePath);
                                            });
                                        });
                                        deps.add(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":"+MINECRAFT_DEPENDENCIES, dep -> {
                                            dep.version(depVersion -> {
                                                depVersion.strictly(versionString);
                                            });
                                        });
                                    });
                                });
                            }
                            var clientMappings = version.downloads().get("client_mappings");
                            var serverMappings = version.downloads().get("server_mappings");
                            if (clientMappings != null) {
                                details.addVariant("clientMappings", v -> {
                                    v.attributes(attributes -> {
                                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category.class, "mappings"));
                                        attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                                    });
                                    if (!clientMappings.url().startsWith(VersionManifest.PISTON_DATA_URL)) {
                                        throw new IllegalStateException("Mappings URL not from piston-data " + clientMappings.url() + " for " + versionString);
                                    }
                                    var relativePath = clientMappings.url().substring(VersionManifest.PISTON_DATA_URL.length());
                                    v.withDependencies(deps -> {
                                        var extension = relativePath.substring(relativePath.lastIndexOf('.') + 1);
                                        deps.add(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":"+MINECRAFT_DATA_ARTIFACT+"@"+extension, dep -> {
                                            dep.version(depVersion -> {
                                                depVersion.strictly(relativePath);
                                            });
                                        });
                                    });
                                });
                            }
                            if (serverMappings != null) {
                                details.addVariant("serverMappings", v -> {
                                    v.attributes(attributes -> {
                                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category.class, "mappings"));
                                        attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "server");
                                    });
                                    if (!serverMappings.url().startsWith(VersionManifest.PISTON_DATA_URL)) {
                                        throw new IllegalStateException("Mappings URL not from piston-data " + serverMappings.url() + " for " + versionString);
                                    }
                                    var relativePath = serverMappings.url().substring(VersionManifest.PISTON_DATA_URL.length());
                                    v.withDependencies(deps -> {
                                        var extension = relativePath.substring(relativePath.lastIndexOf('.') + 1);
                                        deps.add(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":"+MINECRAFT_DATA_ARTIFACT+"@"+extension, dep -> {
                                            dep.version(depVersion -> {
                                                depVersion.strictly(relativePath);
                                            });
                                        });
                                    });
                                });
                            }
                            details.addVariant("versionJson", v -> {
                                v.attributes(attributes -> {
                                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category.class, "versionjson"));
                                });
                                v.withDependencies(deps -> {
                                    var extension = relativeVersionUrl.substring(relativeVersionUrl.lastIndexOf('.') + 1);
                                    deps.add(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":" + MINECRAFT_META_ARTIFACT+"@"+extension, dep -> {
                                        dep.version(depVersion -> {
                                            depVersion.strictly(relativeVersionUrl);
                                        });
                                    });
                                });
                            });
                        }
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

}
