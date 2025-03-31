package dev.lukebemish.crochet.internal.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import javax.inject.Inject;

@UntrackedTask(because = "Relies on its own up-to-date checks, taking no input or output")
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
