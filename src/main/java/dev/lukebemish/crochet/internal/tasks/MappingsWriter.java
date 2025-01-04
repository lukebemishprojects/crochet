package dev.lukebemish.crochet.internal.tasks;

import net.neoforged.srgutils.IMappingFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;

@DisableCachingByDefault(because = "Not worth caching")
public abstract class MappingsWriter extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
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
