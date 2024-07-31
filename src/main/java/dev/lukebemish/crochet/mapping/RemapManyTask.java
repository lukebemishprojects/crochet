package dev.lukebemish.crochet.mapping;

import org.apache.commons.io.FileUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public abstract class RemapManyTask extends AbstractRemapTask {
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFiles
    public abstract ConfigurableFileCollection getInputArtifacts();

    @OutputDirectory
    public abstract DirectoryProperty getOutputArtifact();

    @Inject
    public RemapManyTask() {
        getRemapParameters().getClasspath().from(getInputArtifacts());
    }

    @TaskAction
    void remap() {
        try {
            FileUtils.deleteDirectory(getOutputArtifact().get().getAsFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int index = 0;
        for (File input : getInputArtifacts().getFiles()) {
            Path outputPath = outputFor(input, index).toPath();
            remap(input.toPath(), outputPath);
            index++;
        }
    }

    File outputFor(File input, int index) {
        String name = input.getName();
        String outputName = "crochet-"+index+"-"+name;
        return getOutputArtifact().file(outputName).get().getAsFile();
    }
}
