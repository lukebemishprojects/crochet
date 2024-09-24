package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.IdeaModelHandlerPlugin;
import dev.lukebemish.crochet.tasks.TaskGraphExecution;
import dev.lukebemish.crochet.tasks.VanillaInstallationArtifacts;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;

public abstract class AbstractVanillaInstallation extends MinecraftInstallation {
    final Project project;
    final Configuration minecraft;
    final TaskProvider<TaskGraphExecution> binaryArtifactsTask;
    final TaskProvider<TaskGraphExecution> sourcesArtifactsTask;
    final VanillaInstallationArtifacts vanillaConfigMaker;
    final Provider<Directory> workingDirectory;

    final Provider<RegularFile> sources;
    final Provider<RegularFile> resources;
    final Provider<RegularFile> binary;

    @Inject
    public AbstractVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.project = extension.project;

        var workingDirectory = project.getLayout().getBuildDirectory().dir("crochet/installations/" + name);
        this.workingDirectory = workingDirectory;

        this.resources = workingDirectory.map(it -> it.file("extra-resources.jar"));
        this.binary = workingDirectory.map(it -> it.file("compiled.jar"));
        this.sources = workingDirectory.map(it -> it.file("sources.jar"));

        if (IdeaModelHandlerPlugin.isIdeaSyncRelated(project)) {
            var model = IdeaModelHandlerPlugin.retrieve(project);
            model.mapBinaryToSource(binary, sources);
        }

        this.vanillaConfigMaker = project.getObjects().newInstance(VanillaInstallationArtifacts.class);
        vanillaConfigMaker.getMinecraftVersion().set(getMinecraft());
        vanillaConfigMaker.getAccessTransformers().from(this.accessTransformersPath);
        this.binaryArtifactsTask = project.getTasks().register(name + "CrochetMinecraftBinaryArtifacts", TaskGraphExecution.class, task -> {
            task.setGroup("crochet setup");
            task.getConfigMaker().set(vanillaConfigMaker);
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("resources", resources, project.getObjects()));
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("binary", binary, project.getObjects()));
            task.getClasspath().from(project.getConfigurations().named(CrochetPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        this.sourcesArtifactsTask = project.getTasks().register(name + "CrochetMinecraftSourcesArtifacts", TaskGraphExecution.class, task -> {
            task.copyConfigFrom(binaryArtifactsTask.get());
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("sources", sources, project.getObjects()));
        });

        extension.generateSources.configure(t -> t.dependsOn(this.sourcesArtifactsTask));

        this.downloadAssetsTask.configure(task -> {
            task.copyConfigFrom(binaryArtifactsTask.get());
        });

        var minecraftDependencies = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"MinecraftDependencies");
        this.minecraft = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"Minecraft", config -> {
            config.extendsFrom(minecraftDependencies);
            config.attributes(attributes -> attributes.attributeProvider(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::attributeValue)));
        });

        var decompileCompileClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoformCompileClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        var decompileRuntimeClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoformRuntimeClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });

        this.project.getDependencies().addProvider(
            minecraftDependencies.getName(),
            getMinecraft().map(it -> "net.neoforged:minecraft-dependencies:"+it)
        );

        this.binaryArtifactsTask.configure(task -> {
            task.artifactsConfiguration(decompileCompileClasspath);
            task.artifactsConfiguration(decompileRuntimeClasspath);
            task.artifactsConfiguration(project.getConfigurations().getByName(CrochetPlugin.TASK_GRAPH_RUNNER_DEPENDENCIES_CONFIGURATION_NAME));
        });

        var binaryFiles = project.files(binary);
        binaryFiles.builtBy(binaryArtifactsTask);
        this.project.getDependencies().add(
            minecraft.getName(),
            binaryFiles
        );

        var resourcesFiles = project.files(resources);
        resourcesFiles.builtBy(binaryArtifactsTask);

        this.project.getDependencies().add(
            minecraft.getName(),
            resourcesFiles
        );

        extension.idePostSync.configure(t -> t.dependsOn(binaryArtifactsTask));
    }

    public abstract Property<String> getMinecraft();

    @Override
    public void forSourceSet(SourceSet sourceSet) {
        super.forSourceSet(sourceSet);
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(minecraft);
        });
    }
}
