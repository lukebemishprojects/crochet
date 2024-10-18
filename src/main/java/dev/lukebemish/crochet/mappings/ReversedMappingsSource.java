package dev.lukebemish.crochet.mappings;

import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;

public abstract class ReversedMappingsSource extends MappingsSource {
    @Nested
    public abstract Property<MappingsSource> getInputMappings();

    @Override
    public IMappingFile makeMappings() {
        return getInputMappings().get().makeMappings().reverse();
    }

    @Inject
    public ReversedMappingsSource() {}
}
