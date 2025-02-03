package dev.lukebemish.crochet.model;

import javax.inject.Inject;

public abstract class ExternalVanillaInstallation extends ExternalAbstractVanillaInstallation {
    private final VanillaInstallationLogic logic;

    @Inject
    public ExternalVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);
        logic = new VanillaInstallationLogic(this) {};
    }

    @Override
    void forRun(Run run, RunType runType) {
        super.forRun(run, runType);
        logic.forRun(run, runType);
    }

    @Override
    protected String sharingInstallationTypeTag() {
        return "vanilla";
    }
}
