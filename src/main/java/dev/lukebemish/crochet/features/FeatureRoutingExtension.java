package dev.lukebemish.crochet.features;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSetContainer;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.List;

public abstract class FeatureRoutingExtension {
    @Inject
    protected abstract Project getProject();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    public abstract NamedDomainObjectSet<RegisteredFeature> getFeatures();

    @Inject
    public FeatureRoutingExtension() {
        var sourceSets = getProject().getExtensions().getByType(SourceSetContainer.class);
        sourceSets.all(sourceSet -> {
            var feature = getObjects().newInstance(RegisteredFeature.class, sourceSet);
            getFeatures().add(feature);
        });
    }

    public <T extends Configuration> FeatureElement<T> registerElement(List<FeatureElement<?>> parents, @Nullable String prefix, String name, ElementScope<T> scope, Action<? super RegisteredFeature> action) {
        var element = new DependentFeatureElement<>(parents, prefix, name, scope, action);
        addElement(element);
        return element;
    }

    <T extends Configuration> void addElement(DependentFeatureElement<T> element) {
        getFeatures().configureEach(feature -> {
            boolean hasParents = true;
            try {
                for (var parent : element.parents()) {
                    feature.element(parent);
                }
            } catch (UnknownDomainObjectException ignored) {
                hasParents = false;
            }
            if (hasParents) {
                // Parents all exist (potentially lazily), so we can register immediately
                element.registerDirect(feature);
            } else {
                // Lazy, wackier approach using a Rule
                element.register(getConfigurations(), feature);
            }
        });
    }
}
