package dev.lukebemish.crochet.internal.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.file.Files;

@DisableCachingByDefault(because = "Not worth caching")
public abstract class MakeRemapClasspathFile extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getRemapClasspathFile();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getRemapClasspath();

    @TaskAction
    public void execute() throws IOException {
        var path = getRemapClasspathFile().get().getAsFile().toPath();
        if (Files.exists(path)) {
            Files.delete(path);
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, getRemapClasspath().getAsPath());
    }
}
