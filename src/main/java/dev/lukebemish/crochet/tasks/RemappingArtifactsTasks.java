package dev.lukebemish.crochet.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;
import java.util.function.Consumer;

public abstract class RemappingArtifactsTasks extends AbstractRuntimeArtifactsTask {
    @Internal
    public abstract RegularFileProperty getIntermediaryJar();

    @Input
    public abstract Property<String> getNeoFormModule();

    @Inject
    public RemappingArtifactsTasks() {
        getTargets().put("vanillaDeobfuscated", getIntermediaryJar());
    }

    @Override
    public void collectArguments(Consumer<String> arguments) {
        arguments.accept("--neoform");
        arguments.accept(getNeoFormModule().get());
    }
}
