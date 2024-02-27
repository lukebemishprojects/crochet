package dev.lukebemish.crochet.mapping;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public abstract class RemapTransform implements TransformAction<RemapTransform.Parameters> {

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(@NotNull TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        String mappings = getParameters().getMappings().getFiles().stream().toList().toString();
        String mappingClasspath = getParameters().getMappingClasspath().getFiles().stream().toList().toString();
        var fileName = "mapped@" + input.getName();
        try {
            if (input.isFile()) {
                var output = outputs.file(fileName);
                FileUtils.copyFile(input, output);
                URI uri = URI.create("jar:" + output.toURI());
                try (FileSystem fs = FileSystems.newFileSystem(uri, ImmutableMap.of("create", "true"))) {
                    Path nf = fs.getPath("mapped.txt");
                    try (Writer writer = Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                        writer.write(mappings + "\n" + mappingClasspath + "\n");
                    }
                }
            } else if (input.isDirectory()) {
                var output = outputs.dir(fileName);
                FileUtils.copyDirectory(input, output);
                Path nf = output.toPath().resolve("mapped.txt");
                try (Writer writer = Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                    writer.write(mappings + "\n" + mappingClasspath + "\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract static class Parameters implements TransformParameters {
        @PathSensitive(PathSensitivity.NONE)
        @InputFiles
        public abstract ConfigurableFileCollection getMappings();

        @PathSensitive(PathSensitivity.NONE)
        @InputFiles
        public abstract ConfigurableFileCollection getMappingClasspath();
    }
}
