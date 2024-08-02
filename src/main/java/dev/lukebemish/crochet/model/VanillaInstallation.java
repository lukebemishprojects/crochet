package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.tasks.VanillaArtifactsTask;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;

public abstract class VanillaInstallation extends MinecraftInstallation {
    @Inject
    public VanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        var project = extension.project;

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
    }

    public abstract Property<String> getNeoFormModule();

    public void setNeoFormVersion(String version) {
        getNeoFormModule().set("net.neoforged:neoform:" + version);
    }

    public void setNeoFormVersion(Provider<String> version) {
        getNeoFormModule().set(version.map(v -> "net.neoforged:neoform:" + v));
    }
}
