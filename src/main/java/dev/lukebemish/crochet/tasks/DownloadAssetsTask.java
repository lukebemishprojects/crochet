package dev.lukebemish.crochet.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public abstract class DownloadAssetsTask extends AbstractNeoFormRuntimeTask {
    @OutputFile
    public abstract RegularFileProperty getAssetsProperties();

    @Input
    public abstract ListProperty<String> getArguments();

    @Inject
    public DownloadAssetsTask() {
        this.getOutputs().upToDateWhen(t -> {
            var task = (DownloadAssetsTask) t;
            var propertiesFile = task.getAssetsProperties().get().getAsFile();
            if (propertiesFile.exists()) {
                try (var input = new FileInputStream(propertiesFile)) {
                    Properties properties = new Properties();
                    properties.load(input);
                    if (!properties.containsKey("assets_root")) {
                        return false;
                    }
                    String assetsRoot = properties.getProperty("assets_root");
                    var assetsDir = new File(assetsRoot);
                    if (!assetsDir.exists() || !assetsDir.isDirectory()) {
                        return false;
                    }
                } catch (IOException e) {
                    return false;
                }
            }
            return true;
        });
    }

    @TaskAction
    void execute() {
        List<String> arguments = new ArrayList<>();

        arguments.add("download-assets");
        arguments.add("--write-properties");
        arguments.add(getAssetsProperties().get().getAsFile().getAbsolutePath());
        arguments.addAll(getArguments().get());

        invokeNFRT(arguments);
    }
}
