package dev.lukebemish.crochet.mapping;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

public abstract class RemapTask extends AbstractRemapTask {
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFile
    public abstract RegularFileProperty getInputArtifact();

    @OutputFile
    public abstract RegularFileProperty getOutputArtifact();

    @TaskAction
    void remap() {
        var outputPath = getOutputArtifact().get().getAsFile().toPath();
        var inputPath = getInputArtifact().get().getAsFile().toPath();

        remap(inputPath, outputPath);
    }
}
