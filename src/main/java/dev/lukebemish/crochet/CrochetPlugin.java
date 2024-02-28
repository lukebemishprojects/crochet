package dev.lukebemish.crochet;

import com.google.common.collect.ImmutableMap;
import dev.lukebemish.crochet.mapping.Mappings;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CrochetPlugin implements Plugin<Project> {
    public static final Attribute<String> ARTIFACT_TYPE_ATTRIBUTE = Attribute.of("artifactType", String.class);

    public static final List<String> DEFAULT_REMAPPABLE_CONFIGURATIONS = List.of(
            JavaPlugin.API_CONFIGURATION_NAME,
            JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
            JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME,
            JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
            JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME
    );

    @Override
    public void apply(@NotNull Project project) {
        var extension = project.getExtensions().create("crochet", CrochetExtension.class, project);
        project.apply(ImmutableMap.of("plugin", "java-library"));

        var dependencies = project.getDependencies();
        dependencies.attributesSchema(schema -> {
            schema.attribute(Mappings.MAPPINGS_ATTRIBUTE);
        });
        dependencies.getArtifactTypes().configureEach(type -> {
            type.getAttributes().attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, Mappings.UNMAPPED));
        });
    }
}
