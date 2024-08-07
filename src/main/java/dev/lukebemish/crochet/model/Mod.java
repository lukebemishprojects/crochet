package dev.lukebemish.crochet.model;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Named;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;

@SuppressWarnings("UnstableApiUsage")
public abstract class Mod implements Dependencies, Named {
    private final String name;
    protected final Configuration components;

    @Inject
    public Mod(String name) {
        this.name = name;
        this.components = getProject().getConfigurations().create("crochetMod"+ StringUtils.capitalize(name) + "Components");
        components.fromDependencyCollector(getInclude());
    }

    @Override
    public String getName() {
        return name;
    }

    public abstract DependencyCollector getInclude();

    public void include(SourceSet sourceSet) {
        getInclude().add(sourceSet.getOutput());
    }

    public void include(Mod mod) {
        components.extendsFrom(mod.components);
    }
}
