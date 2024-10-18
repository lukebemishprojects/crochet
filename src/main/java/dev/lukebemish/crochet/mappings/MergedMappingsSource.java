package dev.lukebemish.crochet.mappings;

import net.neoforged.srgutils.IMappingBuilder;
import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;

public abstract class MergedMappingsSource extends MappingsSource {
    @Nested
    public abstract ListProperty<MappingsSource> getInputMappings();

    @Override
    public IMappingFile makeMappings() {
        return getInputMappings().get().stream()
            .map(MappingsSource::makeMappings)
            .reduce(IMappingFile::merge).orElse(IMappingBuilder.create("source", "target").build().getMap("source", "target"));
    }

    @Inject
    public MergedMappingsSource() {}
}
