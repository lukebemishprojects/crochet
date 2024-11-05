package dev.lukebemish.crochet.internal;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.provider.ProviderFactory;

public final class ConfigurationUtils {
    private ConfigurationUtils() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void copyAttributes(AttributeContainer source, AttributeContainer destination, ProviderFactory providerFactory) {
        source.keySet().forEach(key -> {
            destination.attributeProvider((Attribute) key, providerFactory.provider(() -> source.getAttribute(key)));
        });
    }
}
