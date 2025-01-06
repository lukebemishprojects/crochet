package dev.lukebemish.crochet.model;

import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;

public abstract class ExternalAbstractVanillaInstallation extends ExternalMinecraftInstallation {

    @Inject
    public ExternalAbstractVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);
    }

    @Override
    public void forFeature(SourceSet sourceSet) {
        super.forFeature(sourceSet);
    }

    @Override
    public void forLocalFeature(SourceSet sourceSet) {
        super.forLocalFeature(sourceSet);
    }
}
