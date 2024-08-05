package dev.lukebemish.crochet.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class GenerateArgFiles extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getArgFile();

    @OutputFile
    public abstract RegularFileProperty getJvmArgFile();

    @Input
    public abstract ListProperty<String> getJvmArgs();

    @Input
    public abstract ListProperty<String> getArgs();

    @Input
    public abstract Property<String> getMainClass();

    @TaskAction
    public void generate() {
        getArgFile().get().getAsFile().getParentFile().mkdirs();
        try (var argsWriter = new OutputStreamWriter(new FileOutputStream(getArgFile().get().getAsFile()));
             var jvmArgsWriter = new OutputStreamWriter(new FileOutputStream(getJvmArgFile().get().getAsFile()))) {
            List<String> argsLInes = new ArrayList<>();
            argsLInes.add(escapeArgument(getMainClass().get()));
            for (var arg : getArgs().get()) {
                argsLInes.add(escapeArgument(arg));
            }
            argsWriter.write(String.join("\n", argsLInes));

            List<String> jvmArgsLines = new ArrayList<>();
            for (var jvmArg : getJvmArgs().get()) {
                jvmArgsLines.add(escapeArgument(jvmArg));
            }
            jvmArgsWriter.write(String.join("\n", jvmArgsLines));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write arg file", e);
        }
    }

    private static String escapeArgument(String argument) {
        argument = argument.replace("\\", "\\\\");
        argument = argument.replace("\"", "\\\"");
        if (argument.contains(" ")) {
            argument = "\"" + argument + "\"";
        }
        return argument;
    }
}
