package dev.lukebemish.crochet.internal.tasks;

import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

public abstract class MappingsWriter extends DefaultTask {
    @InputFiles
    public abstract ConfigurableFileCollection getInputMappings();

    @OutputFile
    public abstract RegularFileProperty getOutputMappings();

    @Input
    public abstract Property<IMappingFile.Format> getTargetFormat();

    @TaskAction
    void execute() throws IOException {
        IMappingFile mappings = IMappingFile.load(getInputMappings().getSingleFile());
        mappings.write(getOutputMappings().get().getAsFile().toPath(), getTargetFormat().get(), false);
    }
}
