package dev.lukebemish.crochet.mappings;

import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;
import java.io.IOException;

public abstract class FileMappingSource extends MappingsSource {
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getMappingsFile();

    @Inject
    public FileMappingSource() {}

    @Override
    public IMappingFile makeMappings() {
        try {
            return IMappingFile.load(getMappingsFile().getSingleFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
