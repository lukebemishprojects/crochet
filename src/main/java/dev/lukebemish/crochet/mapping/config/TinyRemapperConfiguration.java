package dev.lukebemish.crochet.mapping.config;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.util.List;

public abstract class TinyRemapperConfiguration {
    public abstract Property<String> getSourceNamespace();
    public abstract Property<String> getTargetNamespace();
    public abstract Property<Boolean> getRemapLocalVariables();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @ApiStatus.Internal
    public Provider<List<String>> makeExtraArguments() {
        var property = getObjectFactory().listProperty(String.class);
        property.addAll(getSourceNamespace().map(s -> List.of("--source", s)).orElse(List.of()));
        property.addAll(getTargetNamespace().map(s -> List.of("--target", s)).orElse(List.of()));
        property.addAll(getRemapLocalVariables().map(b -> List.of("--remap-locals", b.toString())).orElse(List.of()));
        return property;
    }
}
