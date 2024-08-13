package dev.lukebemish.crochet.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

public abstract class RemapJarsTask extends DefaultTask {
    public abstract static class Target {
        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        public abstract RegularFileProperty getSource();

        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        public abstract RegularFileProperty getTarget();
    }

    @Nested
    public abstract ListProperty<Target> getTargets();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getTinyRemapperClasspath();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getRemappingClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getMappings();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    public RemapJarsTask() {}

    @TaskAction
    public void execute() {
        getExecOperations().javaexec(spec -> {
            spec.classpath(getTinyRemapperClasspath());
            spec.getMainClass().set("dev.lukebemish.crochet.wrappers.tinyremapper.Main");
            for (var target : getTargets().get()) {
                spec.args(
                    target.getSource().get().getAsFile().getAbsolutePath(),
                    target.getTarget().get().getAsFile().getAbsolutePath()
                );
            }
            spec.args("--mappings", getMappings().get().getAsFile().getAbsolutePath());
            spec.args("--classpath", getRemappingClasspath().getAsPath());
        });
    }

    public void forConfiguration(Configuration source, Directory destinationDirectory, ConfigurableFileCollection destinationFiles) {
        destinationFiles.builtBy(this);
    }
}
