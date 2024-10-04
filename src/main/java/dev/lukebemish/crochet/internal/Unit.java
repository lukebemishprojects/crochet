package dev.lukebemish.crochet.internal;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

public final class Unit {
    public static final Unit UNIT = new Unit();

    private Unit() {}

    public static Provider<Unit> provider(Project project) {
        return project.provider(() -> UNIT);
    }
}
