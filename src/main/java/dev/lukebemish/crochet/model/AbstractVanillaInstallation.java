package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.CrochetProperties;
import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.CrochetRepositoriesPlugin;
import dev.lukebemish.crochet.internal.IdeaModelHandlerPlugin;
import dev.lukebemish.crochet.internal.pistonmeta.PistonMetaMetadataRule;
import dev.lukebemish.crochet.mappings.ChainedMappingsStructure;
import dev.lukebemish.crochet.mappings.FileMappingsStructure;
import dev.lukebemish.crochet.mappings.MappingsStructure;
import dev.lukebemish.crochet.mappings.MergedMappingsStructure;
import dev.lukebemish.crochet.mappings.MojangOfficialMappingsStructure;
import dev.lukebemish.crochet.mappings.ReversedMappingsStructure;
import dev.lukebemish.crochet.tasks.TaskGraphExecution;
import dev.lukebemish.crochet.tasks.VanillaInstallationArtifacts;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.util.Map;

public abstract class AbstractVanillaInstallation extends MinecraftInstallation {
    final Project project;
    final Configuration minecraft;
    final Configuration minecraftLineMapped;
    final Configuration minecraftDependencies;
    final Configuration minecraftResources;
    final TaskProvider<TaskGraphExecution> binaryArtifactsTask;
    final TaskProvider<TaskGraphExecution> sourcesArtifactsTask;
    final TaskProvider<TaskGraphExecution> lineMappedBinaryArtifactsTask;
    final VanillaInstallationArtifacts vanillaConfigMaker;
    final Provider<Directory> workingDirectory;

    final Provider<RegularFile> sources;
    final Provider<RegularFile> resources;
    final Provider<RegularFile> binary;
    final Provider<RegularFile> binaryLineMapped;

    final Property<String> minecraftVersion;

    @Inject
    public AbstractVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.project = extension.project;

