package dev.lukebemish.crochet.mappings;

import net.neoforged.srgutils.IMappingBuilder;
import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Nested;

public abstract class MergedMappingsSource implements MappingsSource {
    @Nested
    public abstract ListProperty<MappingsSource> getInputSources();

    @Override
    public IMappingFile getMappings() {
        return getInputSources().get().stream()
            .map(MappingsSource::getMappings)
            .reduce(IMappingFile::merge).orElse(IMappingBuilder.create("source", "target").build().getMap("source", "target"));
    }
}
