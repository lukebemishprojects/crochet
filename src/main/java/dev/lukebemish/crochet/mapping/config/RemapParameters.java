package dev.lukebemish.crochet.mapping.config;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class RemapParameters implements Serializable {
    @CompileClasspath
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract ListProperty<String> getExtraArguments();

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFiles
    public abstract ConfigurableFileCollection getMappings();

    public void from(RemapParameters other) {
        getClasspath().from(other.getClasspath());
        getMainClass().set(other.getMainClass());
        getExtraArguments().set(other.getExtraArguments());
        getMappings().from(other.getMappings());
    }

    @ApiStatus.Internal
    public void execute(ExecOperations execOperations, Path output, Path input, Path tmpDir, List<Path> classpath) {
        Path mappingsPath = getMappings().getSingleFile().toPath();
        Path argsFile = tmpDir.resolve("remap.args");

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
            args.addAll(classpath.stream().map(Path::toString).toList());
            args.addAll(getExtraArguments().get());
            args.add("--tmpdir");
            args.add(tmpDir.toString());
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
