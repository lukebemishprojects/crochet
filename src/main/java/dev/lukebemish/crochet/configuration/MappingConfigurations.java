package dev.lukebemish.crochet.configuration;

import dev.lukebemish.crochet.CrochetPlugin;
import dev.lukebemish.crochet.mapping.MapSpec;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;

public class MappingConfigurations {
    public static void requireMappings(Project project, Configuration configuration, Provider<MapSpec.Named> mappings) {
        project.afterEvaluate(p -> {
            if (!configuration.isCanBeResolved()) return;
            configuration.getAttributes().attribute(CrochetPlugin.MAPPINGS_ATTRIBUTE, mappings.get());
        });
    }
}
