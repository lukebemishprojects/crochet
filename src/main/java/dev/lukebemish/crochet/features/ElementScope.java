package dev.lukebemish.crochet.features;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;

public sealed interface ElementScope<T extends Configuration> {
    Class<T> getType();

    enum DependencyScope implements ElementScope<DependencyScopeConfiguration> {
        INSTANCE;

        @Override
        public Class<DependencyScopeConfiguration> getType() {
            return DependencyScopeConfiguration.class;
        }
    }
    enum Resolvable implements ElementScope<ResolvableConfiguration> {
        INSTANCE;

        @Override
        public Class<ResolvableConfiguration> getType() {
            return ResolvableConfiguration.class;
        }
    }
    enum Consumable implements ElementScope<ConsumableConfiguration> {
        INSTANCE;

        @Override
        public Class<ConsumableConfiguration> getType() {
            return ConsumableConfiguration.class;
        }
    }

    DependencyScope DEPENDENCY_SCOPE = DependencyScope.INSTANCE;
    Resolvable RESOLVABLE = Resolvable.INSTANCE;
    Consumable CONSUMABLE = Consumable.INSTANCE;
}
