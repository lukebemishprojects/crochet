package dev.lukebemish.crochet.model;

import org.gradle.api.artifacts.dsl.DependencyCollector;

import javax.inject.Inject;

@SuppressWarnings("UnstableApiUsage")
public abstract class FabricInstallationDependencies extends InstallationDependencies {
    @Inject
    public FabricInstallationDependencies(MinecraftInstallation installation) {
        super(installation);
    }

    public abstract DependencyCollector getLoader();

    public abstract DependencyCollector getIntermediary();

    public abstract DependencyCollector getAccessWideners();
}
