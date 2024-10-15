package dev.lukebemish.crochet.internal;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;

public final class ConfigurationUtils {
    private ConfigurationUtils() {}

    @SuppressWarnings({"rawtypes", "DataFlowIssue", "unchecked"})
    public static void copyAttributes(AttributeContainer source, AttributeContainer destination) {
        source.keySet().forEach(key -> destination.attribute((Attribute) key, source.getAttribute(key)));
    }
}
