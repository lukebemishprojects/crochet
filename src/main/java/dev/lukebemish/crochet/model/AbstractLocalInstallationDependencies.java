package dev.lukebemish.crochet.model;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

import javax.inject.Inject;

@SuppressWarnings("UnstableApiUsage")
public abstract class AbstractLocalInstallationDependencies<T extends AbstractLocalInstallationDependencies<T>> implements Dependencies {
    final LocalMinecraftInstallation installation;

    @Inject
    public AbstractLocalInstallationDependencies(LocalMinecraftInstallation installation) {
        this.installation = installation;
    }

    @SuppressWarnings("unchecked")
    public T configure(Action<? super T> action) {
        action.execute((T) this);
        return (T) this;
    }

    public abstract DependencyCollector getAccessTransformers();

    public abstract DependencyCollector getAccessTransformersApi();

    public abstract DependencyCollector getInjectedInterfaces();

    public abstract DependencyCollector getInjectedInterfacesApi();

    public Dependency publishInjectedInterfaces(Object path, Action<ConfigurablePublishArtifact> action) {
        var dep = getDependencyFactory().create(getProject().files(path));
        getInjectedInterfaces().add(dep);
        getProject().getArtifacts().add(installation.injectedInterfacesElements.get().getName(), path, action);
        return dep;
    }

    public Dependency publishInjectedInterfaces(Object path) {
        return publishInjectedInterfaces(path, artifact -> {});
    }

    public Dependency publishAccessTransformers(Object path, Action<ConfigurablePublishArtifact> action) {
        var dep = getDependencyFactory().create(getProject().files(path));
        getAccessTransformers().add(dep);
        getProject().getArtifacts().add(installation.accessTransformersElements.get().getName(), path, action);
        return dep;
    }

    public Dependency publishAccessTransformers(Object path) {
        return publishAccessTransformers(path, artifact -> {});
    }
}
