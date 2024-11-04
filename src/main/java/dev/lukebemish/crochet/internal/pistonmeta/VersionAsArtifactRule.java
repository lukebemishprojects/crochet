package dev.lukebemish.crochet.internal.pistonmeta;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;

import javax.inject.Inject;

public abstract class VersionAsArtifactRule implements ComponentMetadataRule {
    @Inject
    public VersionAsArtifactRule() {}

    @Override
    public void execute(ComponentMetadataContext context) {
        var version = context.getDetails().getId().getVersion();
        var extension = version.substring(version.lastIndexOf('.') + 1);
        context.getDetails().setChanging(false);
        context.getDetails().allVariants(variant -> {
            variant.attributes(attributeContainer -> {
                attributeContainer.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, extension);
            });
        });
    }
}
