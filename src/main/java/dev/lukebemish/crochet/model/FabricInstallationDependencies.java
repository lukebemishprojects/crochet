package dev.lukebemish.crochet.model;

import org.gradle.api.artifacts.dsl.DependencyCollector;

@SuppressWarnings("UnstableApiUsage")
public abstract class FabricInstallationDependencies extends InstallationDependencies {
    public abstract DependencyCollector getLoader();
}