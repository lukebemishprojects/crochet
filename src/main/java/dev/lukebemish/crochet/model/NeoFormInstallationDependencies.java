package dev.lukebemish.crochet.model;

import org.gradle.api.artifacts.dsl.DependencyCollector;

import javax.inject.Inject;

public abstract class NeoFormInstallationDependencies extends AbstractInstallationDependencies {
    @Inject
    public NeoFormInstallationDependencies(MinecraftInstallation installation) {
        super(installation);
    }

    public abstract DependencyCollector getNeoForm();

    public abstract DependencyCollector getParchment();
}
