package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.CrochetProperties;
import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.CrochetRepositoriesPlugin;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.PistonMetaMetadataRule;
import dev.lukebemish.crochet.model.mappings.ChainedMappingsStructure;
import dev.lukebemish.crochet.model.mappings.FileMappingsStructure;
import dev.lukebemish.crochet.model.mappings.MappingsStructure;
import dev.lukebemish.crochet.model.mappings.MergedMappingsStructure;
import dev.lukebemish.crochet.model.mappings.MojangOfficialMappingsStructure;
import dev.lukebemish.crochet.model.mappings.ReversedMappingsStructure;
import dev.lukebemish.crochet.internal.tasks.VanillaInstallationArtifacts;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.attributes.Usage;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;

public abstract class AbstractVanillaInstallation extends LocalMinecraftInstallation {
    final Project project;
    final VanillaInstallationArtifacts vanillaConfigMaker;

    @Inject
    public AbstractVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.project = extension.project;

        var minecraftPistonMeta = ConfigurationUtils.pistonMetaDependencies(this, name);
        var clientJarPistonMeta = ConfigurationUtils.pistonMeta(this, name, minecraftPistonMeta, ConfigurationUtils.PistonMetaPiece.CLIENT_JAR);
        var serverJarPistonMeta = ConfigurationUtils.pistonMeta(this, name, minecraftPistonMeta, ConfigurationUtils.PistonMetaPiece.SERVER_JAR);
        var mappingsPistonMeta = ConfigurationUtils.pistonMeta(this, name, minecraftPistonMeta, ConfigurationUtils.PistonMetaPiece.CLIENT_MAPPINGS);
        var versionJsonPistonMeta = ConfigurationUtils.pistonMeta(this, name, minecraftPistonMeta, ConfigurationUtils.PistonMetaPiece.VERSION_JSON);
        project.getDependencies().addProvider(minecraftPistonMeta.getName(), getMinecraft().map(v -> CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":"+PistonMetaMetadataRule.MINECRAFT+":"+v));

        this.vanillaConfigMaker = project.getObjects().newInstance(VanillaInstallationArtifacts.class);
        vanillaConfigMaker.getMinecraftVersion().set(getMinecraft());
        vanillaConfigMaker.getAccessTransformers().from(this.accessTransformersPath);
        vanillaConfigMaker.getInjectedInterfaces().from(this.injectedInterfacesPath);
        vanillaConfigMaker.getMappings().set(getDependencies().getMappings());
        vanillaConfigMaker.getDistribution().set(getDistribution());
        this.binaryArtifactsTask.configure(t -> t.getConfigMaker().set(vanillaConfigMaker));

        var decompileCompileClasspath = ConfigurationUtils.resolvableInternal(this, name, "RunnerCompileClasspath", c -> {
            c.extendsFrom(minecraftDependencies);
            c.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        var decompileRuntimeClasspath = ConfigurationUtils.resolvableInternal(this, name, "RunnerRuntimeClasspath", c -> {
            c.extendsFrom(minecraftDependencies);
            c.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });

        var useStubDeps = project.getProviders().gradleProperty(CrochetProperties.USE_STUB_GENERATED_MINECRAFT_DEPENDENCIES).map(Boolean::parseBoolean).orElse(true);
        getUseStubBackedMinecraftDependencies().convention(useStubDeps);

        minecraftDependencies.fromDependencyCollector(getDependencies().getMinecraftDependencies());

        this.binaryArtifactsTask.configure(task -> {
            task.artifactsConfiguration(decompileCompileClasspath);
            task.artifactsConfiguration(decompileRuntimeClasspath);
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-version-json", versionJsonPistonMeta);
            // Both for now as the config is always JOINED
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-client-jar", clientJarPistonMeta);
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-server-jar", serverJarPistonMeta);
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-mappings", mappingsPistonMeta, vanillaConfigMaker.getMappings().map(AbstractVanillaInstallation::requiresVanillaMappings));
            task.artifactsConfiguration(project.getConfigurations().getByName(CrochetProjectPlugin.TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME));
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
    public AbstractVanillaInstallationDependencies<?> getDependencies() {
        return (AbstractVanillaInstallationDependencies<?>) dependencies;
    }

    @ApiStatus.Experimental
    public abstract Property<Boolean> getUseStubBackedMinecraftDependencies();

    public void setMinecraft(String string) {
        setMinecraft(project.provider(() -> string));
    }

    /**
     * Sets the Minecraft version to use for this installation; the version can be a string or a {@link VersionConstraint}.
     */
    @SuppressWarnings("UnstableApiUsage")
    public void setMinecraft(Provider<?> provider) {
        getDependencies().getMinecraftDependencies().add(
            project.provider(() -> dependencies.module(
                (getUseStubBackedMinecraftDependencies().get() ? CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP + ":minecraft-dependencies" : "net.neoforged:minecraft-dependencies")
            )), dep -> {
                var value = provider.get();
                if (value instanceof VersionConstraint version) {
                    dep.version(v -> {
                        if (!version.getPreferredVersion().isEmpty()) v.prefer(version.getPreferredVersion());
                        if (!version.getRejectedVersions().isEmpty()) v.reject(version.getRejectedVersions().toArray(String[]::new));
                        if (version.getBranch() != null) v.setBranch(version.getBranch());
                        if (!version.getStrictVersion().isEmpty()) v.strictly(version.getStrictVersion());
                        if (!version.getRequiredVersion().isEmpty()) v.require(version.getRequiredVersion());
                    });
                } else if (value instanceof String string) {
                    dep.version(v -> v.require(string));
                } else {
                    throw new IllegalArgumentException("Unsupported type for minecraft version: " + value.getClass());
                }
            }
        );
    }

    public void setMinecraft(VersionConstraint version) {
        setMinecraft(project.provider(() -> version));
    }

    @Override
    public void forFeature(SourceSet sourceSet) {
        super.forFeature(sourceSet);
    }

    @Override
    public void forLocalFeature(SourceSet sourceSet) {
        super.forLocalFeature(sourceSet);
    }
}
