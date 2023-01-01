package dev.lukebemish.crochet;

import dev.lukebemish.crochet.configuration.MappingConfigurations;
import dev.lukebemish.crochet.mapping.MapSpec;
import dev.lukebemish.crochet.mapping.RemapTransformSpec;
import dev.lukebemish.crochet.tasks.BoringTask;
import org.apache.commons.text.WordUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.util.HashSet;

public abstract class CrochetExtension {
    private final Project project;
    private final HashSet<MapSpec> transformations = new HashSet<>();

    @Inject
    public CrochetExtension(Project project) {
        this.project = project;
    }

    public void remap(Configuration source, Configuration destination, Provider<String> from, Provider<String> to) {
        var mapSpecProvider = project.provider(() -> new MapSpec.Named(from.get(), to.get()));

        source.setCanBeResolved(true);
        source.setCanBeConsumed(false);
        MappingConfigurations.requireMappings(project, source, mapSpecProvider);

        var taskName = "crochetRemapTo" + WordUtils.capitalize(destination.getName())+"From"+WordUtils.capitalize(source.getName());

        var remapTask = project.getTasks().create(taskName, BoringTask.class, task -> {
            task.setConfiguration(source);
            task.setGroup("Crochet Setup");
        });

        project.getDependencies().add(destination.getName(), project.files(remapTask.getOutputFiles()));

        transformMappings(mapSpecProvider);
    }

    public void remap(Configuration source, Configuration destination, String from, String to) {
        remap(source, destination, project.provider(() -> from), project.provider(() -> to));
    }

    public void transformMappings(Provider<MapSpec.Named> mapSpecProvider) {
        project.afterEvaluate(p -> {
            var mapSpec = mapSpecProvider.get();
            if (!transformations.contains(mapSpec)) {
                project.getDependencies().registerTransform(RemapTransformSpec.class, transformSpec -> {
                    transformSpec.getFrom().attribute(CrochetPlugin.MAPPINGS_ATTRIBUTE, MapSpec.Unmapped.INSTANCE).attribute(CrochetPlugin.ARTIFACT_TYPE_ATTRIBUTE, "jar");
                    transformSpec.getTo().attribute(CrochetPlugin.MAPPINGS_ATTRIBUTE, mapSpec).attribute(CrochetPlugin.ARTIFACT_TYPE_ATTRIBUTE, "jar");
                    transformSpec.getParameters().setMappings(mapSpec);
                });

                transformations.add(mapSpec);
            }
        });
    }
}
