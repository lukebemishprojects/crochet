package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.tasks.ExtractConfigTask;
import org.gradle.api.Action;

import javax.inject.Inject;

public abstract class VanillaInstallation extends AbstractVanillaInstallation {
    @Inject
    public VanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);
    }

    public void dependencies(Action<InstallationDependencies> action) {
        action.execute(getDependencies());
    }

    @Override
    protected void forRun(Run run, RunType runType) {
        run.argFilesTask.configure(task -> {
            task.dependsOn(extractConfig);
            task.getNeoFormConfig().set(extractConfig.flatMap(ExtractConfigTask::getNeoFormConfig));
        });
        switch (runType) {
            case CLIENT -> {
                run.getMainClass().convention("net.minecraft.client.main.Main");
                run.classpath.extendsFrom(clientMinecraft.get());
                run.getArgs().addAll(
                    "--gameDir", ".",
                    "--assetIndex", "${assets_index_name}",
                    "--assetsDir", "${assets_root}",
                    "--accessToken", "NotValid",
                    "--version", "${minecraft_version}"
                );
            }
            case SERVER -> {
                run.classpath.extendsFrom(serverMinecraft.get());
                run.getMainClass().convention("net.minecraft.server.Main");
            }
            case DATA -> {
                // TODO: what's the right stuff to go here?
                run.classpath.extendsFrom(clientMinecraft.get());
                run.getMainClass().convention("net.minecraft.data.Main");
            }
        }
    }
}
