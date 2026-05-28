package dev.lukebemish.crochet.features;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;

public abstract class RegisteredFeature implements Named {
    @Inject
    public RegisteredFeature(SourceSet sourceSet) {
        this.sourceSet = sourceSet;
    }

    @Override
    public String getName() {
        return sourceSet.getName();
    }

    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    private final SourceSet sourceSet;

    public SourceSet getSourceSet() {
        return this.sourceSet;
    }

    public <T extends Configuration> NamedDomainObjectProvider<T> element(FeatureElement<T> element) {
        return getConfigurations().named(element.getConfigurationName(getSourceSet()), element.getType());
    }

    public <T extends Configuration> void maybeConfigureElement(FeatureElement<T> element, Action<? super T> action) {
        getConfigurations().named(it -> it.equals(element.getConfigurationName(sourceSet))).withType(element.getType()).configureEach(action);
    }
}
