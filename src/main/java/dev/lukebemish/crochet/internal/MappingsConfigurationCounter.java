package dev.lukebemish.crochet.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MappingsConfigurationCounter {
    private final AtomicInteger counter = new AtomicInteger();

    @Inject
    protected abstract Project getProject();

    @Inject
    public MappingsConfigurationCounter() {}

    public Configurations newConfiguration() {
        var dependencies = ConfigurationUtils.dependencyScopeInternal(getProject(), String.valueOf(counter.getAndIncrement()), "counterMappings", c -> {});
        var classpath = ConfigurationUtils.resolvableInternal(getProject(), String.valueOf(counter.getAndIncrement()), "counterMappingsClasspath", c -> {
            c.extendsFrom(dependencies);
        });
        return new Configurations(classpath, dependencies);
    }

    @SuppressWarnings("UnstableApiUsage")
    public record Configurations(ResolvableConfiguration classpath, DependencyScopeConfiguration dependencies) {}
}
