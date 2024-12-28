package dev.lukebemish.crochet.internal;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;

public abstract class InheritanceMarker {
    @Inject public InheritanceMarker() {}

    public abstract DomainObjectSet<String> getInheritsFrom();
    public abstract DomainObjectSet<String> getInheritedBy();
    public abstract MapProperty<String, String> getSourceToManifestNameMap();

    public abstract DomainObjectSet<String> getShouldTakeConfigurationsFrom();
    public abstract DomainObjectSet<String> getShouldGiveConfigurationsTo();

    public static InheritanceMarker getOrCreate(ObjectFactory objectFactory, SourceSet sourceSet) {
        var found = sourceSet.getExtensions().findByType(InheritanceMarker.class);
        if (found != null) {
            return found;
        }
        var instance = objectFactory.newInstance(InheritanceMarker.class);
        sourceSet.getExtensions().add(InheritanceMarker.class, "crochet-sourceSetInheritanceMarker", instance);
        return instance;
    }
}
