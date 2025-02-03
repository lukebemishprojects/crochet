package dev.lukebemish.crochet.model;

import org.gradle.api.Action;
import org.gradle.api.Project;

import javax.inject.Inject;

public abstract class VanillaInstallation extends AbstractVanillaInstallation {
    private final VanillaInstallationLogic logic;

    @Inject
    public VanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        logic = new VanillaInstallationLogic(this) {};
    }

    @Override
    protected VanillaInstallationDependencies makeDependencies(Project project) {
        return project.getObjects().newInstance(VanillaInstallationDependencies.class, this);
    }

    @Override
    public VanillaInstallationDependencies getDependencies() {
        return (VanillaInstallationDependencies) super.getDependencies();
    }

    public void dependencies(Action<? super VanillaInstallationDependencies> action) {
        action.execute(getDependencies());
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
