package dev.lukebemish.crochet.model;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;

import javax.inject.Inject;

public abstract class CrochetTasksContext {
    @Inject public CrochetTasksContext() {}

    @Inject
    protected abstract Project getProject();

    public void maybeNamed(String name, Action<? super Task> action) {
        getProject().getTasks().configureEach(t -> {
            if (t.getName().equals(name)) {
                action.execute(t);
            }
        });
    }

    public <T extends Task> void maybeNamed(String name, Class<T> type, Action<? super T> action) {
        getProject().getTasks().withType(type, t -> {
            if (t.getName().equals(name)) {
                action.execute(t);
            }
        });
    }
}
