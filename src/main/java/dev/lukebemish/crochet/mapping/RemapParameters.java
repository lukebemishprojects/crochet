package dev.lukebemish.crochet.mapping;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

public abstract class RemapParameters {
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    private final ExecOperations operations;

    @Inject
    public RemapParameters(ExecOperations operations) {
        this.operations = operations;
    }
}
