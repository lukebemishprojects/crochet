package dev.lukebemish.crochet.model;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.FeatureSpec;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;

public abstract class CrochetFeaturesContext {
    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract Project getProject();

    @Inject
    public CrochetFeaturesContext() {}

    public CrochetFeatureContext modify(String name, Action<? super CrochetFeatureContext> action) {
        var context = getObjectFactory().newInstance(CrochetFeatureContext.class, name);
        action.execute(context);
        return context;
    }

    public CrochetFeatureContext create(String name) {
        return create(name, spec -> {});
    }

    public CrochetFeatureContext create(String name, Action<? super FeatureSpec> action) {
        var sourceSet = getProject().getExtensions().getByType(SourceSetContainer.class).maybeCreate(name);
        var javaExtension = getProject().getExtensions().getByType(JavaPluginExtension.class);
        javaExtension.registerFeature(name, spec -> {
            spec.usingSourceSet(sourceSet);
            action.execute(spec);
        });
        return getObjectFactory().newInstance(CrochetFeatureContext.class, name);
    }
}
