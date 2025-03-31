package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.ConfigurationUtils;
import org.gradle.api.Project;

abstract class VanillaInstallationLogic {
    private final MinecraftInstallation minecraftInstallation;
    private final Project project;

    VanillaInstallationLogic(MinecraftInstallation minecraftInstallation) {
        this.minecraftInstallation = minecraftInstallation;
        this.project = minecraftInstallation.crochetExtension.project;
    }

    void forRun(Run run, RunType runType) {
        run.argFilesTask.configure(task -> task.getMinecraftVersion().set(minecraftInstallation.getMinecraft()));

        var implementation = ConfigurationUtils.dependencyScopeInternal(project, run.getName(), "runImplementation", c -> {});
        implementation.fromDependencyCollector(run.getImplementation());
        run.classpath.extendsFrom(implementation);

        project.afterEvaluate(p -> {
            if (run.getAvoidNeedlessDecompilation().get()) {
                run.classpath.extendsFrom(minecraftInstallation.minecraft);
            } else {
                run.classpath.extendsFrom(minecraftInstallation.minecraftLineMapped);
            }
        });

        runType.distribution().apply(run.classpath.getAttributes());

        switch (runType) {
            case CLIENT -> {
                run.getMainClass().convention("net.minecraft.client.main.Main");
                run.getArgs().addAll(
                    "--gameDir", ".",
                    "--assetIndex", "${assets_index_name}",
                    "--assetsDir", "${assets_root}",
                    "--accessToken", "NotValid",
                    "--version", "${minecraft_version}"
                );
            }
            case SERVER -> {
                run.getMainClass().convention("net.minecraft.server.Main");
            }
            case DATA -> {
                // TODO: what's the right stuff to go here?
                run.getMainClass().convention("net.minecraft.data.Main");
            }
        }
    }
}
