package dev.lukebemish.crochet.model;

import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

@SuppressWarnings("UnstableApiUsage")
public abstract class FabricSourceSetDependencies implements Dependencies {
    public abstract DependencyCollector getModCompileOnly();
    public abstract DependencyCollector getModCompileOnlyApi();
    public abstract DependencyCollector getModRuntimeOnly();
    public abstract DependencyCollector getModLocalRuntime();
    public abstract DependencyCollector getModImplementation();
    public abstract DependencyCollector getModApi();
}
