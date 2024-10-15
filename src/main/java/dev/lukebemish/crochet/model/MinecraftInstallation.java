package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.tasks.TaskGraphExecution;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.attributes.Category;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MinecraftInstallation implements Named {
    private static final String ACCESS_TRANSFORMER_CATEGORY = "accesstransformer";
    private static final String INTERFACE_INJECTION_CATEGORY = "interfaceinjection";

    private final String name;
    private final Set<SourceSet> sourceSets = new LinkedHashSet<>();
    private final CrochetExtension crochetExtension;
    private final Property<InstallationDistribution> distribution;

    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> accessTransformers;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ResolvableConfiguration> accessTransformersPath;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ConsumableConfiguration> accessTransformersElements;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> accessTransformersApi;

    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> injectedInterfaces;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ResolvableConfiguration> injectedInterfacesPath;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ConsumableConfiguration> injectedInterfacesElements;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> injectedInterfacesApi;

    final Provider<Configuration> parchmentConfiguration;

    final TaskProvider<TaskGraphExecution> downloadAssetsTask;

    final Provider<RegularFile> assetsProperties;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public MinecraftInstallation(String name, CrochetExtension extension) {
        this.name = name;
        this.crochetExtension = extension;

        var project = this.crochetExtension.project;

        this.dependencies = makeDependencies(project);

        this.assetsProperties = project.getLayout().getBuildDirectory().file("crochet/installations/"+name+"/assets.properties");
        this.downloadAssetsTask = project.getTasks().register(name+"CrochetDownloadAssets", TaskGraphExecution.class, task -> {
            task.setGroup("crochet setup");
            task.getClasspath().from(project.getConfigurations().named(CrochetPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("assets", assetsProperties, project.getObjects()));
            // Bounce, to avoid capturing the installation in the args
            var assetsProperties = this.assetsProperties;
            task.getOutputs().upToDateWhen(t -> {
                var file = assetsProperties.get().getAsFile();
                if (!file.exists()) {
                    return false;
                }
                var properties = new Properties();
                try (var reader = new FileReader(file)) {
                    properties.load(reader);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                var index = properties.getProperty("asset_index");
                var root = Paths.get(properties.getProperty("assets_root"));
                return Files.exists(root.resolve("indexes").resolve(index+".json"));
            });
        });

        this.accessTransformersApi = project.getConfigurations().dependencyScope(name+"AccessTransformersApi", config -> {
            config.fromDependencyCollector(getDependencies().getAccessTransformersApi());
        });
        this.accessTransformers = project.getConfigurations().dependencyScope(name+"AccessTransformers", config -> {
            config.fromDependencyCollector(getDependencies().getAccessTransformers());
        });
        this.accessTransformersPath = project.getConfigurations().resolvable(name+"AccessTransformersPath", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_TRANSFORMER_CATEGORY))
            );
            config.extendsFrom(this.accessTransformersApi.get());
            config.extendsFrom(this.accessTransformers.get());
        });
        this.accessTransformersElements = project.getConfigurations().consumable(name+"AccessTransformersElements", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_TRANSFORMER_CATEGORY))
            );
            config.extendsFrom(this.accessTransformersApi.get());
        });

        this.injectedInterfacesApi = project.getConfigurations().dependencyScope(name+"InterfaceInjectionsApi", config -> {
            config.fromDependencyCollector(getDependencies().getInjectedInterfacesApi());
        });
        this.injectedInterfaces = project.getConfigurations().dependencyScope(name+"InterfaceInjections", config -> {
            config.fromDependencyCollector(getDependencies().getInjectedInterfaces());
        });
        this.injectedInterfacesPath = project.getConfigurations().resolvable(name+"InterfaceInjectionsPath", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, INTERFACE_INJECTION_CATEGORY))
            );
            config.extendsFrom(this.injectedInterfacesApi.get());
            config.extendsFrom(this.injectedInterfaces.get());
        });
        this.injectedInterfacesElements = project.getConfigurations().consumable(name+"InterfaceInjectionsElements", config -> {
            config.extendsFrom(this.injectedInterfacesApi.get());
        });

        this.parchmentConfiguration = project.getConfigurations().register(name+"Parchment", config ->
            config.fromDependencyCollector(getDependencies().getParchment())
        );

        this.distribution = project.getObjects().property(InstallationDistribution.class);
        this.distribution.convention(InstallationDistribution.JOINED);

        // Execute any pending actions
        crochetExtension.executePendingActions(name, this);
    }

    public Property<InstallationDistribution> getDistribution() {
        return this.distribution;
    }

    public void client() {
        this.distribution.set(InstallationDistribution.CLIENT);
    }

    public void joined() {
        this.distribution.set(InstallationDistribution.JOINED);
    }

    public void server() {
        this.distribution.set(InstallationDistribution.SERVER);
    }

    @Override
    public String getName() {
        return name;
    }

    public void forFeature(SourceSet sourceSet) {
        if (sourceSets.add(sourceSet)) {
            this.crochetExtension.forSourceSet(this.getName(), sourceSet);
        }
        FeatureUtils.forSourceSetFeature(crochetExtension.project, sourceSet.getName(), context -> {
            AtomicBoolean atsAdded = new AtomicBoolean(false);
            context.withCapabilities(accessTransformersElements.get());
            accessTransformersElements.get().attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, crochetExtension.project.getObjects().named(Category.class, ACCESS_TRANSFORMER_CATEGORY));
            });
            accessTransformersElements.get().getDependencies().configureEach(dep -> {
                if (!atsAdded.compareAndSet(false, true)) {
                    context.publishWithVariants(accessTransformersElements.get());
                }
            });
            accessTransformersElements.get().getOutgoing().getArtifacts().configureEach(artifact -> {
                if (!atsAdded.compareAndSet(false, true)) {
                    context.publishWithVariants(accessTransformersElements.get());
                }
            });

            AtomicBoolean iisAdded = new AtomicBoolean(false);
            context.withCapabilities(injectedInterfacesElements.get());
            injectedInterfacesElements.get().attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, crochetExtension.project.getObjects().named(Category.class, INTERFACE_INJECTION_CATEGORY));
            });
            injectedInterfacesElements.get().getDependencies().configureEach(dep -> {
                if (!iisAdded.compareAndSet(false, true)) {
                    context.publishWithVariants(injectedInterfacesElements.get());
                }
            });
            injectedInterfacesElements.get().getOutgoing().getArtifacts().configureEach(artifact -> {
                if (!iisAdded.compareAndSet(false, true)) {
                    context.publishWithVariants(injectedInterfacesElements.get());
                }
            });
        });
    }

    public void forLocalFeature(SourceSet sourceSet) {
        if (sourceSets.add(sourceSet)) {
            this.crochetExtension.forSourceSet(this.getName(), sourceSet);
        }
        FeatureUtils.forSourceSetFeature(crochetExtension.project, sourceSet.getName(), context -> {
            context.withCapabilities(accessTransformersElements.get());
            context.withCapabilities(injectedInterfacesElements.get());
        });
    }

    protected final InstallationDependencies dependencies;

    protected InstallationDependencies makeDependencies(Project project) {
        return project.getObjects().newInstance(InstallationDependencies.class, this);
    }

    @Nested
    public InstallationDependencies getDependencies() {
        return this.dependencies;
    }

    abstract void forRun(Run run, RunType runType);

    void forMod(Mod mod, SourceSet sourceSet) {}
}
