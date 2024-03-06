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
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class RemapParameters implements Serializable {
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract ListProperty<String> getExtraArguments();

    @ApiStatus.Internal
    public void execute(ExecOperations execOperations, Path output, Path input, FileCollection mappingClasspath, FileCollection mappings, Path argsFile) {
        Path mappingsPath = mappings.getSingleFile().toPath();

        try {
            Files.createDirectories(output.getParent());
            List<String> args = new ArrayList<>();
            args.add("--mappings");
            args.add(mappingsPath.toString());
            args.add("--output");
            args.add(output.toString());
            args.add("--input");
            args.add(input.toString());
            args.add("--classpath");
            args.addAll(mappingClasspath.getFiles().stream().map(File::getPath).toList());
            args.addAll(getExtraArguments().get());
            Files.writeString(argsFile,
                String.join("\n", args)
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var result = execOperations.javaexec(spec -> {
            spec.classpath(getClasspath());
            spec.getMainClass().set(getMainClass().get());
            spec.args(argsFile.toString());
        });

        try {
            Files.delete(argsFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        result.rethrowFailure();
        result.assertNormalExitValue();
    }
}
