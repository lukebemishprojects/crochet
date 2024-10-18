package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.mappings.MappingsStructure;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class AbstractVanillaInstallationDependencies extends InstallationDependencies implements Mappings {
    @Inject
    public AbstractVanillaInstallationDependencies(MinecraftInstallation installation) {
        super(installation);
    }

    public abstract Property<MappingsStructure> getMappings();
}
