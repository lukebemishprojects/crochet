package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.CrochetRepositoriesPlugin;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.PistonMetaMetadataRule;
import dev.lukebemish.crochet.internal.tasks.NeoFormInstallationArtifacts;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Usage;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.stream.Collectors;

public abstract class NeoFormInstallation extends LocalMinecraftInstallation {
    final Project project;
    final NeoFormInstallationArtifacts neoFormConfigMaker;

    final Configuration parchment;
    final Configuration neoFormConfigDependencies;
    final Configuration neoFormConfig;

    @Inject
    public NeoFormInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.project = extension.project;

        var minecraftPistonMeta = ConfigurationUtils.pistonMetaDependencies(this, name);
        var clientJarPistonMeta = ConfigurationUtils.pistonMeta(this, name, minecraftPistonMeta, ConfigurationUtils.PistonMetaPiece.CLIENT_JAR);
        var serverJarPistonMeta = ConfigurationUtils.pistonMeta(this, name, minecraftPistonMeta, ConfigurationUtils.PistonMetaPiece.SERVER_JAR);
        var clientMappingsPistonMeta = ConfigurationUtils.pistonMeta(this, name, minecraftPistonMeta, ConfigurationUtils.PistonMetaPiece.CLIENT_MAPPINGS);
        var serverMappingsPistonMeta = ConfigurationUtils.pistonMeta(this, name, minecraftPistonMeta, ConfigurationUtils.PistonMetaPiece.SERVER_MAPPINGS);
        var versionJsonPistonMeta = ConfigurationUtils.pistonMeta(this, name, minecraftPistonMeta, ConfigurationUtils.PistonMetaPiece.VERSION_JSON);
        project.getDependencies().addProvider(minecraftPistonMeta.getName(), getMinecraft().map(v -> CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP+":"+PistonMetaMetadataRule.MINECRAFT+":"+v));

        this.parchment = ConfigurationUtils.dependencyScope(this, name, null, "parchment", c -> {});
        this.parchment.fromDependencyCollector(getDependencies().getParchment());

        var parchmentData = ConfigurationUtils.resolvableInternal(this, name, "parchmentData", c -> {
            c.extendsFrom(parchment);
        });

        this.neoFormConfigMaker = project.getObjects().newInstance(NeoFormInstallationArtifacts.class);
        neoFormConfigMaker.getAccessTransformers().from(this.accessTransformersPath);
        neoFormConfigMaker.getInjectedInterfaces().from(this.injectedInterfacesPath);
        neoFormConfigMaker.getParchment().from(parchmentData);
        neoFormConfigMaker.getRecompile().set(this.getRecompile());
        this.binaryArtifactsTask.configure(t -> t.getConfigMaker().set(neoFormConfigMaker));

        var decompileCompileClasspath = ConfigurationUtils.resolvableInternal(this, name, "neoFormCompileClasspath", c -> {
            c.extendsFrom(minecraftDependencies);
            c.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        var decompileRuntimeClasspath = ConfigurationUtils.resolvableInternal(this, name, "neoFormRuntimeClasspath", c -> {
            c.extendsFrom(minecraftDependencies);
            c.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });

        var neoForm = ConfigurationUtils.dependencyScope(this, name, null, "neoForm", c -> {});
        this.neoFormConfigDependencies = ConfigurationUtils.resolvableInternal(this, name, "neoFormConfigDependencies", c -> {
            c.extendsFrom(neoForm);
            c.attributes(attributes -> attributes.attributeProvider(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::neoAttributeValue)));
        });
        this.neoFormConfig = ConfigurationUtils.resolvableInternal(this, name, "neoFormConfig", c -> {
            c.extendsFrom(neoForm);
            c.setTransitive(false);
            c.attributes(attributes -> attributes.attributeProvider(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::neoAttributeValue)));
        });

        neoForm.fromDependencyCollector(getDependencies().getNeoForm());
        this.minecraftDependencies.getDependencies().addAllLater(project.provider(() -> neoForm.getAllDependencies().stream().map(d -> {
            var copy = d.copy();
            if (copy instanceof ModuleDependency moduleDependency) {
                copy = moduleDependency.capabilities(capabilities -> {
                    capabilities.requireCapability("net.neoforged:neoform-dependencies");
                });
            }
            return copy;
        }).collect(Collectors.toCollection(ArrayList::new))));
        this.minecraftDependencies.getDependencyConstraints().addAllLater(project.provider(neoForm::getDependencyConstraints));

        neoFormConfigMaker.getNeoForm().from(neoFormConfig);

        this.binaryArtifactsTask.configure(task -> {
            task.artifactsConfiguration(decompileCompileClasspath);
            task.artifactsConfiguration(decompileRuntimeClasspath);
            task.artifactsConfiguration(neoFormConfigDependencies);
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-version-json", versionJsonPistonMeta);
            // Both for now as the config is always JOINED
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-client-jar", clientJarPistonMeta);
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-server-jar", serverJarPistonMeta);
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-client-mappings", clientMappingsPistonMeta);
            task.singleFileConfiguration("dev.lukebemish.crochet.internal:minecraft-server-mappings", serverMappingsPistonMeta);
            task.artifactsConfiguration(project.getConfigurations().getByName(CrochetProjectPlugin.TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME));
        });

        this.getRecompile().convention(false);
    }

    @Override
    public NeoFormInstallationDependencies getDependencies() {
        return (NeoFormInstallationDependencies) dependencies;
    }

    public void dependencies(Action<? super NeoFormInstallationDependencies> action) {
        action.execute(getDependencies());
    }

    @Override
    protected NeoFormInstallationDependencies makeDependencies(Project project) {
        return project.getObjects().newInstance(NeoFormInstallationDependencies.class, this);
    }

    public abstract Property<Boolean> getRecompile();

    @Override
    void forRun(Run run, RunType runType) {
        super.forRun(run, runType);
        run.argFilesTask.configure(task -> task.getMinecraftVersion().set(getMinecraft()));

        var implementation = ConfigurationUtils.dependencyScopeInternal(project, run.getName(), "runImplementation", c -> {});
        implementation.fromDependencyCollector(run.getImplementation());
        run.classpath.extendsFrom(implementation);

        switch (runType) {
            case CLIENT -> {
                run.getMainClass().convention("net.minecraft.client.main.Main");
                run.classpath.attributes(attributes -> attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client"));
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
                run.classpath.attributes(attributes -> attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "server"));
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
                run.classpath.attributes(attributes -> attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client"));
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
    }

    @Override
    public void forLocalFeature(SourceSet sourceSet) {
        super.forLocalFeature(sourceSet);
    }

    @Override
    protected String sharingInstallationTypeTag() {
        return "neoform";
    }
}
