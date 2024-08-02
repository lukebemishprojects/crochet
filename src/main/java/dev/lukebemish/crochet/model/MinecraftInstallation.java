package dev.lukebemish.crochet.model;

import org.gradle.api.Named;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class MinecraftInstallation implements Named {
    private final String name;
    private final Set<SourceSet> sourceSets = new LinkedHashSet<>();
    private final CrochetExtension crochetExtension;

    @SuppressWarnings("UnstableApiUsage")
    protected final Provider<DependencyScopeConfiguration> accessTransformers;
    @SuppressWarnings("UnstableApiUsage")
    protected final Provider<ResolvableConfiguration> accessTransformersPath;
    @SuppressWarnings("UnstableApiUsage")
    protected final Provider<ConsumableConfiguration> accessTransformersElements;
    @SuppressWarnings("UnstableApiUsage")
    protected final Provider<DependencyScopeConfiguration> accessTransformersApi;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public MinecraftInstallation(String name, CrochetExtension extension) {
        this.name = name;
        this.crochetExtension = extension;

        var project = this.crochetExtension.project;
        this.accessTransformersApi = project.getConfigurations().dependencyScope(name+"AccessTransformersApi", config -> {
            config.fromDependencyCollector(getDependencies().getAccessTransformersApi());
        });
        this.accessTransformers = project.getConfigurations().dependencyScope(name+"AccessTransformers", config -> {
            config.fromDependencyCollector(getDependencies().getAccessTransformers());
        });
        this.accessTransformersPath = project.getConfigurations().resolvable(name+"AccessTransformersPath", config -> {
            // TODO: attributes
            config.extendsFrom(this.accessTransformersApi.get());
            config.extendsFrom(this.accessTransformers.get());
        });
        this.accessTransformersElements = project.getConfigurations().consumable(name+"AccessTransformersElements", config -> {
            // TODO: attributes
            config.extendsFrom(this.accessTransformersApi.get());
        });
    }

    @Override
    public String getName() {
        return name;
    }

    public void forSourceSet(SourceSet sourceSet) {
        if (sourceSets.add(sourceSet)) {
            this.crochetExtension.forSourceSet(this.getName(), sourceSet);
        }
    }

    @Nested
    public abstract InstallationDependencies getDependencies();
}
