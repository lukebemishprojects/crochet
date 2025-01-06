package dev.lukebemish.crochet.model;

import javax.inject.Inject;

public abstract class AbstractExternalVanillaInstallation extends ExternalMinecraftInstallation {
    @Inject
    public AbstractExternalVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);
    }
}
