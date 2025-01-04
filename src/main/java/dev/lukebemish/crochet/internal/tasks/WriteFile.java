package dev.lukebemish.crochet.internal.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

@DisableCachingByDefault(because = "Not worth caching")
public abstract class WriteFile extends DefaultTask {
    @Input
    public abstract Property<String> getContents();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void execute() {
        getOutputFile().get().getAsFile().getParentFile().mkdirs();
        try (var writer = Files.newBufferedWriter(getOutputFile().get().getAsFile().toPath())) {
            writer.write(getContents().get());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
