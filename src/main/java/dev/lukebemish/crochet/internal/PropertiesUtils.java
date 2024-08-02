package dev.lukebemish.crochet.internal;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

public final class PropertiesUtils {
    private PropertiesUtils() {}

    @SuppressWarnings("UnstableApiUsage")
    public static Provider<Map<String, String>> networkProperties(ProviderFactory providerFactory) {
        List<Provider<Map<String, String>>> parts = new ArrayList<>();

        // See https://docs.oracle.com/en%2Fjava%2Fjavase%2F21%2Fdocs%2Fapi%2F%2F/java.base/java/net/doc-files/net-properties.html
        for (String property : List.of(
            "socksProxyHost",
            "socksProxyPort",
            "socksProxyVersion"
        )) {
            parts.add(providerFactory.systemProperty(property).map(value -> Map.of(property, value)).orElse(Map.of()));
        }
        for (String prefix : List.of(
            "http.",
            "https.",
            "java.net.",
            "ftp.",
            "jdk.https.",
            "networkaddress.",
            // Thanks to the ModDevGradle for noticing that these two are also needed, despite not being in the above docs page
            "javax.net.ssl.",
            "jdk.tls."
        )) {
            parts.add(providerFactory.systemPropertiesPrefixedBy(prefix));
        }

        return flatten(providerFactory, parts);
    }

    private static Provider<Map<String, String>> flatten(ProviderFactory providerFactory, List<Provider<Map<String, String>>> providers) {
        BinaryOperator<Provider<Map<String, String>>> operator = (a, b) -> a.zip(b, (aMap, bMap) -> {
            var combined = new HashMap<String, String>();
            combined.putAll(aMap);
            combined.putAll(bMap);
            return combined;
        });
        return providers.stream().reduce(operator).orElse(providerFactory.provider(Map::of));
    }
}
