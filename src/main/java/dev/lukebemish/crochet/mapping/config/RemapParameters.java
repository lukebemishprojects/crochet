package dev.lukebemish.crochet.mapping.config;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;

public abstract class RemapParameters implements Serializable {
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract ListProperty<String> getExtraArguments();

    @ApiStatus.Internal
    public void execute(ExecOperations execOperations, Path output, Path input, FileCollection mappingClasspath, FileCollection mappings) {
        Path mappingsPath = mappings.getSingleFile().toPath();

        var result = execOperations.javaexec(spec -> {
            spec.classpath(getClasspath());
            spec.getMainClass().set(getMainClass().get());
            spec.args("--mappings", mappingsPath.toString(), "--output", output.toString(), "--input", input.toString());
            spec.args("--classpath");
            spec.args(mappingClasspath.getFiles().stream().map(File::getPath).toList());
            spec.args(getExtraArguments().get());
        });

        result.rethrowFailure();
        result.assertNormalExitValue();
    }
}
