package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
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
    void forRun(Run run, RunType runType) {
        run.argFilesTask.configure(task -> task.getMinecraftVersion().set(getMinecraft()));

        run.classpath.fromDependencyCollector(run.getImplementation());

        switch (runType) {
            case CLIENT -> {
                run.getMainClass().convention("net.minecraft.client.main.Main");
                run.classpath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client"));
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
                run.classpath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server"));
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
                run.classpath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client"));
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
}
