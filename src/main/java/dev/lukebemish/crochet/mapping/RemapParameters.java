package dev.lukebemish.crochet.mapping;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.Serializable;

public abstract class RemapParameters implements Serializable {
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Inject
    protected abstract ExecOperations getExecOperations();
}
