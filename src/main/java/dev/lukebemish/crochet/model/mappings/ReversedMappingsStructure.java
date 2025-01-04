package dev.lukebemish.crochet.model.mappings;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

public abstract non-sealed class ReversedMappingsStructure implements MappingsStructure {
    @Nested
    public abstract Property<MappingsStructure> getInputMappings();
}
