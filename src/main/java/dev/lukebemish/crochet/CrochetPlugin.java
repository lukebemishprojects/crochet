package dev.lukebemish.crochet;

import com.google.common.collect.ImmutableMap;
import dev.lukebemish.crochet.mapping.Mappings;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.Set;

public class CrochetPlugin implements Plugin<Project> {
    public static final String LOCAL_RUNTIME_CONFIGURATION_NAME = "localRuntime";

    @Override
    public void apply(@NotNull Project project) {
        var extension = project.getExtensions().create("crochet", CrochetExtension.class, project);
        project.apply(ImmutableMap.of("plugin", "java-library"));

        JavaPluginExtension javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);

        javaPlugin.getSourceSets().configureEach(sourceSet -> {
            var prefix = SourceSet.isMain(sourceSet) ? "" : sourceSet.getName();
            extension.extraConfigurations(prefix);
        });

        var dependencies = project.getDependencies();
        dependencies.attributesSchema(schema -> {
            schema.attribute(Mappings.MAPPINGS_ATTRIBUTE);
            schema.getMatchingStrategy(Mappings.MAPPINGS_ATTRIBUTE).getDisambiguationRules().add(MappingsDisambiguation.class, config ->
                config.params(project.getObjects().named(Mappings.class, Mappings.UNMAPPED))
            );
        });
        dependencies.getArtifactTypes()
            .maybeCreate(ArtifactTypeDefinition.JAR_TYPE)
            .getAttributes()
            .attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, Mappings.UNMAPPED));
    }

    private static class MappingsDisambiguation implements AttributeDisambiguationRule<Mappings> {
        private final Mappings unmapped;

        @Inject
        private MappingsDisambiguation(Mappings unmapped) {
            this.unmapped = unmapped;
        }

        @Override
        public void execute(MultipleCandidatesDetails<Mappings> details) {
            Set<Mappings> candidateValues = details.getCandidateValues();
            Mappings consumerValue = details.getConsumerValue();
            if (consumerValue == null) {
                if (candidateValues.contains(unmapped)) {
                    details.closestMatch(unmapped);
                }
            } else if (candidateValues.contains(consumerValue)) {
                details.closestMatch(consumerValue);
            }
        }
    }
}
