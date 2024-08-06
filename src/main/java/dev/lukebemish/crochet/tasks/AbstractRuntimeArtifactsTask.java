package dev.lukebemish.crochet.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractRuntimeArtifactsTask extends AbstractNeoFormRuntimeTask {

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getAccessTransformers();

    @OutputFiles
    abstract MapProperty<String, RegularFile> getTargets();

    @Inject
    public AbstractRuntimeArtifactsTask() {}

    @TaskAction
    void execute() {
        List<String> arguments = new ArrayList<>();

        arguments.add("run");
        arguments.add("--dist");
        // For now, we always use the whole thing -- later we'll see about split source sets
        arguments.add("joined");

        for (var accessTransformer : getAccessTransformers().getFiles()) {
            arguments.add("--access-transformer");
            arguments.add(accessTransformer.getAbsolutePath());
        }

        // Each output comes as a --write-result=nameoftarget:path/to/output
        for (var target : getTargets().get().entrySet()) {
            arguments.add("--write-result=" + target.getKey() + ":" + target.getValue().getAsFile().getAbsolutePath());
        }

        collectArguments(arguments::add);

        invokeNFRT(arguments);
    }

    protected abstract void collectArguments(Consumer<String> arguments);
}
