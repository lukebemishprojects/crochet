package dev.lukebemish.crochet.mappings;

import net.neoforged.srgutils.IMappingBuilder;
import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Nested;

public abstract class ChainedMappingsSource implements MappingsSource {
    @Nested
    public abstract ListProperty<MappingsSource> getInputSources();

    @Override
    public IMappingFile makeMappings() {
        return getInputSources().get().stream()
            .map(MappingsSource::makeMappings)
            .reduce(IMappingFile::chain).orElse(IMappingBuilder.create("source", "target").build().getMap("source", "target"));
    }
}
