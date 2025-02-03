package dev.lukebemish.crochet.internal;

import org.jspecify.annotations.Nullable;

public sealed interface NameProvider {
    String name(String parent);

    record Named(@Nullable String prefix, @Nullable String suffix) implements NameProvider {
        @Override
        public String name(String parent) {
            return NameUtils.name(parent, prefix, suffix);
        }
    }

    record Internal(String name) implements NameProvider {
        @Override
        public String name(String parent) {
            return NameUtils.internal(parent, name);
        }
    }
}
