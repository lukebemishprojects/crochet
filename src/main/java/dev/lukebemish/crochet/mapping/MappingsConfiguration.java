package dev.lukebemish.crochet.mapping;

import dev.lukebemish.crochet.CrochetExtension;
import dev.lukebemish.crochet.mapping.config.RemapParameters;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyCollector;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class MappingsConfiguration {
    private final Project project;

    public abstract Property<Action<RemapParameters>> getRemapping();

    private final Mappings mappings;

    @Inject
    public MappingsConfiguration(Mappings mappings, CrochetExtension parent, Project project) {
        this.project = project;
        this.mappings = mappings;
    }

    public abstract DependencyCollector getMappings();

    public void mappings(Object dependencyNotation) {
        getMappings().add(project.getDependencies().create(dependencyNotation));
    }


    // TODO: how feasible is this? Won't work with tasks I don't think, but... hmm
    // The trick would be getting this to know the names of the files needed ahead of time... hmm
    private String cleanName(Mappings mappings) {
        if (Mappings.isCompound(mappings)) {
            return Mappings.source(mappings) + "To" + StringUtils.capitalize(Mappings.target(mappings));
        } else {
            return mappings.getName();
        }
    }

    public Configuration remapClasspath(Configuration configuration) {
        String name = cleanName(mappings)+"Remap"+StringUtils.capitalize(configuration.getName());
        Configuration toRemap = project.getConfigurations().maybeCreate(name);
        TaskProvider<RemapManyTask> remapTask = project.getTasks().register(name, RemapManyTask.class, task -> {
            getRemapping().get().execute(task.getRemapParameters());
            task.getInputArtifacts().from(toRemap);
            task.dependsOn(toRemap);
            task.getOutputArtifact().set(project.getLayout().getBuildDirectory().dir("remapped/"+name));
        });
        ConfigurableFileCollection remapped = project.getObjects().fileCollection();
        remapped.from(project.provider(() -> {
            List<File> out = new ArrayList<>();
            int index = 0;
            for (File in : remapTask.get().getInputArtifacts()) {
                File output = remapTask.get().outputFor(in, index);
                out.add(output);
                index++;
            }
            return out;
        }));
        remapped.builtBy(remapTask);
        configuration.getDependencies().add(project.getDependencies().create(remapped));
        return toRemap;
    }

    public void configurations(SourceSet sourceSet) {
        var toRemapCompile = remapClasspath(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()));
        var toRemapRuntime = remapClasspath(project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName()));
    }

    private void copyExternalAttributes(Configuration to, Configuration from) {
        for (var attr : from.getAttributes().keySet()) {
            if (attr.getName().equals(Mappings.MAPPINGS_ATTRIBUTE.getName())) {
                continue;
            }
            copyAttribute(to, from, attr);
        }
    }

    private <T> void copyAttribute(Configuration to, Configuration from, Attribute<T> attr) {
        var value = from.getAttributes().getAttribute(attr);
        if (value != null) {
            to.getAttributes().attribute(attr, value);
        }
    }
}
