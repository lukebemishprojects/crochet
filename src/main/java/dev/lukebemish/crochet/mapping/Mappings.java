package dev.lukebemish.crochet.mapping;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

public interface Mappings extends Named {
    Attribute<Mappings> MAPPINGS_ATTRIBUTE = Attribute.of("dev.lukebemish.mappings", Mappings.class);

    String UNMAPPED = "unmapped";

    static String source(String mappings) {
        String[] parts = mappings.split("->");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Compound mappings must contain exactly one '->'");
        }
        return parts[0];
    }

    static String target(String mappings) {
        String[] parts = mappings.split("->");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Compound mappings must contain exactly one '->'");
        }
        return parts[1];
    }

    static boolean isCompound(String mappings) {
        return mappings.contains("->");
    }

    static String source(Mappings mappings) {
        return source(mappings.getName());
    }

    static String target(Mappings mappings) {
        return target(mappings.getName());
    }

    static boolean isCompound(Mappings mappings) {
        return isCompound(mappings.getName());
    }

    static String of(String source, String target) {
        if (source.contains("->") || target.contains("->")) {
            throw new IllegalArgumentException("Mappings must contain at most one '->'");
        }
        return source + "->" + target;
    }

    static String of(Mappings source, Mappings target) {
        return of(source.getName(), target.getName());
    }
}