        // Create early so getMinecraft provider works right
        this.minecraftDependencies = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"MinecraftDependencies");
        this.minecraftVersion = project.getObjects().property(String.class);
        this.minecraftVersion.set(minecraftDependencies.getIncoming().getResolutionResult().getRootComponent().map(component -> {
            var dependencies = component.getDependencies();
            if (dependencies.size() != 1) {
                throw new IllegalStateException("Expected exactly one dependency, got "+dependencies.size());
            }
            var dependencyResult = dependencies.iterator().next();
            if (!(dependencyResult instanceof ResolvedDependencyResult resolvedDependencyResult)) {
                throw new IllegalStateException("Could not resolve minecraft-dependencies, got "+dependencyResult);
            }
            var identifier = resolvedDependencyResult.getResolvedVariant().getOwner();
            if (!(identifier instanceof ModuleComponentIdentifier moduleIdentifier)) {
                throw new IllegalStateException("Could not recover version from non-module component "+identifier);
            }
            return moduleIdentifier.getVersion();
        }));

        var minecraftPistonMeta = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"PistonMetaDownloads");
        var clientJarPistonMeta = project.getConfigurations().resolvable("crochet"+StringUtils.capitalize(name)+"ClientJarPistonMetaDownloads", c -> {
            c.extendsFrom(minecraftPistonMeta.get());
            c.exclude(Map.of(
                "group", CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP,
                "module", PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES
            ));
            c.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            });
        });
        var serverJarPistonMeta = project.getConfigurations().resolvable("crochet"+StringUtils.capitalize(name)+"ServerJarPistonMetaDownloads", c -> {
            c.extendsFrom(minecraftPistonMeta.get());
            c.exclude(Map.of(
                "group", CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP,
                "module", PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES
            ));
            c.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server");
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            });
        });
        var mappingsPistonMeta = project.getConfigurations().resolvable("crochet"+StringUtils.capitalize(name)+"MappingsPistonMetaDownloads", c -> {
            c.extendsFrom(minecraftPistonMeta.get());
            c.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, "mappings"));
            });
        });
        var versionJsonPistonMeta = project.getConfigurations().resolvable("crochet"+StringUtils.capitalize(name)+"VersionJsonPistonMetaDownloads", c -> {
            c.extendsFrom(minecraftPistonMeta.get());
            c.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, "versionjson"));
            });
        });
        project.getDependencies().addProvider(minecraftPistonMeta.getName(), minecraftVersion.map(v -> CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":"+PistonMetaMetadataRule.MINECRAFT+":"+v));

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

        this.vanillaConfigMaker = project.getObjects().newInstance(VanillaInstallationArtifacts.class);
        vanillaConfigMaker.getMinecraftVersion().set(getMinecraft());
        vanillaConfigMaker.getAccessTransformers().from(this.accessTransformersPath);
        vanillaConfigMaker.getInjectedInterfaces().from(this.injectedInterfacesPath);
        vanillaConfigMaker.getMappings().set(getDependencies().getMappings());
        this.binaryArtifactsTask = project.getTasks().register(name + "CrochetMinecraftBinaryArtifacts", TaskGraphExecution.class, task -> {
            task.setGroup("crochet setup");
            task.getConfigMaker().set(vanillaConfigMaker);
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

        this.minecraftResources = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"MinecraftResources");
        this.minecraft = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"Minecraft", config -> {
            config.setCanBeConsumed(false);
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
            config.attributes(attributes -> attributes.attributeProvider(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::attributeValue)));
        });

        this.minecraftLineMapped = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"MinecraftLineMapped", config -> {
            config.setCanBeConsumed(false);
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
            config.attributes(attributes -> attributes.attributeProvider(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::attributeValue)));
        });

        var decompileCompileClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoformCompileClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        var decompileRuntimeClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoformRuntimeClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });

        var useStubDeps = project.getProviders().gradleProperty(CrochetProperties.USE_STUB_GENERATED_MINECRAFT_DEPENDENCIES).map(Boolean::parseBoolean).orElse(false);
        getUseStubBackedMinecraftDependencies().convention(useStubDeps);

        minecraftDependencies.fromDependencyCollector(getDependencies().getMinecraftDependencies());

        this.binaryArtifactsTask.configure(task -> {
            task.artifactsConfiguration(decompileCompileClasspath);
            task.artifactsConfiguration(decompileRuntimeClasspath);
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-version-json", versionJsonPistonMeta.get());
            // Both for now as the config is always JOINED
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-client-jar", clientJarPistonMeta.get());
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-server-jar", serverJarPistonMeta.get());
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-mappings", mappingsPistonMeta.get(), vanillaConfigMaker.getMappings().map(AbstractVanillaInstallation::requiresVanillaMappings));
            task.artifactsConfiguration(project.getConfigurations().getByName(CrochetPlugin.TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME));
        });

        var binaryFiles = project.files(binary);
        binaryFiles.builtBy(binaryArtifactsTask);
        this.project.getDependencies().add(
            minecraft.getName(),
            binaryFiles
        );

        var lineMappedBinaryFiles = project.files(binaryLineMapped);
        lineMappedBinaryFiles.builtBy(lineMappedBinaryArtifactsTask);
        this.project.getDependencies().add(
            minecraftLineMapped.getName(),
            lineMappedBinaryFiles
        );

        var resourcesFiles = project.files(resources);
        resourcesFiles.builtBy(binaryArtifactsTask);

        this.project.getDependencies().add(
            minecraftResources.getName(),
            resourcesFiles
        );

        extension.idePostSync.configure(t -> t.dependsOn(binaryArtifactsTask));
    }

    private static boolean requiresVanillaMappings(MappingsStructure structure) {
        return switch (structure) {
            case ChainedMappingsStructure chainedMappingsStructure -> chainedMappingsStructure.getInputMappings().get().stream().anyMatch(AbstractVanillaInstallation::requiresVanillaMappings);
            case FileMappingsStructure ignored -> false;
            case MergedMappingsStructure mergedMappingsStructure -> mergedMappingsStructure.getInputMappings().get().stream().anyMatch(AbstractVanillaInstallation::requiresVanillaMappings);
            case MojangOfficialMappingsStructure ignored -> true;
            case ReversedMappingsStructure reversedMappingsStructure -> requiresVanillaMappings(reversedMappingsStructure.getInputMappings().get());
        };
    }

    @Override
    public AbstractVanillaInstallationDependencies getDependencies() {
        return (AbstractVanillaInstallationDependencies) dependencies;
    }

    @Override
    protected InstallationDependencies makeDependencies(Project project) {
        return project.getObjects().newInstance(AbstractVanillaInstallationDependencies.class, this);
    }

    @ApiStatus.Experimental
    public abstract Property<Boolean> getUseStubBackedMinecraftDependencies();

    public void setMinecraft(String string) {
        setMinecraft(project.provider(() -> string));
    }

    @SuppressWarnings("UnstableApiUsage")
    public void setMinecraft(Provider<String> string) {
        getDependencies().getMinecraftDependencies().add(
            project.provider(() -> project.getDependencies().create(
                (getUseStubBackedMinecraftDependencies().get() ? CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP + ":minecraft-dependencies" : "net.neoforged:minecraft-dependencies")+":"+string.get()
            ))
        );
    }

    public Provider<String> getMinecraft() {
        return this.minecraftVersion;
    }

    @Override
    public void forFeature(SourceSet sourceSet) {
        super.forFeature(sourceSet);
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(minecraft);
        });
    }

    @Override
    public void forLocalFeature(SourceSet sourceSet) {
        super.forLocalFeature(sourceSet);
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(minecraft);
        });
    }
}
