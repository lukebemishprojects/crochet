package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.mappings.MappingsStructure;
import org.gradle.api.artifacts.dsl.DependencyCollector;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;

public abstract class AbstractVanillaInstallationDependencies extends AbstractInstallationDependencies implements Mappings {
    @Inject
    public AbstractVanillaInstallationDependencies(MinecraftInstallation installation) {
        super(installation);
    }

    @SuppressWarnings("UnstableApiUsage")
    public abstract DependencyCollector getMinecraftDependencies();

    public abstract Property<MappingsStructure> getMappings();

    public void mappings(MappingsStructure mappings) {
        getMappings().set(mappings);
    }

    public void mappings(Provider<MappingsStructure> mappings) {
        getMappings().set(mappings);
    }
}
