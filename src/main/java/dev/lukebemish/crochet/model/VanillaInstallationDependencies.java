package dev.lukebemish.crochet.model;

import javax.inject.Inject;

public abstract class VanillaInstallationDependencies extends AbstractVanillaInstallationDependencies<VanillaInstallationDependencies> {
    @Inject
    public VanillaInstallationDependencies(VanillaInstallation installation) {
        super(installation);
    }
}
