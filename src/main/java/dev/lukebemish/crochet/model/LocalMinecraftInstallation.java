package dev.lukebemish.crochet.model;

import com.google.common.base.Suppliers;
import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.IdeaModelHandlerPlugin;
import dev.lukebemish.crochet.internal.Memoize;
import dev.lukebemish.crochet.internal.TaskUtils;
import dev.lukebemish.crochet.internal.tasks.TaskGraphExecution;
import org.gradle.api.Action;
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
import java.util.function.Supplier;

public abstract class LocalMinecraftInstallation extends MinecraftInstallation {
    @SuppressWarnings("UnstableApiUsage")
    final DependencyScopeConfiguration accessTransformers;
    @SuppressWarnings("UnstableApiUsage")
    final ResolvableConfiguration accessTransformersPath;
    @SuppressWarnings("UnstableApiUsage")
    final DependencyScopeConfiguration accessTransformersApi;

    @SuppressWarnings("UnstableApiUsage")
    final DependencyScopeConfiguration injectedInterfaces;
    @SuppressWarnings("UnstableApiUsage")
    final ResolvableConfiguration injectedInterfacesPath;
    @SuppressWarnings("UnstableApiUsage")
    final DependencyScopeConfiguration injectedInterfacesApi;

    final Supplier<Configuration> accessTransformersElements;
    final Memoize<Configuration> injectedInterfacesElements;

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

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public LocalMinecraftInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        var project = extension.project;

