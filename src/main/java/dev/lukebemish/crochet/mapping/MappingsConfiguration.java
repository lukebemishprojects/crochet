package dev.lukebemish.crochet.mapping;

import dev.lukebemish.crochet.CrochetExtension;
import dev.lukebemish.crochet.mapping.config.RemapParameters;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyCollector;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class MappingsConfiguration {
    private final Project project;

    public abstract Property<Action<RemapParameters>> getRemapping();

    @Inject
    public MappingsConfiguration(Mappings mappings, CrochetExtension parent, Project project) {
        this.project = project;
    }

    public abstract DependencyCollector getMappings();

    public void mappings(Object dependencyNotation) {
        getMappings().add(project.getDependencies().create(dependencyNotation));
    }
}
