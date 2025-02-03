package dev.lukebemish.crochet.internal;

import org.gradle.api.problems.ProblemGroup;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public class SimpleProblemGroup implements ProblemGroup {
    public static final ProblemGroup CONFIGURATION_VALIDATION = new SimpleProblemGroup("crochet-configuration-validation", "Crochet Configuration Validation", null);

    private final String name;
    private final String displayName;
    private final @Nullable ProblemGroup parent;

    public SimpleProblemGroup(String name, String displayName, @Nullable ProblemGroup parent) {
        this.name = name;
        this.displayName = displayName;
        this.parent = parent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public @Nullable ProblemGroup getParent() {
        return parent;
    }
}
