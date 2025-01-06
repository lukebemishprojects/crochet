package dev.lukebemish.crochet.model;

import org.gradle.api.Action;
import org.gradle.api.Named;

import javax.inject.Inject;

public abstract class DependencySet implements Named {
    private final Action<FabricRemapDependencies> action;
    private final String name;

    @Inject
    public DependencySet(String name, Action<FabricRemapDependencies> action) {
        this.action = action;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
