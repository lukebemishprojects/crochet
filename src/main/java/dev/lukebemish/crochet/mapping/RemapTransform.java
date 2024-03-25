package dev.lukebemish.crochet.mapping;

import dev.lukebemish.crochet.mapping.config.RemapParameters;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;

@CacheableTransform
public abstract class RemapTransform implements TransformAction<RemapTransform.Parameters> {

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @CompileClasspath
    @InputArtifactDependencies
    public abstract FileCollection getDependencies();

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

        getParameters().getRemapParameters().get().execute(
            getExecOperations(),
            outputPath,
            input.toPath(),
            outputPath.getParent(),
            getDependencies().getFiles().stream().map(File::toPath).toList()
        );
    }

    public interface Parameters extends TransformParameters {
        @Nested
        Property<RemapParameters> getRemapParameters();
    }
}
