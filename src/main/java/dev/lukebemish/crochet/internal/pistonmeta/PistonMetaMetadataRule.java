package dev.lukebemish.crochet.internal.pistonmeta;

import com.google.gson.stream.JsonReader;
import dev.lukebemish.crochet.internal.CrochetPlugin;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.MutableVariantFilesMetadata;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@CacheableRule
public abstract class PistonMetaMetadataRule implements ComponentMetadataRule {
    public static final String MINECRAFT_DEPENDENCIES = "minecraft-dependencies";
    public static final String MINECRAFT_DEPENDENCIES_NATIVES = "minecraft-dependencies-natives";

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
        if ("dev.lukebemish.crochet".equals(details.getId().getGroup()) && (MINECRAFT_DEPENDENCIES.equals(details.getId().getName()) || MINECRAFT_DEPENDENCIES_NATIVES.equals(details.getId().getName()))) {
            var versionString = details.getId().getVersion();
            boolean isNatives = MINECRAFT_DEPENDENCIES_NATIVES.equals(details.getId().getName());
            details.setStatusScheme(List.of("old_alpha", "old_beta", "snapshot", "release"));
            getRepositoryResourceAccessor().withResource(VersionManifest.VERSION_MANIFEST, versionManifestStream -> {
                try (var reader = new JsonReader(new InputStreamReader(versionManifestStream))) {
                    VersionManifest manifest = VersionManifest.GSON.fromJson(reader, VersionManifest.class);
                    var versionEntry = manifest.versions().stream()
                        .filter(v -> v.id().equals(versionString))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Version not found in piston-meta version manifest; may not exist: " + versionString));
                    details.setStatus(versionEntry.type());
                    details.allVariants(v -> {
                        v.withFiles(MutableVariantFilesMetadata::removeAllFiles);
                    });
                    if (!versionEntry.url().startsWith(VersionManifest.PISTON_META_URL)) {
                        throw new IllegalStateException("Version URL not from piston-meta " + versionEntry.url() + " for " + versionString);
                    }
                    var relativeVersionUrl = versionEntry.url().substring(VersionManifest.PISTON_META_URL.length());
                    List<String> clientDeps = new ArrayList<>();
                    List<String> serverDeps = new ArrayList<>();
                    Map<String, List<String>> clientNativeDeps = new HashMap<>();
                    AtomicInteger javaVersion = new AtomicInteger();
                    getRepositoryResourceAccessor().withResource(relativeVersionUrl, versionStream -> {
                        try (var versionReader = new JsonReader(new InputStreamReader(versionStream))) {
                            Version version = VersionManifest.GSON.fromJson(versionReader, Version.class);
                            javaVersion.set(version.javaVersion().majorVersion());
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
                                if (serverDownload != null) {
                                    var serverUrl = serverDownload.url();
                                    if (serverUrl != null) {
                                        URL url = URI.create(serverUrl).toURL();
                                        try (var serverStream = url.openStream()) {
                                            try (var zip = new ZipInputStream(serverStream)) {
                                                ZipEntry entry;
                                                while ((entry = zip.getNextEntry()) != null) {
                                                    if (entry.getName().equals("META-INF/libraries.list")) {
                                                        new String(zip.readAllBytes(), StandardCharsets.UTF_8).lines().forEach(line -> {
                                                            serverDeps.add(line.split("\t")[1]);
                                                        });
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    if (!isNatives) {
                        details.addVariant("clientCompileDependencies", v -> {
                            v.withDependencies(deps -> {
                                List<String> fullDeps = new ArrayList<>(clientDeps);
                                clientNativeDeps.getOrDefault("osx", List.of()).forEach(dep -> {
                                    if (dep.startsWith("ca.weblite:java-objc-bridge")) {
                                        fullDeps.add(dep);
                                    }
                                });
                                depsOf(fullDeps, deps);
                            });
                            v.attributes(attributes -> {
                                attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_API));
                                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion.intValue());
                                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                            });
                        });
                        details.addVariant("clientRuntimeDependencies", v -> {
                            v.withDependencies(deps -> {
                                depsOf(clientDeps, deps);
                                deps.add("dev.lukebemish.crochet:"+MINECRAFT_DEPENDENCIES_NATIVES, dep -> {
                                    dep.version(version -> {
                                        version.strictly(versionString);
                                    });
                                    dep.endorseStrictVersions();
                                });
                            });
                            v.attributes(attributes -> {
                                attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion.intValue());
                                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                            });
                        });
                        details.addVariant("serverCompileDependencies", v -> {
                            v.withDependencies(deps -> {
                                depsOf(serverDeps, deps);
                            });
                            v.attributes(attributes -> {
                                attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_API));
                                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion.intValue());
                                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server");
                            });
                        });
                        details.addVariant("serverRuntimeDependencies", v -> {
                            v.withDependencies(deps -> {
                                depsOf(serverDeps, deps);
                            });
                            v.attributes(attributes -> {
                                attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion.intValue());
                                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server");
                            });
                        });
                    } else {
                        clientNativeDeps.forEach((os, deps) -> {
                            details.addVariant(os + "NativeDependencies", v -> {
                                v.withDependencies(dep -> {
                                    depsOf(deps, dep);
                                });
                                v.attributes(attributes -> {
                                    attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.NATIVE_LINK));
                                    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion.intValue());
                                    attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                                    attributes.attribute(CrochetPlugin.OPERATING_SYSTEM_ATTRIBUTE, os);
                                });
                            });
                        });
                    }
                    details.setChanging(false);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private void depsOf(List<String> fullDeps, DirectDependenciesMetadata deps) {
        var unique = new LinkedHashSet<>(fullDeps);
        for (var notation : unique) {
            deps.add(notation, dep -> {
                // TODO: make this non-transitive with proper gradle API
                // (this requires actually contributing said API to gradle)
                var existingVersion = dep.getVersionConstraint().getRequiredVersion();
                dep.version(version -> {
                    if (!existingVersion.isEmpty()) {
                        version.strictly(existingVersion);
                    }
                });
            });
        }
    }
}
