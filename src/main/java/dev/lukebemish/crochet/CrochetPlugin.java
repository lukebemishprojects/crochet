package dev.lukebemish.crochet;

import com.google.common.collect.ImmutableMap;
import dev.lukebemish.crochet.mapping.Mappings;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
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
        project.apply(ImmutableMap.of("plugin", "java-library"));
        var extension = project.getExtensions().create("crochet", CrochetExtension.class, project);

        setupRemappingConfigurations(project, extension);
    }

    private static void setupRemappingConfigurations(@NotNull Project project, CrochetExtension extension) {
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
            schema.getMatchingStrategy(Mappings.MAPPINGS_ATTRIBUTE).getCompatibilityRules().add(MappingsCompatibility.class);
        });
        dependencies.getArtifactTypes()
            .maybeCreate(ArtifactTypeDefinition.JAR_TYPE)
            .getAttributes()
            .attribute(Mappings.MAPPINGS_ATTRIBUTE, project.getObjects().named(Mappings.class, Mappings.UNMAPPED));
    }

    static class MappingsDisambiguation implements AttributeDisambiguationRule<Mappings> {
        private final Mappings unmapped;

        @Inject
        MappingsDisambiguation(Mappings unmapped) {
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

    static class MappingsCompatibility implements AttributeCompatibilityRule<Mappings> {
        @Override
        public void execute(CompatibilityCheckDetails<Mappings> details) {
            var consumer = details.getConsumerValue();
            var producer = details.getProducerValue();
            if (consumer == null || producer == null) {
                return;
            }
            if (consumer.getName().equals(producer.getName())) {
                details.compatible();
            }
            if (consumer.getName().equals(Mappings.UNMAPPED)) {
                details.compatible();
            }
            if (Mappings.isCompound(consumer)) {
                if (!Mappings.isCompound(producer) && Mappings.target(consumer).equals(producer.getName())) {
                    details.compatible();
                } else if (Mappings.isCompound(producer) && Mappings.target(producer).equals(Mappings.target(consumer))) {
                    details.compatible();
                }
            } else {
                if (Mappings.isCompound(producer) && Mappings.target(producer).equals(consumer.getName())) {
                    details.compatible();
                }
            }
        }
    }
}
