package dev.lukebemish.crochet.internal.pistonmeta;

import com.google.gson.stream.JsonReader;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataListerDetails;
import org.gradle.api.artifacts.ComponentMetadataVersionLister;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

public abstract class PistonMetaVersionLister implements ComponentMetadataVersionLister {
    @Inject
    public PistonMetaVersionLister() {}

    @Inject
    protected abstract RepositoryResourceAccessor getRepositoryResourceAccessor();

    @Override
    public void execute(ComponentMetadataListerDetails details) {
        if ("dev.lukebemish.crochet".equals(details.getModuleIdentifier().getGroup()) && "minecraft-dependencies".equals(details.getModuleIdentifier().getName())) {
            getRepositoryResourceAccessor().withResource(VersionManifest.VERSION_MANIFEST, resource -> {
                try (var reader = new JsonReader(new InputStreamReader(resource))) {
                    VersionManifest manifest = VersionManifest.GSON.fromJson(reader, VersionManifest.class);
                    var versions = manifest.versions().stream()
                        .map(VersionManifest.Version::id)
                        .toList();
                    details.listed(versions);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
