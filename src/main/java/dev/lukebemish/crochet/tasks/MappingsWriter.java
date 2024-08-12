package dev.lukebemish.crochet.tasks;

import dev.lukebemish.crochet.mappings.MappingsSource;
import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

public abstract class MappingsWriter extends DefaultTask {
    @Nested
    public abstract Property<MappingsSource> getInputMappings();

    @OutputFile
    public abstract RegularFileProperty getOutputMappings();

    @Input
    public abstract Property<IMappingFile.Format> getTargetFormat();

    @TaskAction
    void execute() throws IOException {
        IMappingFile mappings = getInputMappings().get().getMappings();
        mappings.write(getOutputMappings().get().getAsFile().toPath(), getTargetFormat().get(), false);
    }
}
