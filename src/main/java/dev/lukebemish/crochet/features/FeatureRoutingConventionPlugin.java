package dev.lukebemish.crochet.features;

import dev.lukebemish.crochet.CrochetSharedProjectExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyScopeConfiguration;

public abstract class FeatureRoutingConventionPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(FeatureRoutingPlugin.class);
        var extension = project.getExtensions().getByType(CrochetSharedProjectExtension.class).getExtensions().getByType(FeatureRoutingExtension.class);
        extension.addElement((DependentFeatureElement<DependencyScopeConfiguration>) FeatureElement.LOCAL_RUNTIME);
        extension.addElement((DependentFeatureElement<DependencyScopeConfiguration>) FeatureElement.LOCAL_IMPLEMENTATION);
        extension.getFeatures().configureEach(feature -> {
            feature.maybeConfigureElement(FeatureElement.RUNTIME_CLASSPATH, config -> {
                config.extendsFrom(feature.element(FeatureElement.LOCAL_RUNTIME));
                config.extendsFrom(feature.element(FeatureElement.LOCAL_IMPLEMENTATION));
            });
            feature.maybeConfigureElement(FeatureElement.COMPILE_CLASSPATH, config -> {
                config.extendsFrom(feature.element(FeatureElement.LOCAL_IMPLEMENTATION));
            });
        });
    }
}
