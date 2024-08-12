package dev.lukebemish.crochet.mappings;

import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

public abstract class ReversedMappingsSource implements MappingsSource {
    @Nested
    public abstract Property<MappingsSource> getInputMappings();

    @Override
    public IMappingFile getMappings() {
        return getInputMappings().get().getMappings().reverse();
    }
}
