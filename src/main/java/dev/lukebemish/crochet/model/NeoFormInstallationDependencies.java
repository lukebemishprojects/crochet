package dev.lukebemish.crochet.model;

import org.gradle.api.artifacts.dsl.DependencyCollector;

import javax.inject.Inject;

public abstract class NeoFormInstallationDependencies extends AbstractLocalInstallationDependencies<NeoFormInstallationDependencies> {
    @Inject
    public NeoFormInstallationDependencies(NeoFormInstallation installation) {
        super(installation);
    }

    @SuppressWarnings("UnstableApiUsage")
    public abstract DependencyCollector getNeoForm();

    @SuppressWarnings("UnstableApiUsage")
    public abstract DependencyCollector getParchment();
}
