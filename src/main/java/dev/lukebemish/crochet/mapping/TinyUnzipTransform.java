package dev.lukebemish.crochet.mapping;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

@CacheableTransform
public abstract class TinyUnzipTransform implements TransformAction<TransformParameters.None> {
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(@NotNull TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        if (!input.exists()) {
            // Can't do anything before the configuration's dependencies have been resolved.
            return;
        }
        try {
            var output = outputs.file(input.getName().substring(0, input.getName().lastIndexOf('.')) + ".tiny");
            URI uri = URI.create("jar:" + input.toURI());
            try (FileSystem fs = FileSystems.newFileSystem(uri, ImmutableMap.of("create", "true"))) {
                Path nf = fs.getPath("mappings/mappings.tiny");
                if (!Files.exists(nf)) {
                    throw new RuntimeException("No tiny mappings found in jar");
                }
                Files.copy(nf, output.toPath());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
