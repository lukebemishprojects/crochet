package dev.lukebemish.crochet.mapping;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@CacheableTransform
public abstract class RemapTransform implements TransformAction<RemapTransform.Parameters> {

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(@NotNull TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        if (!input.exists()) {
            // We're trying to remap before the configuration's dependencies have been resolved. This can occur during
            // IntelliJ project import - as the file contents are used in the cache key, we should be able to safely
            // return here without producing an artifact.
            return;
        }
        String mappings = getParameters().getMappings().getFiles().stream().toList().toString();
        String mappingClasspath = getParameters().getMappingClasspath().getFiles().stream().toList().toString();
        var fileName = "mapped@" + input.getName();
        try {
            var output = outputs.file(fileName);
            FileUtils.copyFile(input, output);
            URI uri = URI.create("jar:" + output.toURI());
            try (FileSystem fs = FileSystems.newFileSystem(uri, ImmutableMap.of("create", "true"))) {
                Path nf = fs.getPath("mapped.txt");
                try (Writer writer = Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                    writer.write(mappings + "\n" + mappingClasspath + "\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract static class Parameters implements TransformParameters {
        @PathSensitive(PathSensitivity.NAME_ONLY)
        @InputFiles
        public abstract ConfigurableFileCollection getMappings();

        @Classpath
        @InputFiles
        public abstract ConfigurableFileCollection getMappingClasspath();

        @Nested
        public abstract Property<RemapParameters> getRemapParameters();
    }
}
