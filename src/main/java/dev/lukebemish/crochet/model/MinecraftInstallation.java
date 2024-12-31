package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.IdeaModelHandlerPlugin;
import dev.lukebemish.crochet.internal.InheritanceMarker;
import dev.lukebemish.crochet.internal.tasks.TaskGraphExecution;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.attributes.Category;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MinecraftInstallation implements Named {
    static final String ACCESS_TRANSFORMER_CATEGORY = "accesstransformer";
    static final String INTERFACE_INJECTION_CATEGORY = "interfaceinjection";

    private final String name;
    private final Set<SourceSet> sourceSets = new LinkedHashSet<>();
    private final CrochetExtension crochetExtension;
    private final Property<InstallationDistribution> distribution;

    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> accessTransformers;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ResolvableConfiguration> accessTransformersPath;
    final Provider<Configuration> accessTransformersElements;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> accessTransformersApi;

    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> injectedInterfaces;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<ResolvableConfiguration> injectedInterfacesPath;
    final Provider<Configuration> injectedInterfacesElements;
    @SuppressWarnings("UnstableApiUsage")
    final Provider<DependencyScopeConfiguration> injectedInterfacesApi;

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

    final Configuration minecraft;
    final Configuration minecraftResources;
    final Configuration minecraftLineMapped;
    final Configuration minecraftDependencies;

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
        this.accessTransformersElements = project.getConfigurations().register(name+"AccessTransformersElements", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_TRANSFORMER_CATEGORY))
            );
            config.setCanBeResolved(false);
            config.setCanBeDeclared(false);
            config.setCanBeConsumed(false);
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
        this.injectedInterfacesElements = project.getConfigurations().register(name+"InterfaceInjectionsElements", config -> {
            config.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, INTERFACE_INJECTION_CATEGORY))
            );
            config.setCanBeResolved(false);
            config.setCanBeDeclared(false);
            config.setCanBeConsumed(false);
            config.extendsFrom(this.injectedInterfacesApi.get());
        });

        this.distribution = project.getObjects().property(InstallationDistribution.class);
        this.distribution.finalizeValueOnRead();
        this.distribution.convention(InstallationDistribution.JOINED);

        var workingDirectory = project.getLayout().getBuildDirectory().dir("crochet/installations/" + name);
        this.workingDirectory = workingDirectory;

        this.resources = workingDirectory.map(it -> it.file(name+"-extra-resources.jar"));
        this.binary = workingDirectory.map(it -> it.file(name+"-compiled.jar"));
        this.sources = workingDirectory.map(it -> it.file(name+"-sources.jar"));
        this.binaryLineMapped = workingDirectory.map(it -> it.file(name+"-compiled-line-mapped.jar"));

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

        this.minecraftDependencies = project.getConfigurations().create("crochet"+ StringUtils.capitalize(name)+"MinecraftDependencies");

        this.minecraftResources = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"MinecraftResources");
        this.minecraft = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"Minecraft", config -> {
            config.setCanBeConsumed(false);
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
            config.attributes(attributes -> attributes.attributeProvider(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::neoAttributeValue)));
        });

        this.minecraftLineMapped = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"MinecraftLineMapped", config -> {
            config.setCanBeConsumed(false);
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
            config.attributes(attributes -> attributes.attributeProvider(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::neoAttributeValue)));
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

    public Property<InstallationDistribution> getDistribution() {
        return this.distribution;
    }

    public void client() {
        this.distribution.set(InstallationDistribution.CLIENT);
    }

    public void common() {
        this.distribution.set(InstallationDistribution.COMMON);
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

    protected boolean canPublishInjectedInterfaces() {
        return true;
    }

    public void forLocalFeature(SourceSet sourceSet) {
        if (sourceSets.add(sourceSet)) {
            this.crochetExtension.forSourceSet(this.getName(), sourceSet);
        }
        FeatureUtils.forSourceSetFeature(crochetExtension.project, sourceSet.getName(), context -> {
            forFeatureShared(context);
        });
    }

    private static final List<String> INSTALLATION_CONFIGURATION_NAMES = List.of(
        "AccessTransformers",
        "AccessTransformersApi",
        "InterfaceInjections",
        "InterfaceInjectionsApi"
    );

    protected List<String> getInstallationConfigurationNames() {
        return INSTALLATION_CONFIGURATION_NAMES;
    }

    private void forFeatureShared(FeatureUtils.Context context) {
        context.withCapabilities(accessTransformersElements.get());
        context.withCapabilities(injectedInterfacesElements.get());

        var project = crochetExtension.project;
        var sourceSet = context.getSourceSet();
        // Link up inheritance via CrochetFeatureContexts for the injected configurations
        var marker = InheritanceMarker.getOrCreate(project.getObjects(), sourceSet);
        marker.getShouldTakeConfigurationsFrom().configureEach(name -> {
            var otherSourceSet = project.getExtensions().getByType(SourceSetContainer.class).findByName(name);
            var otherInstallation = crochetExtension.findInstallation(otherSourceSet);
            if (otherInstallation != this && otherInstallation != null) {
                for (var confName : getInstallationConfigurationNames()) {
                    var thisConf = project.getConfigurations().getByName(this.name + confName);
                    var otherConf = project.getConfigurations().getByName(otherInstallation.name + confName);
                    thisConf.extendsFrom(otherConf);
                }
            }
        });
        marker.getShouldGiveConfigurationsTo().configureEach(name -> {
            var otherSourceSet = project.getExtensions().getByType(SourceSetContainer.class).findByName(name);
            var otherInstallation = crochetExtension.findInstallation(otherSourceSet);
            if (otherInstallation != this && otherInstallation != null) {
                for (var confName : getInstallationConfigurationNames()) {
                    var thisConf = project.getConfigurations().getByName(this.name + confName);
                    var otherConf = project.getConfigurations().getByName(otherInstallation.name + confName);
                    otherConf.extendsFrom(thisConf);
                }
            }
        });
    }

    protected final AbstractInstallationDependencies dependencies;

    protected AbstractInstallationDependencies makeDependencies(Project project) {
        return project.getObjects().newInstance(AbstractInstallationDependencies.class, this);
    }

    @Nested
    public AbstractInstallationDependencies getDependencies() {
        return this.dependencies;
    }

    abstract void forRun(Run run, RunType runType);
}
