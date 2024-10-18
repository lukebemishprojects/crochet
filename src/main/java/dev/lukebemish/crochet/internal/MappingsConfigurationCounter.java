package dev.lukebemish.crochet.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MappingsConfigurationCounter {
    private final AtomicInteger counter = new AtomicInteger();

    @Inject
    protected abstract Project getProject();

    @Inject
    public MappingsConfigurationCounter() {}

    public Configuration newConfiguration() {
        return getProject().getConfigurations().create("crochetMappings" + counter.getAndIncrement());
    }
}
