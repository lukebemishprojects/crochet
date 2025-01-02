package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.CrochetProperties;
import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.CrochetRepositoriesPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.PistonMetaMetadataRule;
import dev.lukebemish.crochet.mappings.ChainedMappingsStructure;
import dev.lukebemish.crochet.mappings.FileMappingsStructure;
import dev.lukebemish.crochet.mappings.MappingsStructure;
import dev.lukebemish.crochet.mappings.MergedMappingsStructure;
import dev.lukebemish.crochet.mappings.MojangOfficialMappingsStructure;
import dev.lukebemish.crochet.mappings.ReversedMappingsStructure;
import dev.lukebemish.crochet.internal.tasks.VanillaInstallationArtifacts;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Map;

public abstract class AbstractVanillaInstallation extends MinecraftInstallation {
    final Project project;
    final VanillaInstallationArtifacts vanillaConfigMaker;

    final Property<String> minecraftVersion;

    @Inject
    public AbstractVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.project = extension.project;

        // Create early so getMinecraft provider works right
        this.minecraftVersion = project.getObjects().property(String.class);
        this.minecraftVersion.set(minecraftDependencies.getIncoming().getResolutionResult().getRootComponent().map(ConfigurationUtils::extractMinecraftVersion));

        var minecraftPistonMeta = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"PistonMetaDownloads");
        var clientJarPistonMeta = project.getConfigurations().resolvable("crochet"+StringUtils.capitalize(name)+"ClientJarPistonMetaDownloads", c -> {
            c.extendsFrom(minecraftPistonMeta.get());
            c.exclude(Map.of(
                "group", CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP,
                "module", PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES
            ));
            c.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
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
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "server");
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            });
        });
        var mappingsPistonMeta = project.getConfigurations().resolvable("crochet"+StringUtils.capitalize(name)+"MappingsPistonMetaDownloads", c -> {
            c.extendsFrom(minecraftPistonMeta.get());
            c.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
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

        this.vanillaConfigMaker = project.getObjects().newInstance(VanillaInstallationArtifacts.class);
        vanillaConfigMaker.getMinecraftVersion().set(getMinecraft());
        vanillaConfigMaker.getAccessTransformers().from(this.accessTransformersPath);
        vanillaConfigMaker.getInjectedInterfaces().from(this.injectedInterfacesPath);
        vanillaConfigMaker.getMappings().set(getDependencies().getMappings());
        vanillaConfigMaker.getDistribution().set(getDistribution());
        this.binaryArtifactsTask.configure(t -> t.getConfigMaker().set(vanillaConfigMaker));

        var decompileCompileClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"RunnerCompileClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        var decompileRuntimeClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"RunnerRuntimeClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
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
        sharedFeature(sourceSet);
        FeatureUtils.forSourceSetFeature(project, sourceSet.getName(), context -> {
            Action<AttributeContainer> attributesAction = attributes -> {
                var dist = getDistribution().get();
                if (dist != InstallationDistribution.JOINED) {
                    attributes.attribute(CrochetPlugin.CROCHET_DISTRIBUTION_ATTRIBUTE, dist.name().toLowerCase(Locale.ROOT));
                }
            };
            context.getRuntimeElements().attributes(attributesAction);
            context.getApiElements().attributes(attributesAction);
            project.getConfigurations().getByName(context.getSourceSet().getCompileClasspathConfigurationName()).attributes(attributesAction);
            project.getConfigurations().getByName(context.getSourceSet().getRuntimeClasspathConfigurationName()).attributes(attributesAction);
        });
    }

    private void sharedFeature(SourceSet sourceSet) {
        Action<AttributeContainer> attributesAction = attributes -> {
            var dist = getDistribution().get();
            if (dist != InstallationDistribution.JOINED) {
                attributes.attribute(CrochetPlugin.CROCHET_DISTRIBUTION_ATTRIBUTE, dist.name().toLowerCase(Locale.ROOT));
            }
        };
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(minecraft);
            config.shouldResolveConsistentlyWith(switch (getDistribution().get()) {
                case CLIENT, JOINED -> nonUpgradableClientCompileDependencies;
                case SERVER, COMMON -> nonUpgradableServerCompileDependencies;
            });
            config.attributes(attributesAction);
        });
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.attributes(attributesAction);
        });
    }

    @Override
    public void forLocalFeature(SourceSet sourceSet) {
        super.forLocalFeature(sourceSet);
        sharedFeature(sourceSet);
    }
}
