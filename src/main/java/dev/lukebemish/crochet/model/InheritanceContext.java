package dev.lukebemish.crochet.model;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class InheritanceContext {
    @Inject public InheritanceContext() {
        this.getLinkConfigurations().convention(getMultiloader().map(it -> !it));
        this.getRecompile().convention(getMultiloader());
        this.getCompileLanguages().add("java");
        this.getMultiloader().convention(false);
    }

    public abstract Property<Boolean> getLinkConfigurations();
    public abstract Property<Boolean> getRecompile();

    public abstract Property<Boolean> getMultiloader();

    public abstract ListProperty<String> getCompileLanguages();

    /**
     * Ignored with recompile.
     */
    public abstract Property<String> getMarkAdditionalString();
}
