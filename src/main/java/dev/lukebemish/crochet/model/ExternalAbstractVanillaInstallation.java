package dev.lukebemish.crochet.model;

import javax.inject.Inject;

public abstract class ExternalAbstractVanillaInstallation extends ExternalMinecraftInstallation {
    @Inject
    public ExternalAbstractVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);
    }
}
