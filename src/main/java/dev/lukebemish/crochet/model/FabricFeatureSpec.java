package dev.lukebemish.crochet.model;

import org.gradle.api.Action;
import org.gradle.api.plugins.FeatureSpec;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class FabricFeatureSpec {
    @Inject public FabricFeatureSpec() {}

    final List<Action<FeatureSpec>> featureActions = new ArrayList<>();
    final List<Action<FabricInstallation>> installationActions = new ArrayList<>();
    final List<Action<FabricSourceSetDependencies>> dependenciesActions = new ArrayList<>();

    public void featureSpec(Action<FeatureSpec> action) {
        featureActions.add(action);
    }

    public void installation(Action<FabricInstallation> action) {
        installationActions.add(action);
    }

    public void dependencies(Action<FabricSourceSetDependencies> action) {
        dependenciesActions.add(action);
    }
}
