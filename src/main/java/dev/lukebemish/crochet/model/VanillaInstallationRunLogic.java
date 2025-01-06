package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import org.gradle.api.Project;

public abstract class VanillaInstallationRunLogic {
    private final MinecraftInstallation minecraftInstallation;
    private final Project project;

    public VanillaInstallationRunLogic(MinecraftInstallation minecraftInstallation) {
        this.minecraftInstallation = minecraftInstallation;
        this.project = minecraftInstallation.crochetExtension.project;
    }

    void forRun(Run run, RunType runType) {
        run.argFilesTask.configure(task -> task.getMinecraftVersion().set(minecraftInstallation.getMinecraft()));

        run.classpath.fromDependencyCollector(run.getImplementation());

        project.afterEvaluate(p -> {
            if (run.getAvoidNeedlessDecompilation().get()) {
                run.classpath.extendsFrom(minecraftInstallation.minecraft);
            } else {
                run.classpath.extendsFrom(minecraftInstallation.minecraftLineMapped);
            }
        });

        switch (runType) {
            case CLIENT -> {
                run.getMainClass().convention("net.minecraft.client.main.Main");
                run.classpath.attributes(attributes -> attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client"));
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
                run.classpath.attributes(attributes -> attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "server"));
            }
            case DATA -> {
                // TODO: what's the right stuff to go here?
                run.getMainClass().convention("net.minecraft.data.Main");
                run.classpath.attributes(attributes -> attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, "client"));
            }
        }
    }
}