        this.assetsProperties = project.getLayout().getBuildDirectory().file("crochet/installations/"+name+"/assets.properties");
        this.downloadAssetsTask = TaskUtils.registerInternal(this, TaskGraphExecution.class, name, "DownloadAssets", task -> {
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
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

        this.accessTransformersApi = ConfigurationUtils.dependencyScope(this, name, null, "accessTransformersApi", config -> {
            config.fromDependencyCollector(getDependencies().getAccessTransformersApi());
        });
        this.accessTransformers = ConfigurationUtils.dependencyScope(this, name, null, "accessTransformers", config -> {
            config.fromDependencyCollector(getDependencies().getAccessTransformers());
        });
        this.accessTransformersPath = ConfigurationUtils.resolvableInternal(this, name, "accessTransformersPath", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_TRANSFORMER_CATEGORY))
            );
            config.extendsFrom(this.accessTransformersApi);
            config.extendsFrom(this.accessTransformers);
        });

        this.injectedInterfacesApi = ConfigurationUtils.dependencyScope(this, name, null, "injectedInterfacesApi", config -> {
            config.fromDependencyCollector(getDependencies().getInjectedInterfacesApi());
        });
        this.injectedInterfaces = ConfigurationUtils.dependencyScope(this, name, null, "injectedInterfaces", config -> {
            config.fromDependencyCollector(getDependencies().getInjectedInterfaces());
        });
        this.injectedInterfacesPath = ConfigurationUtils.resolvableInternal(this, name, "injectedInterfacesPath", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, INTERFACE_INJECTION_CATEGORY))
            );
            config.extendsFrom(this.injectedInterfacesApi);
            config.extendsFrom(this.injectedInterfaces);
        });

        this.accessTransformersElements = Suppliers.memoize(() -> ConfigurationUtils.consumable(this, name, null, "accessTransformersElements", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_TRANSFORMER_CATEGORY))
            );
            config.setVisible(false);
            config.extendsFrom(this.accessTransformersApi);
        }));

        this.injectedInterfacesElements = Memoize.of(() -> {
            var c = ConfigurationUtils.consumable(this, name, null, "injectedInterfacesElements", config -> {
                config.attributes(attributes ->
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, INTERFACE_INJECTION_CATEGORY))
                );
                config.setVisible(false);
                config.extendsFrom(this.injectedInterfacesApi);
            });
            return c;
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

        this.binaryArtifactsTask = TaskUtils.registerInternal(this, TaskGraphExecution.class, name, "crochetMinecraftBinaryArtifacts", task -> {
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("resources", resources, project.getObjects()));
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("binarySourceIndependent", binary, project.getObjects()));
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        this.sourcesArtifactsTask = TaskUtils.registerInternal(this, TaskGraphExecution.class, name, "crochetMinecraftSourcesArtifacts", task -> {
            task.copyConfigFrom(binaryArtifactsTask.get());
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("sources", sources, project.getObjects()));
        });

        this.lineMappedBinaryArtifactsTask = TaskUtils.registerInternal(this, TaskGraphExecution.class, name, "crochetMinecraftLineMappedBinaryArtifacts", task -> {
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
    }

    public void share(String externalTag) {
        for (var entry : getConfigurationsToLink().entrySet()) {
            var configuration = entry.getValue();
            var tag = entry.getKey();
            var group = CROSS_PROJECT_SHARING_CAPABILITY_GROUP + sharingInstallationTypeTag();
            var module = tag + "-" + externalTag;
            configuration.getOutgoing().capability(group + ":" + module + ":" + "1.0.0");
            configuration.getAttributes().attributeProvider(CrochetProjectPlugin.LOCAL_DISTRIBUTION_ATTRIBUTE, getDistribution().map(it -> it.name().toLowerCase(Locale.ROOT)));
        }
    }

    @Override
    public void forFeature(SourceSet sourceSet) {
        super.forFeature(sourceSet);
        forFeatureShared(sourceSet);
        FeatureUtils.forSourceSetFeature(crochetExtension.project, sourceSet.getName(), context -> {
            AtomicBoolean atsAdded = new AtomicBoolean(false);
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
        "InjectedInterfaces",
        "InjectedInterfacesApi"
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
        forFeatureShared(sourceSet);
    }

    private void forFeatureShared(SourceSet sourceSet) {
        FeatureUtils.forSourceSetFeature(crochetExtension.project, sourceSet.getName(), context -> {
            context.withCapabilities(accessTransformersElements.get());
            accessTransformersElements.get().attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, crochetExtension.project.getObjects().named(Category.class, ACCESS_TRANSFORMER_CATEGORY));
            });

            context.withCapabilities(injectedInterfacesElements.get());
            injectedInterfacesElements.get().attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, crochetExtension.project.getObjects().named(Category.class, INTERFACE_INJECTION_CATEGORY));
            });
        });
    }

    protected final AbstractLocalInstallationDependencies<?> dependencies;

    public AbstractLocalInstallationDependencies<?> getDependencies() {
        return this.dependencies;
    }

    protected abstract String sharingInstallationTypeTag();

    protected Map<String, Configuration> getConfigurationsToLink() {
        return configurationsToLink.get();
    }

    private final Supplier<Map<String, Configuration>> configurationsToLink = Suppliers.memoize(this::makeConfigurationsToLink);

    protected Map<String, Configuration> makeConfigurationsToLink() {
        var assetsPropertiesConfiguration = ConfigurationUtils.dependencyScopeInternal(this, getName(), "assetsProperties", config -> {});
        crochetExtension.project.getDependencies().add(assetsPropertiesConfiguration.getName(), assetsPropertiesFiles);

        return Map.of(
            "assets-properties", ConfigurationUtils.consumableInternal(this, getName(), "assetsPropertiesElements", config -> {
                config.extendsFrom(assetsPropertiesConfiguration);
            }),
            "minecraft", ConfigurationUtils.consumableInternal(this, getName(), "minecraftElements", config -> {
                config.extendsFrom(minecraft);
            }),
            "minecraft-dependencies", ConfigurationUtils.consumableInternal(this, getName(), "minecraftDependenciesElements", config -> {
                config.extendsFrom(minecraftDependencies);
            }),
            "minecraft-resources", ConfigurationUtils.consumableInternal(this, getName(), "minecraftResourcesElements", config -> {
                config.extendsFrom(minecraftResources);
            }),
            "minecraft-line-mapped", ConfigurationUtils.consumableInternal(this, getName(), "minecraftLineMappedElements", config -> {
                config.extendsFrom(minecraftLineMapped);
            }),
            "non-upgradable", ConfigurationUtils.consumableInternal(this, getName(), "nonUpgradableElements", config -> {
                config.extendsFrom(nonUpgradableDependencies);
            }),
            "binary", ConfigurationUtils.consumableInternal(this, getName(), "binaryElements", config -> {
                crochetExtension.project.getArtifacts().add(config.getName(), binary, a -> a.builtBy(binaryArtifactsTask));
            }),
            "binary-line-mapped", ConfigurationUtils.consumableInternal(this, getName(), "binaryLineMappedElements", config -> {
                crochetExtension.project.getArtifacts().add(config.getName(), binaryLineMapped, a -> a.builtBy(lineMappedBinaryArtifactsTask));
            }),
            "resources", ConfigurationUtils.consumableInternal(this, getName(), "resourcesElements", config -> {
                crochetExtension.project.getArtifacts().add(config.getName(), resources, a -> a.builtBy(binaryArtifactsTask));
            })
        );
    }
}
