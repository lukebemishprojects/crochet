package dev.lukebemish.crochet.internal;

import org.gradle.api.Project;

import javax.inject.Inject;

public abstract class ProjectHolder {
    final Project project;

    @Inject
    public ProjectHolder(Project project) {
        this.project = project;
    }
}
