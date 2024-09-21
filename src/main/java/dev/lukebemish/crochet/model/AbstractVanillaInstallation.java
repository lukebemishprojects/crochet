package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.tasks.CreateArtifactManifest;
import dev.lukebemish.crochet.tasks.ExtractConfigTask;
import dev.lukebemish.crochet.tasks.TaskGraphExecution;
import dev.lukebemish.crochet.tasks.VanillaArtifactsTask;
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
    final TaskProvider<TaskGraphExecution> artifactsTask;
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

        this.artifactsTask = project.getTasks().register(name + "CrochetMinecraftArtifacts", TaskGraphExecution.class, task -> {
            task.setGroup("crochet setup");
            var vanillaArtifacts = project.getObjects().newInstance(VanillaInstallationArtifacts.class);
            vanillaArtifacts.getMinecraftVersion().set(getMinecraft());
            task.getConfigMaker().set(vanillaArtifacts);
            vanillaArtifacts.getAccessTransformers().from(this.accessTransformersPath);
            task.getTargets().put(
                "resources",
                resources
            );
            task.getTargets().put(
                "binary",
                binary
            );
            task.getTargets().put(
                "sources",
                sources
            );
            task.getClasspath().from(project.getConfigurations().named(CrochetPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        this.downloadAssetsTask.configure(task -> {
            task.getConfigMaker().set(this.artifactsTask.flatMap(TaskGraphExecution::getConfigMaker));
            task.getArtifactFiles().set(this.artifactsTask.flatMap(TaskGraphExecution::getArtifactFiles));
            task.getArtifactIdentifiers().set(this.artifactsTask.flatMap(TaskGraphExecution::getArtifactIdentifiers));
            task.getJavaLauncher().set(this.artifactsTask.flatMap(TaskGraphExecution::getJavaLauncher));
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

        this.artifactsTask.configure(task -> {
            task.artifactsConfiguration(decompileCompileClasspath);
            task.artifactsConfiguration(decompileRuntimeClasspath);
            task.artifactsConfiguration(project.getConfigurations().getByName(CrochetPlugin.TASK_GRAPH_RUNNER_DEPENDENCIES_CONFIGURATION_NAME));
        });

        var binaryFiles = project.files(binary);
        binaryFiles.builtBy(artifactsTask);
        this.project.getDependencies().add(
            minecraft.getName(),
            binaryFiles
        );

        var resourcesFiles = project.files(resources);
        resourcesFiles.builtBy(artifactsTask);

        this.project.getDependencies().add(
            minecraft.getName(),
            resourcesFiles
        );

        extension.idePostSync.configure(t -> t.dependsOn(artifactsTask));
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
