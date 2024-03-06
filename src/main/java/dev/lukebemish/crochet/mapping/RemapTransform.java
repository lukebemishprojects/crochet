package dev.lukebemish.crochet.mapping;

import dev.lukebemish.crochet.mapping.config.RemapParameters;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

@CacheableTransform
public abstract class RemapTransform implements TransformAction<RemapTransform.Parameters> {

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public void transform(@NotNull TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        if (!input.exists()) {
            // We're trying to remap before the configuration's dependencies have been resolved. This can occur during
            // IntelliJ project import - as the file contents are used in the cache key, we should be able to safely
            // return here without producing an artifact.
            return;
        }
        var fileName = "mapped@" + input.getName();
        var outputPath = outputs.file(fileName).toPath();

        var argsFile = outputPath.getParent().resolve(fileName + ".args");

        getParameters().getRemapParameters().get().execute(
            getExecOperations(),
            outputPath,
            input.toPath(),
            getParameters().getMappingClasspath(),
            getParameters().getMappings(),
            argsFile
        );
    }

    public interface Parameters extends TransformParameters {
        @PathSensitive(PathSensitivity.NAME_ONLY)
        @InputFiles
        ConfigurableFileCollection getMappings();

        @Classpath
        @InputFiles
        ConfigurableFileCollection getMappingClasspath();

        @Nested
        Property<RemapParameters> getRemapParameters();
    }
}
