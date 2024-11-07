package dev.lukebemish.crochet.model;

import javax.inject.Inject;

public abstract class VanillaInstallationDependencies extends AbstractVanillaInstallationDependencies {
    @Inject
    public VanillaInstallationDependencies(MinecraftInstallation installation) {
        super(installation);
    }
}
