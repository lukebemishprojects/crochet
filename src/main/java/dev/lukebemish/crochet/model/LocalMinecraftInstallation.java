package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.IdeaModelHandlerPlugin;
import dev.lukebemish.crochet.internal.tasks.TaskGraphExecution;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.attributes.Category;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class LocalMinecraftInstallation extends MinecraftInstallation {
    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> accessTransformers;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ResolvableConfiguration> accessTransformersPath;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> accessTransformersApi;

    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> injectedInterfaces;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ResolvableConfiguration> injectedInterfacesPath;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> injectedInterfacesApi;

    final Provider<Configuration> accessTransformersElements;
    final Provider<Configuration> injectedInterfacesElements;

    final TaskProvider<TaskGraphExecution> downloadAssetsTask;

    final Provider<RegularFile> assetsProperties;

    final Provider<Directory> workingDirectory;

    final Provider<RegularFile> sources;
    final Provider<RegularFile> resources;
    final Provider<RegularFile> binary;
    final Provider<RegularFile> binaryLineMapped;

    final TaskProvider<TaskGraphExecution> binaryArtifactsTask;
    final TaskProvider<TaskGraphExecution> sourcesArtifactsTask;
    final TaskProvider<TaskGraphExecution> lineMappedBinaryArtifactsTask;

    final Configuration minecraftElements;
    final Configuration minecraftDependenciesElements;
    final Configuration minecraftResourcesElements;
    final Configuration minecraftLineMappedElements;

    final Configuration nonUpgradableElements;

    final Configuration assetsPropertiesElements;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public LocalMinecraftInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        var project = extension.project;

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

        this.assetsPropertiesFiles.from(assetsProperties).builtBy(downloadAssetsTask);

        this.dependencies = makeDependencies(project);

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

        this.accessTransformersElements = project.getConfigurations().register(name+"AccessTransformersElements", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_TRANSFORMER_CATEGORY))
            );
            config.setCanBeResolved(false);
            config.setCanBeDeclared(false);
            config.setCanBeConsumed(false);
            config.extendsFrom(this.accessTransformersApi.get());
        });

        this.injectedInterfacesElements = project.getConfigurations().register(name+"InterfaceInjectionsElements", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, INTERFACE_INJECTION_CATEGORY))
            );
            config.setCanBeResolved(false);
            config.setCanBeDeclared(false);
            config.setCanBeConsumed(false);
            config.extendsFrom(this.injectedInterfacesApi.get());
        });

        var workingDirectory = project.getLayout().getBuildDirectory().dir("crochet/installations/" + name);
        this.workingDirectory = workingDirectory;

        String prefix;
        {
            var prefixTemp = project.getPath().replace(":", "-");
            if (prefixTemp.startsWith("-")) {
                prefixTemp = prefixTemp.substring(1);
            }
            if (!prefixTemp.isEmpty()) {
                prefixTemp = prefixTemp + "-";
            }
            prefix = prefixTemp;
        }

        this.resources = workingDirectory.map(it -> it.file("crochet-"+prefix+name+"-extra-resources.jar"));
        this.binary = workingDirectory.map(it -> it.file("crochet-"+prefix+name+"-compiled.jar"));
        this.sources = workingDirectory.map(it -> it.file("crochet-"+prefix+name+"-sources.jar"));
        this.binaryLineMapped = workingDirectory.map(it -> it.file("crochet-"+prefix+name+"-compiled-line-mapped.jar"));

        if (IdeaModelHandlerPlugin.isIdeaSyncRelated(project)) {
            var model = IdeaModelHandlerPlugin.retrieve(project);
            model.mapBinaryToSourceWithLineMaps(binary, sources, binaryLineMapped);
        }

        this.binaryArtifactsTask = project.getTasks().register(name + "CrochetMinecraftBinaryArtifacts", TaskGraphExecution.class, task -> {
            task.setGroup("crochet setup");
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("resources", resources, project.getObjects()));
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("binarySourceIndependent", binary, project.getObjects()));
            task.getClasspath().from(project.getConfigurations().named(CrochetPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        this.sourcesArtifactsTask = project.getTasks().register(name + "CrochetMinecraftSourcesArtifacts", TaskGraphExecution.class, task -> {
            task.copyConfigFrom(binaryArtifactsTask.get());
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("sources", sources, project.getObjects()));
        });

        this.lineMappedBinaryArtifactsTask = project.getTasks().register(name + "CrochetMinecraftLineMappedBinaryArtifacts", TaskGraphExecution.class, task -> {
            task.copyConfigFrom(binaryArtifactsTask.get());
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("binary", binaryLineMapped, project.getObjects()));
        });

        extension.generateSources.configure(t -> {
            t.dependsOn(this.sourcesArtifactsTask);
            t.dependsOn(this.lineMappedBinaryArtifactsTask);
        });

        this.downloadAssetsTask.configure(task -> {
            task.copyConfigFrom(binaryArtifactsTask.get());
        });

        var binaryFiles = project.files(binary);
        binaryFiles.builtBy(binaryArtifactsTask);
        project.getDependencies().add(
            minecraft.getName(),
            binaryFiles
        );

        var lineMappedBinaryFiles = project.files(binaryLineMapped);
        lineMappedBinaryFiles.builtBy(lineMappedBinaryArtifactsTask);
        project.getDependencies().add(
            minecraftLineMapped.getName(),
            lineMappedBinaryFiles
        );

        var resourcesFiles = project.files(resources);
        resourcesFiles.builtBy(binaryArtifactsTask);

        project.getDependencies().add(
            minecraftResources.getName(),
            resourcesFiles
        );

        extension.idePostSync.configure(t -> t.dependsOn(binaryArtifactsTask));

        this.minecraftElements = project.getConfigurations().consumable(name+"MinecraftElements", config -> {
            config.extendsFrom(minecraft);
        }).get();
        this.minecraftDependenciesElements = project.getConfigurations().consumable(name+"MinecraftDependenciesElements", config -> {
            config.extendsFrom(minecraftDependencies);
        }).get();
        this.minecraftResourcesElements = project.getConfigurations().consumable(name+"MinecraftResourcesElements", config -> {
            config.extendsFrom(minecraftResources);
        }).get();
        this.minecraftLineMappedElements = project.getConfigurations().consumable(name+"MinecraftLineMappedElements", config -> {
            config.extendsFrom(minecraftLineMapped);
        }).get();
        this.nonUpgradableElements = project.getConfigurations().consumable(name+"NonUpgradableElements", config -> {
            config.extendsFrom(nonUpgradableDependencies);
        }).get();

        var assetsPropertiesConfiguration = project.getConfigurations().dependencyScope(name+"AssetsProperties");
        project.getDependencies().add(assetsPropertiesConfiguration.getName(), assetsPropertiesFiles);

        this.assetsPropertiesElements = project.getConfigurations().consumable(name+"AssetsPropertiesElements", config -> {
            config.extendsFrom(assetsPropertiesConfiguration.get());
        }).get();
    }

    public void share(String externalTag) {
        for (var entry : getConfigurationsToLink().entrySet()) {
            var configuration = entry.getValue();
            var tag = entry.getKey();
            var group = CROSS_PROJECT_SHARING_CAPABILITY_GROUP + sharingInstallationTypeTag();
            var module = tag + "-" + externalTag;
            configuration.getOutgoing().capability(group + ":" + module + ":" + "1.0.0");
            configuration.getAttributes().attributeProvider(CrochetPlugin.LOCAL_DISTRIBUTION_ATTRIBUTE, getDistribution().map(it -> it.name().toLowerCase(Locale.ROOT)));
        }
    }

    @Override
    public void forFeature(SourceSet sourceSet) {
        super.forFeature(sourceSet);
        FeatureUtils.forSourceSetFeature(crochetExtension.project, sourceSet.getName(), context -> {
            forFeatureShared(context);

            AtomicBoolean atsAdded = new AtomicBoolean(false);
            context.withCapabilities(accessTransformersElements.get());
            accessTransformersElements.get().setCanBeConsumed(true);
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
            injectedInterfacesElements.get().setCanBeConsumed(true);
            injectedInterfacesElements.get().attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, crochetExtension.project.getObjects().named(Category.class, INTERFACE_INJECTION_CATEGORY));
            });
            injectedInterfacesElements.get().getDependencies().configureEach(dep -> {
                if (canPublishInjectedInterfaces() && !iisAdded.compareAndSet(false, true)) {
                    context.publishWithVariants(injectedInterfacesElements.get());
                }
            });
            injectedInterfacesElements.get().getOutgoing().getArtifacts().configureEach(artifact -> {
                if (canPublishInjectedInterfaces() && !iisAdded.compareAndSet(false, true)) {
                    context.publishWithVariants(injectedInterfacesElements.get());
                }
            });
        });
    }

    protected AbstractLocalInstallationDependencies<?> makeDependencies(Project project) {
        return project.getObjects().newInstance(AbstractLocalInstallationDependencies.class, this);
    }

    private static final List<String> INSTALLATION_CONFIGURATION_NAMES = List.of(
        "AccessTransformers",
        "AccessTransformersApi",
        "InterfaceInjections",
        "InterfaceInjectionsApi"
    );

    @Override
    protected List<String> getInstallationConfigurationNames() {
        var out = new ArrayList<>(super.getInstallationConfigurationNames());
        out.addAll(INSTALLATION_CONFIGURATION_NAMES);
        return out;
    }

    @Override
    public void forLocalFeature(SourceSet sourceSet) {
        super.forLocalFeature(sourceSet);
        FeatureUtils.forSourceSetFeature(crochetExtension.project, sourceSet.getName(), context -> {
            forFeatureShared(context);
        });
    }

    protected final AbstractLocalInstallationDependencies dependencies;

    public AbstractLocalInstallationDependencies getDependencies() {
        return this.dependencies;
    }

    private void forFeatureShared(FeatureUtils.Context context) {
        context.withCapabilities(accessTransformersElements.get());
        context.withCapabilities(injectedInterfacesElements.get());
    }

    protected abstract String sharingInstallationTypeTag();

    protected Map<String, Configuration> getConfigurationsToLink() {
        return Map.of(
            "assets-properties", assetsPropertiesElements,
            "minecraft", minecraftElements,
            "minecraft-dependencies", minecraftDependenciesElements,
            "minecraft-resources", minecraftResourcesElements,
            "minecraft-line-mapped", minecraftLineMappedElements,
            "non-upgradable", nonUpgradableElements
        );
    }
}
