package dev.lukebemish.crochet.features;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.tasks.SourceSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public interface FeatureElement<T extends Configuration> {
    String getConfigurationName(SourceSet sourceSet);
    Class<T> getType();

    FeatureElement<Configuration> COMPILE_CLASSPATH = new DefaultFeatureElement(SourceSet::getCompileClasspathConfigurationName);
    FeatureElement<Configuration> RUNTIME_CLASSPATH = new DefaultFeatureElement(SourceSet::getRuntimeClasspathConfigurationName);
    FeatureElement<Configuration> ANNOTATION_PROCESSOR = new DefaultFeatureElement(SourceSet::getAnnotationProcessorConfigurationName);

    FeatureElement<Configuration> COMPILE_ONLY = new DefaultFeatureElement(SourceSet::getCompileOnlyConfigurationName);
    FeatureElement<Configuration> RUNTIME_ONLY = new DefaultFeatureElement(SourceSet::getRuntimeOnlyConfigurationName);
    FeatureElement<Configuration> IMPLEMENTATION = new DefaultFeatureElement(SourceSet::getImplementationConfigurationName);
    FeatureElement<Configuration> API = new DefaultFeatureElement(SourceSet::getApiConfigurationName);
    FeatureElement<Configuration> COMPILE_ONLY_API = new DefaultFeatureElement(SourceSet::getCompileOnlyApiConfigurationName);

    FeatureElement<Configuration> API_ELEMENTS = new DefaultFeatureElement(SourceSet::getApiElementsConfigurationName);
    FeatureElement<Configuration> RUNTIME_ELEMENTS = new DefaultFeatureElement(SourceSet::getRuntimeElementsConfigurationName);

    FeatureElement<Configuration> SOURCES_ELEMENTS = new DefaultFeatureElement(SourceSet::getSourcesElementsConfigurationName);
    FeatureElement<Configuration> JAVADOC_ELEMENTS = new DefaultFeatureElement(SourceSet::getJavadocElementsConfigurationName);

    FeatureElement<DependencyScopeConfiguration> LOCAL_RUNTIME = new DependentFeatureElement<>(List.of(), null, "localRuntime", ElementScope.DEPENDENCY_SCOPE, feature -> {});
    FeatureElement<DependencyScopeConfiguration> LOCAL_IMPLEMENTATION = new DependentFeatureElement<>(List.of(), null, "localImplementation", ElementScope.DEPENDENCY_SCOPE, feature -> {});
}

record DefaultFeatureElement(Function<SourceSet, String> namer) implements FeatureElement<Configuration> {
    @Override
    public String getConfigurationName(SourceSet sourceSet) {
        return namer.apply(sourceSet);
    }

    @Override
    public Class<Configuration> getType() {
        return Configuration.class;
    }
}

record DependentFeatureElement<T extends Configuration>(List<FeatureElement<?>> parents, @Nullable String prefix, String name, ElementScope<T> scope, Action<? super RegisteredFeature> action) implements FeatureElement<T> {
    @Override
    public String getConfigurationName(SourceSet sourceSet) {
        return sourceSet.getTaskName(prefix, name);
    }

    @Override
    public Class<T> getType() {
        return scope.getType();
    }

    void register(ConfigurationContainer configurations, RegisteredFeature feature) {
        // Yes, this is awful and not all that stable
        // No, Gradle doesn't give us a better API. No "withNamed" or the like on named domain containers...
        var name = getConfigurationName(feature.getSourceSet());
        configurations.addRule("crochet features: register "+name, it -> {
            if (name.equals(it)) {
                for (var parent : parents) {
                    feature.element(parent);
                }
                // De-lazy this due to rule weirdness -- direct `getByName()` triggering the rule will fail otherwise
                createConfiguration(configurations, name).get();
                action.execute(feature);
            }
        });
    }

    void registerDirect(RegisteredFeature feature) {
        var name = getConfigurationName(feature.getSourceSet());
        for (var parent : parents) {
            feature.element(parent);
        }
        createConfiguration(feature.getConfigurations(), name);
        action.execute(feature);
    }

    @SuppressWarnings("UnstableApiUsage")
    private NamedDomainObjectProvider<? extends Configuration> createConfiguration(ConfigurationContainer configurations, String name) {
        return switch (scope) {
            case ElementScope.Consumable ignored -> configurations.consumable(name);
            case ElementScope.DependencyScope ignored -> configurations.dependencyScope(name);
            case ElementScope.Resolvable ignored -> configurations.resolvable(name);
        };
    }
}
