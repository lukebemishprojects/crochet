package dev.lukebemish.crochet.mapping;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

public interface Mappings extends Named {
    Attribute<Mappings> MAPPINGS_ATTRIBUTE = Attribute.of("dev.lukebemish.mappings", Mappings.class);

    String UNMAPPED = "unmapped";
}
