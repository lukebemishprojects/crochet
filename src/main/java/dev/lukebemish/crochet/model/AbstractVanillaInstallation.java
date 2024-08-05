package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.tasks.VanillaArtifactsTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;

public abstract class AbstractVanillaInstallation extends MinecraftInstallation {
    final Project project;
    final Configuration minecraft;

    @Inject
    public AbstractVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.project = extension.project;

        var workingDirectory = project.getLayout().getBuildDirectory().dir("crochet/" + name);

        var artifactsTask = project.getTasks().register(name + "MinecraftArtifacts", VanillaArtifactsTask.class, task -> {
            task.getAccessTransformers().from(this.accessTransformersPath);
            task.getNeoFormModule().set(this.getNeoFormModule().map(m -> m + "@zip"));
            task.getClientResources().set(workingDirectory.get().file("client-extra.jar"));
            task.getCompiled().set(workingDirectory.get().file("compiled.jar"));
            task.getSources().set(workingDirectory.get().file("sources.jar"));
            task.getSourcesAndCompiled().set(workingDirectory.get().file("sources-and-compiled.jar"));
            task.getRuntimeClasspath().from(project.getConfigurations().named(CrochetPlugin.NEOFORM_RUNTIME_CONFIGURATION_NAME));

            // TODO: artifact manifest
        });

        this.minecraft = project.getConfigurations().maybeCreate(getName()+"Minecraft");
        this.project.getDependencies().add(
            minecraft.getName(),
            project.files(artifactsTask.flatMap(VanillaArtifactsTask::getSourcesAndCompiled)).builtBy(artifactsTask)
        );
        this.project.getDependencies().addProvider(
            minecraft.getName(),
            this.getNeoFormModule(),
            dependency ->
                dependency.capabilities(capabilities ->
                    capabilities.requireCapability("net.neoforged:neoform-dependencies")
                )
        );

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
            config.extendsFrom(minecraft);
        });
    }
}
