package dev.lukebemish.crochet.internal.metadata;

import org.gradle.api.artifacts.DirectDependenciesMetadata;

import java.util.LinkedHashSet;
import java.util.List;

public final class MetadataUtils {
    private MetadataUtils() {}

    public static void depsOf(List<String> fullDeps, DirectDependenciesMetadata deps, boolean transitive) {
        var unique = new LinkedHashSet<>(fullDeps);
        for (var notation : unique) {
            // TODO: correctly handle artifact selectors in notation here. Requires artifact selector support in the gradle API
            deps.add(notation, dep -> {
                dep.excludes(excludes -> {
                    excludes.addExclude("*", "*");
                });
                var existingVersion = dep.getVersionConstraint().getRequiredVersion();
                dep.version(version -> {
                    if (!existingVersion.isEmpty()) {
                        // Make this non-strict and handle deps slightly differently
                        //version.strictly(existingVersion);
                        version.require(existingVersion);
                    }
                });
            });
        }
    }
}
