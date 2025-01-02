package dev.lukebemish.crochet.internal.metadata;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

import javax.inject.Inject;

public abstract class NeoMinecraftDependenciesRule implements ComponentMetadataRule {
    @Inject
    public NeoMinecraftDependenciesRule() {}

    @Override
    public void execute(ComponentMetadataContext context) {
        var details = context.getDetails();
        var id = details.getId();

        if (id.getGroup().equals("net.neoforged") && id.getName().equals("minecraft-dependencies")) {
            details.allVariants(variant -> {
                variant.withDependencies(metadata -> {
                    for (var dep : metadata) {
                        var strictly = dep.getVersionConstraint().getStrictVersion();
                        dep.version(v -> {
                            v.strictly("*");
                            v.require(strictly);
                        });
                    }
                });
            });
        }
    }
}
