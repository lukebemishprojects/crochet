package dev.lukebemish.crochet.internal.metadata;

import org.gradle.api.artifacts.DirectDependenciesMetadata;

import java.util.LinkedHashSet;
import java.util.List;

public final class MetadataUtils {
    private MetadataUtils() {}

    public static void depsOf(List<String> fullDeps, DirectDependenciesMetadata deps, boolean transitive) {
        var unique = new LinkedHashSet<>(fullDeps);
        for (var notation : unique) {
            deps.add(notation, dep -> {
                // TODO: make this non-transitive with proper gradle API
                // (this requires actually contributing said API to gradle)
                var existingVersion = dep.getVersionConstraint().getRequiredVersion();
                dep.version(version -> {
                    if (!existingVersion.isEmpty()) {
                        // Make this non-strict for now and handle deps slightly differently
                        //version.strictly(existingVersion);
                        version.require(existingVersion);
                    }
                });
            });
        }
    }
}
