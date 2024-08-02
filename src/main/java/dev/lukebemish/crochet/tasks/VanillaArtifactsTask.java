package dev.lukebemish.crochet.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;
import java.util.function.Consumer;

public abstract class VanillaArtifactsTask extends AbstractRuntimeArtifactsTask {
    @Internal
    public abstract RegularFileProperty getClientResources();

    @Internal
    public abstract RegularFileProperty getCompiled();

    @Internal
    public abstract RegularFileProperty getSources();

    @Internal
    public abstract RegularFileProperty getSourcesAndCompiled();

    @Input
    public abstract Property<String> getNeoFormModule();

    @Inject
    public VanillaArtifactsTask() {
        getTargets().put("clientResources", getClientResources());
        getTargets().put("compiled", getCompiled());
        getTargets().put("sources", getSources());
        getTargets().put("sourcesAndCompiled", getSourcesAndCompiled());
    }

    @Override
    public void collectArguments(Consumer<String> arguments) {
        arguments.accept("--neoform");
        arguments.accept(getNeoFormModule().get());
    }
}
