package dev.lukebemish.crochet.model;

import javax.inject.Inject;

public abstract class ExternalVanillaInstallation extends AbstractExternalVanillaInstallation {
    private final VanillaInstallationRunLogic runLogic;

    @Inject
    public ExternalVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);
        runLogic = new VanillaInstallationRunLogic(this) {};
    }

    @Override
    void forRun(Run run, RunType runType) {
        super.forRun(run, runType);
        runLogic.forRun(run, runType);
    }

    @Override
    protected String sharingInstallationTypeTag() {
        return "vanilla";
    }
}
