package dev.lukebemish.crochet.mappings;

import net.neoforged.srgutils.IMappingFile;

import javax.inject.Inject;
import javax.naming.spi.ObjectFactory;

public abstract class MappingsSource {
    public abstract IMappingFile makeMappings();

    @Inject
    public MappingsSource() {}

    @Inject
    protected abstract ObjectFactory getObjectFactory();
}
