package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.CrochetRepositoriesPlugin;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.PistonMetaMetadataRule;
import dev.lukebemish.crochet.internal.tasks.NeoFormInstallationArtifacts;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class NeoFormInstallation extends MinecraftInstallation {
    final Project project;
    final NeoFormInstallationArtifacts neoFormConfigMaker;

    final Property<String> minecraftVersion;

    final Configuration parchmentData;
    final Configuration neoFormConfigDependencies;
    final Configuration neoFormConfig;

    @Inject
    public NeoFormInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.project = extension.project;
        this.minecraftVersion = project.getObjects().property(String.class);

        var minecraftPistonMeta = project.getConfigurations().dependencyScope("crochet"+ StringUtils.capitalize(name)+"PistonMetaDownloads");
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
        var clientMappingsPistonMeta = project.getConfigurations().resolvable("crochet"+StringUtils.capitalize(name)+"ClientMappingsPistonMetaDownloads", c -> {
            c.extendsFrom(minecraftPistonMeta.get());
            c.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, "mappings"));
            });
        });
        var serverMappingsPistonMeta = project.getConfigurations().resolvable("crochet"+StringUtils.capitalize(name)+"ServerMappingsPistonMetaDownloads", c -> {
            c.extendsFrom(minecraftPistonMeta.get());
            c.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "server");
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

        this.parchmentData = project.getConfigurations().create(name+"ParchmentData");
        this.parchmentData.fromDependencyCollector(getDependencies().getParchment());

        this.neoFormConfigMaker = project.getObjects().newInstance(NeoFormInstallationArtifacts.class);
        neoFormConfigMaker.getAccessTransformers().from(this.accessTransformersPath);
        neoFormConfigMaker.getInjectedInterfaces().from(this.injectedInterfacesPath);
        neoFormConfigMaker.getParchment().from(this.parchmentData);
        neoFormConfigMaker.getRecompile().set(this.getRecompile());
        this.binaryArtifactsTask.configure(t -> t.getConfigMaker().set(neoFormConfigMaker));

        var decompileCompileClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoformCompileClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        var decompileRuntimeClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoformRuntimeClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });

        this.neoFormConfigDependencies = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoformConfig", config -> {
            config.setCanBeConsumed(false);
            config.attributes(attributes -> attributes.attributeProvider(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::neoAttributeValue)));
        });
        this.neoFormConfig = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"Neoform", config -> {
            config.extendsFrom(this.neoFormConfigDependencies);
            config.setCanBeConsumed(false);
            config.setTransitive(false);
            config.attributes(attributes -> attributes.attributeProvider(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::neoAttributeValue)));
        });

        this.neoFormConfigDependencies.fromDependencyCollector(getDependencies().getNeoForm());
        this.minecraftDependencies.getDependencies().addAllLater(project.provider(() -> this.neoFormConfigDependencies.getAllDependencies().stream().map(d -> {
            var copy = d.copy();
            if (copy instanceof ModuleDependency moduleDependency) {
                copy = moduleDependency.capabilities(capabilities -> {
                    capabilities.requireCapability("net.neoforged:neoform-dependencies");
                });
            }
            return copy;
        }).collect(Collectors.toCollection(ArrayList::new))));
        this.minecraftVersion.set(minecraftDependencies.getIncoming().getResolutionResult().getRootComponent().map(ConfigurationUtils::extractMinecraftVersion));

        neoFormConfigMaker.getNeoForm().from(neoFormConfig);

        this.minecraftDependencies.getDependencyConstraints().addAllLater(project.provider(this.neoFormConfigDependencies::getDependencyConstraints));

        this.binaryArtifactsTask.configure(task -> {
            task.artifactsConfiguration(decompileCompileClasspath);
            task.artifactsConfiguration(decompileRuntimeClasspath);
            task.artifactsConfiguration(neoFormConfigDependencies);
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-version-json", versionJsonPistonMeta.get());
            // Both for now as the config is always JOINED
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-client-jar", clientJarPistonMeta.get());
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-server-jar", serverJarPistonMeta.get());
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-client-mappings", clientMappingsPistonMeta.get());
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-server-mappings", serverMappingsPistonMeta.get());
            task.artifactsConfiguration(project.getConfigurations().getByName(CrochetPlugin.TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME));
        });

        this.getRecompile().convention(false);
    }

    @Override
    public NeoFormInstallationDependencies getDependencies() {
        return (NeoFormInstallationDependencies) dependencies;
    }

    public void dependencies(Action<NeoFormInstallationDependencies> action) {
        action.execute(getDependencies());
    }

    @Override
    protected AbstractInstallationDependencies makeDependencies(Project project) {
        return project.getObjects().newInstance(NeoFormInstallationDependencies.class, this);
    }

    public abstract Property<Boolean> getRecompile();

    public Provider<String> getMinecraft() {
        return this.minecraftVersion;
    }

    @Override
    void forRun(Run run, RunType runType) {
        super.forRun(run, runType);
        run.argFilesTask.configure(task -> task.getMinecraftVersion().set(getMinecraft()));

        run.classpath.fromDependencyCollector(run.getImplementation());

        switch (runType) {
            case CLIENT -> {
                run.getMainClass().convention("net.minecraft.client.main.Main");
                run.classpath.attributes(attributes -> attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client"));
                project.afterEvaluate(p -> {
                    if (run.getAvoidNeedlessDecompilation().get()) {
                        run.classpath.extendsFrom(minecraft);
                    } else {
                        run.classpath.extendsFrom(minecraftLineMapped);
                    }
                });
                run.getArgs().addAll(
                    "--gameDir", ".",
                    "--assetIndex", "${assets_index_name}",
                    "--assetsDir", "${assets_root}",
                    "--accessToken", "NotValid",
                    "--version", "${minecraft_version}"
                );
            }
            case SERVER -> {
                run.classpath.attributes(attributes -> attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "server"));
                project.afterEvaluate(p -> {
                    if (run.getAvoidNeedlessDecompilation().get()) {
                        run.classpath.extendsFrom(minecraft);
                    } else {
                        run.classpath.extendsFrom(minecraftLineMapped);
                    }
                });
                run.getMainClass().convention("net.minecraft.server.Main");
            }
            case DATA -> {
                // TODO: what's the right stuff to go here?
                run.classpath.attributes(attributes -> attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client"));
                project.afterEvaluate(p -> {
                    if (run.getAvoidNeedlessDecompilation().get()) {
                        run.classpath.extendsFrom(minecraft);
                    } else {
                        run.classpath.extendsFrom(minecraftLineMapped);
                    }
                });
                run.getMainClass().convention("net.minecraft.data.Main");
            }
        }
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
