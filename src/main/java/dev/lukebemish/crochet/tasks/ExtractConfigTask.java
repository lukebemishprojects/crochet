package dev.lukebemish.crochet.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.zip.ZipFile;

public abstract class ExtractConfigTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getNeoForm();

    @OutputFile
    public abstract RegularFileProperty getNeoFormConfig();

    @TaskAction
    public void extract() {
        var file = getNeoForm().getSingleFile();
        try (var zip = new ZipFile(file)) {
            var entry = zip.getEntry("config.json");
            if (entry == null) {
                throw new IllegalArgumentException("config.json not found in " + file);
            }
            try (var reader = new InputStreamReader(zip.getInputStream(entry));
                 var writer = new OutputStreamWriter(new FileOutputStream(getNeoFormConfig().get().getAsFile()))) {
                reader.transferTo(writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
