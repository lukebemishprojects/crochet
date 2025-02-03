package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.MappingsConfigurationCounter;
import dev.lukebemish.crochet.model.mappings.ChainedMappingsStructure;
import dev.lukebemish.crochet.model.mappings.FileMappingsStructure;
import dev.lukebemish.crochet.model.mappings.MappingsStructure;
import dev.lukebemish.crochet.model.mappings.MergedMappingsStructure;
import dev.lukebemish.crochet.model.mappings.MojangOfficialMappingsStructure;
import dev.lukebemish.crochet.model.mappings.ReversedMappingsStructure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

@SuppressWarnings("UnstableApiUsage")
public interface Mappings extends Dependencies {
    default MappingsStructure artifact(Dependency dependency) {
        var configurations = getProject().getExtensions().getByType(MappingsConfigurationCounter.class).newConfiguration();
        configurations.dependencies().getDependencies().add(dependency);
        var source = getObjectFactory().newInstance(FileMappingsStructure.class);
        source.getMappingsFile().from(configurations.classpath());
        return source;
    }

    default MappingsStructure artifact(Provider<Dependency> dependencyProvider) {
        var configurations = getProject().getExtensions().getByType(MappingsConfigurationCounter.class).newConfiguration();
        configurations.dependencies().getDependencies().addLater(dependencyProvider);
        var source = getObjectFactory().newInstance(FileMappingsStructure.class);
        source.getMappingsFile().from(configurations.classpath());
        return source;
    }

    default MappingsStructure chained(Action<ListProperty<MappingsStructure>> action) {
        var chained = getObjectFactory().newInstance(ChainedMappingsStructure.class);
        action.execute(chained.getInputMappings());
        return chained;
    }

    default MappingsStructure merged(Action<ListProperty<MappingsStructure>> action) {
        var merged = getObjectFactory().newInstance(MergedMappingsStructure.class);
        action.execute(merged.getInputMappings());
        return merged;
    }

    default MappingsStructure reversed(Action<Property<MappingsStructure>> action) {
        var reversed = getObjectFactory().newInstance(ReversedMappingsStructure.class);
        action.execute(reversed.getInputMappings());
        return reversed;
    }

    default MappingsStructure official() {
        return MojangOfficialMappingsStructure.INSTANCE;
    }

    default MappingsStructure artifact(Object object) {
        return artifact(unpack(object));
    }

    private Provider<Dependency> unpack(Object object) {
        if (object instanceof Dependency dependency) {
            return getProject().provider(() -> dependency);
        } else if (object instanceof Provider<?> provider) {
            return getProject().provider(() -> unpack(provider.get()).get());
        } else {
            var dependency = getProject().getDependencies().create(object);
            return getProject().provider(() -> dependency);
        }
    }
}
