package dev.lukebemish.crochet.internal.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public abstract class MakeRunDirectories extends DefaultTask {
    @Inject
    public MakeRunDirectories() {
        this.getOutputs().upToDateWhen(t -> {
            for (var dir : ((MakeRunDirectories) t).getRunDirectories()) {
                if (!dir.exists()) {
                    return false;
                }
            }
            return true;
        });
    }

    @Internal
    public abstract ConfigurableFileCollection getRunDirectories();

    @TaskAction
    public void makeRunDirectories() {
        for (var dir : getRunDirectories()) {
            dir.mkdirs();
        }
    }
}
