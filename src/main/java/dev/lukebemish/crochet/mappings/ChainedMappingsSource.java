package dev.lukebemish.crochet.mappings;

import net.neoforged.srgutils.IMappingBuilder;
import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;

public abstract class ChainedMappingsSource extends MappingsSource {
    @Nested
    public abstract ListProperty<MappingsSource> getInputMappings();

    @Inject
    public ChainedMappingsSource() {}

    @Override
    public IMappingFile makeMappings() {
        return getInputMappings().get().stream()
            .map(MappingsSource::makeMappings)
            .reduce(IMappingFile::chain).orElse(IMappingBuilder.create("source", "target").build().getMap("source", "target"));
    }
}
