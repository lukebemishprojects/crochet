package dev.lukebemish.crochet.mapping;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public abstract class RemapTransformSpec implements TransformAction<RemapTransformSpec.Parameters> {

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(@NotNull TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        var mappings = getParameters().getMappings();
        String prefix = "";
        if (mappings instanceof MapSpec.Unmapped) prefix = "unmapped";
        else if (mappings instanceof MapSpec.Named named) prefix = named.start() + "->" + named.end();
        var fileName = prefix + "@" + input.getName();
        try {
            if (input.isFile()) {
                var output = outputs.file(fileName);
                FileUtils.copyFile(input, output);
                URI uri = URI.create("jar:" + output.toURI());
                try (FileSystem fs = FileSystems.newFileSystem(uri, ImmutableMap.of("create", "true"))) {
                    Path nf = fs.getPath("mapped.txt");
                    try (Writer writer = Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                        writer.write(prefix + "\n");
                    }
                }
            } else if (input.isDirectory()) {
                var output = outputs.dir(fileName);
                FileUtils.copyDirectory(input, output);
                Path nf = output.toPath().resolve("mapped.txt");
                try (Writer writer = Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                    writer.write(prefix + "\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public interface Parameters extends TransformParameters {
        @Input
        MapSpec getMappings();
        void setMappings(MapSpec mappings);
    }
}
