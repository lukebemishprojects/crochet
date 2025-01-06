package dev.lukebemish.crochet.model;

import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

@SuppressWarnings("UnstableApiUsage")
public interface FabricRemapDependencies extends Dependencies {
    DependencyCollector getModCompileOnly();
    DependencyCollector getModCompileOnlyApi();
    DependencyCollector getModRuntimeOnly();
    DependencyCollector getModLocalRuntime();
    DependencyCollector getModLocalImplementation();
    DependencyCollector getModImplementation();
    DependencyCollector getModApi();
}
