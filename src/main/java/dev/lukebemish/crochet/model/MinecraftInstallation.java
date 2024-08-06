package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.tasks.DownloadAssetsTask;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Named;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class MinecraftInstallation implements Named {
    private final String name;
    private final Set<SourceSet> sourceSets = new LinkedHashSet<>();
    private final CrochetExtension crochetExtension;

    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> accessTransformers;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ResolvableConfiguration> accessTransformersPath;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ConsumableConfiguration> accessTransformersElements;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> accessTransformersApi;

    final TaskProvider<DownloadAssetsTask> downloadAssetsTask;

    final Configuration neoFormConfig;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public MinecraftInstallation(String name, CrochetExtension extension) {
        this.name = name;
        this.crochetExtension = extension;

        var project = this.crochetExtension.project;

        this.downloadAssetsTask = project.getTasks().register(name+"DownloadAssets", DownloadAssetsTask.class, task -> {
            task.setGroup("crochet setup");
            task.getAssetsProperties().convention(project.getLayout().getBuildDirectory().file("crochet/installations/"+name+"/assets.properties"));
            task.getRuntimeClasspath().from(project.getConfigurations().named(CrochetPlugin.NEOFORM_RUNTIME_CONFIGURATION_NAME));
        });

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

        this.neoFormConfig = project.getConfigurations().maybeCreate("crochet"+ StringUtils.capitalize(name)+"NeoFormConfig");
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

    protected abstract void forRun(Run run, RunType runType);
}
