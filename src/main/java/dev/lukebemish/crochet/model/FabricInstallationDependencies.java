package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.MappingsConfigurationCounter;
import dev.lukebemish.crochet.mappings.FileMappingsStructure;
import dev.lukebemish.crochet.mappings.MappingsStructure;
import org.gradle.api.artifacts.dsl.DependencyCollector;

import javax.inject.Inject;

@SuppressWarnings("UnstableApiUsage")
public abstract class FabricInstallationDependencies extends AbstractVanillaInstallationDependencies implements Mappings {
    @Inject
    public FabricInstallationDependencies(MinecraftInstallation installation) {
        super(installation);
    }

    public abstract DependencyCollector getLoader();

    public abstract DependencyCollector getIntermediary();

    public abstract DependencyCollector getAccessWideners();

    public MappingsStructure intermediary() {
        var configuration = getProject().getExtensions().getByType(MappingsConfigurationCounter.class).newConfiguration();
        configuration.fromDependencyCollector(getIntermediary());
        var source = getObjectFactory().newInstance(FileMappingsStructure.class);
        source.getMappingsFile().from(configuration);
        return source;
    }
}
