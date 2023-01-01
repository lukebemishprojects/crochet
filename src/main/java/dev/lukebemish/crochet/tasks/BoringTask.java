package dev.lukebemish.crochet.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;

import java.io.File;
import java.util.Set;

public abstract class BoringTask extends DefaultTask {
    @Input
    public abstract Configuration getConfiguration();
    public abstract void setConfiguration(Configuration configuration);

    @OutputFiles
    public Provider<Set<File>> getOutputFiles() {
        //return this.getProject().provider(() -> getConfiguration().resolve());
        return this.getProject().provider(() -> {
            return getConfiguration().resolve();
        });
    }
}
