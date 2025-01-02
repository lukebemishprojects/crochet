package dev.lukebemish.crochet.internal;

import dev.lukebemish.crochet.internal.metadata.pistonmeta.PistonMetaMetadataRule;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.provider.ProviderFactory;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.stream.Stream;

public final class ConfigurationUtils {
    private ConfigurationUtils() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void copyAttributes(AttributeContainer source, AttributeContainer destination, ProviderFactory providerFactory) {
        source.keySet().forEach(key -> {
            destination.attributeProvider((Attribute) key, providerFactory.provider(() -> source.getAttribute(key)));
        });
    }

    public static String extractMinecraftVersion(ResolvedComponentResult component) {
        var dependencies = component.getDependencies();
        String candidate = null;
        var queue = new ArrayDeque<DependencyResult>(dependencies);
        var selectors = new HashSet<ComponentSelector>();
        while (!queue.isEmpty()) {
            var dependency = queue.poll();
            if (selectors.add(dependency.getRequested())) {
                if (dependency instanceof ResolvedDependencyResult resolvedDependencyResult) {
                    queue.addAll(resolvedDependencyResult.getSelected().getDependencies());
                    var capabilities = resolvedDependencyResult.getResolvedVariant().getCapabilities();
                    var result = capabilities.stream().flatMap(capability -> {
                        var id = capability.getGroup() + ":" + capability.getName();
                        if ((id.equals(CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP + ":" + PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES)) ||
                            (id.equals("net.neoforged:minecraft-dependencies"))) {
                            return Stream.of(capability);
                        }
                        return Stream.of();
                    }).toList();
                    if (result.size() > 1) {
                        throw new IllegalStateException("Expected exactly one capability, got "+result.size()+": "+result);
                    } else if (!result.isEmpty()) {
                        var capability = result.getFirst();
                        if (candidate != null) {
                            throw new IllegalStateException("Expected exactly one candidate, got "+candidate+" and "+capability);
                        }
                        candidate = capability.getVersion();
                    }
                }
            }
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not find minecraft-dependencies");
        }
        return candidate;
    }
}
