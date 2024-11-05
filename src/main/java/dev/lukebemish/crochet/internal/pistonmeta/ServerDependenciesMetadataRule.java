package dev.lukebemish.crochet.internal.pistonmeta;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.CrochetRepositoriesPlugin;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.MutableVariantFilesMetadata;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@CacheableRule
public abstract class ServerDependenciesMetadataRule implements ComponentMetadataRule {
    public static final String MINECRAFT_SERVER_DEPENDENCIES = "minecraft-server-dependencies";

    @Inject
    public ServerDependenciesMetadataRule() {}

    @Inject
    protected abstract RepositoryResourceAccessor getRepositoryResourceAccessor();

    @Override
    public void execute(ComponentMetadataContext context) {
        var details = context.getDetails();
        // We have natives as a separate module because the metadata rule API does not currently support capabilities fully
        if (CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP.equals(details.getId().getGroup()) && MINECRAFT_SERVER_DEPENDENCIES.equals(details.getId().getName())) {
            var versionString = details.getId().getVersion();
            details.allVariants(v -> {
                v.withFiles(MutableVariantFilesMetadata::removeAllFiles);
            });
            details.setChanging(false);
            getRepositoryResourceAccessor().withResource(versionString, serverStream -> {
                List<String> serverDeps = new ArrayList<>();
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
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                details.addVariant("serverDependencies", v -> {
                    v.withDependencies(deps -> {
                        MetadataUtils.depsOf(serverDeps, deps, false);
                    });
                    v.attributes(attributes -> {
                        attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server");
                    });
                });
            });
        }
    }
}
