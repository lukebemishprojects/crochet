package dev.lukebemish.crochet.model;

import org.gradle.api.Named;

import javax.inject.Inject;

public abstract class SharedInstallation implements Named {
    private final String name;

    @Inject
    public SharedInstallation(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
