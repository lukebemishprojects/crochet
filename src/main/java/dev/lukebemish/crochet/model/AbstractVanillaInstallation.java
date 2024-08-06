package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.tasks.ExtractConfigTask;
import dev.lukebemish.crochet.tasks.VanillaArtifactsTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;

public abstract class AbstractVanillaInstallation extends MinecraftInstallation {
    final Project project;
    final Provider<Configuration> clientMinecraft;
    final Provider<Configuration> serverMinecraft;
    final TaskProvider<ExtractConfigTask> extractConfig;
    final TaskProvider<VanillaArtifactsTask> artifactsTask;

    @Inject
    public AbstractVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.project = extension.project;

        var workingDirectory = project.getLayout().getBuildDirectory().dir("crochet/installations/" + name);

        this.downloadAssetsTask.configure(task -> {
            task.getArguments().add("--neoform");
            task.getArguments().add(this.getNeoFormModule().map(m -> m + "@zip"));

            // TODO: artifact manifest
        });

        this.artifactsTask = project.getTasks().register(name + "MinecraftArtifacts", VanillaArtifactsTask.class, task -> {
            task.setGroup("crochet setup");
            task.getAccessTransformers().from(this.accessTransformersPath);
            task.getNeoFormModule().set(this.getNeoFormModule().map(m -> m + "@zip"));
            task.getClientResources().set(workingDirectory.get().file("client-extra.jar"));
            task.getCompiled().set(workingDirectory.get().file("compiled.jar"));
            task.getSources().set(workingDirectory.get().file("sources.jar"));
            task.getSourcesAndCompiled().set(workingDirectory.get().file("sources-and-compiled.jar"));
            task.getRuntimeClasspath().from(project.getConfigurations().named(CrochetPlugin.NEOFORM_RUNTIME_CONFIGURATION_NAME));

            // TODO: artifact manifest
        });

        var minecraft = project.getConfigurations().maybeCreate(getName()+"Minecraft");

        this.clientMinecraft = this.project.getConfigurations().register(name+"ClientRuntimeClasspath", configuration -> {
            configuration.extendsFrom(minecraft);
            configuration.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client"));
        });
        this.serverMinecraft = this.project.getConfigurations().register(name+"ServerRuntimeClasspath", configuration -> {
            configuration.extendsFrom(minecraft);
            configuration.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server"));
        });

        if (Boolean.getBoolean("idea.sync.active")) {
            this.project.getDependencies().add(
                minecraft.getName(),
                project.files(artifactsTask.flatMap(VanillaArtifactsTask::getSourcesAndCompiled)).builtBy(artifactsTask)
            );
        } else {
            this.project.getDependencies().add(
                minecraft.getName(),
                project.files(artifactsTask.flatMap(VanillaArtifactsTask::getCompiled)).builtBy(artifactsTask)
            );
        }
        this.project.getDependencies().add(
            clientMinecraft.get().getName(),
            project.files(artifactsTask.flatMap(VanillaArtifactsTask::getClientResources)).builtBy(artifactsTask)
        );

        this.project.getDependencies().addProvider(
            minecraft.getName(),
            this.getNeoFormModule(),
            dependency ->
                dependency.capabilities(capabilities ->
                    capabilities.requireCapability("net.neoforged:neoform-dependencies")
                )
        );

        var neoFormOnlyConfiguration = project.getConfigurations().maybeCreate(name + "NeoFormOnly");
        this.project.getDependencies().addProvider(
            neoFormOnlyConfiguration.getName(),
            this.getNeoFormModule(),
            dependency -> dependency.setTransitive(false)
        );

        this.extractConfig = project.getTasks().register(name + "ExtractNeoFormConfig", ExtractConfigTask.class, task -> {
            task.getNeoForm().from(neoFormOnlyConfiguration);
            task.getNeoFormConfig().set(workingDirectory.get().file("config.json"));
        });

        extension.idePostSync.configure(t -> t.dependsOn(artifactsTask));
    }

    public abstract Property<String> getNeoFormModule();

    public void setNeoFormVersion(String version) {
        getNeoFormModule().set("net.neoforged:neoform:" + version);
    }

    @Override
    public void forSourceSet(SourceSet sourceSet) {
        super.forSourceSet(sourceSet);
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(clientMinecraft.get());
        });
    }
}
