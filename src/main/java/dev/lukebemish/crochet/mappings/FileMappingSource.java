package dev.lukebemish.crochet.mappings;

import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.IOException;

public abstract class FileMappingSource implements MappingsSource {
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getMappingsFile();

    @Override
    public IMappingFile getMappings() {
        try {
            return IMappingFile.load(getMappingsFile().get().getAsFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
