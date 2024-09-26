package dev.lukebemish.crochet.model;

import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

@SuppressWarnings("UnstableApiUsage")
public abstract class InstallationDependencies implements Dependencies {
    public abstract DependencyCollector getAccessTransformers();

    public abstract DependencyCollector getAccessTransformersApi();
}
