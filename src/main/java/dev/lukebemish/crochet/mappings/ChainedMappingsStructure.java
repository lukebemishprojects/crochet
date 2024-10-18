package dev.lukebemish.crochet.mappings;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Nested;

public abstract non-sealed class ChainedMappingsStructure implements MappingsStructure {
    @Nested
    public abstract ListProperty<MappingsStructure> getInputMappings();
}
