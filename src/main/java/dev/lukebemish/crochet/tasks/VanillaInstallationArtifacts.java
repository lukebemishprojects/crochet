package dev.lukebemish.crochet.tasks;

import dev.lukebemish.taskgraphrunner.model.Config;
import dev.lukebemish.taskgraphrunner.model.Distribution;
import dev.lukebemish.taskgraphrunner.model.Value;
import dev.lukebemish.taskgraphrunner.model.conversion.SingleVersionGenerator;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

public abstract class VanillaInstallationArtifacts implements TaskGraphExecution.ConfigMaker {
    @Inject
    public VanillaInstallationArtifacts(Project project) {
        getSidedAnnotation().convention(SingleVersionGenerator.Options.SidedAnnotation.FABRIC);
        getHasAccessTransformers().convention(project.provider(() -> !getAccessTransformers().isEmpty()));
        getHasInjectedInterfaces().convention(project.provider(() -> !getInjectedInterfaces().isEmpty()));
    }

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    @Optional
    public abstract Property<SingleVersionGenerator.Options.SidedAnnotation> getSidedAnnotation();

    @Override
    public Config makeConfig() throws IOException {
        var options = SingleVersionGenerator.Options.builder()
            .sidedAnnotation(getSidedAnnotation().getOrNull())
            .distribution(Distribution.JOINED); // for now we only do joined; we'll figure other stuff out later (probably by after-the-fact splitting)
        if (getHasAccessTransformers().get()) {
            options.accessTransformersParameter("accessTransformers");
        }
        if (getHasInjectedInterfaces().get()) {
            options.interfaceInjectionDataParameter("injectedInterfaces");
        }
        if (!getParchment().isEmpty()) {
            options.parchmentDataParameter("parchmentData");
        }

        var config = SingleVersionGenerator.convert(getMinecraftVersion().get(), options.build());
        if (!getAccessTransformers().isEmpty()) {
            config.parameters.put("accessTransformers",
                new Value.ListValue(
                    getAccessTransformers().getFiles().stream()
                        .<Value>map(f -> Value.file(f.toPath()))
                        .toList()
                )
            );
        } else if (getHasAccessTransformers().get()) {
            config.parameters.put("accessTransformers", new Value.ListValue(List.of()));
        }
        if (!getInjectedInterfaces().isEmpty()) {
            config.parameters.put("injectedInterfaces",
                new Value.ListValue(
                    getInjectedInterfaces().getFiles().stream()
                        .<Value>map(f -> Value.file(f.toPath()))
                        .toList()
                )
            );
        } else if (getHasInjectedInterfaces().get()) {
            config.parameters.put("injectedInterfaces", new Value.ListValue(List.of()));
        }
        if (!getParchment().isEmpty()) {
            config.parameters.put("parchmentData",
                Value.file(getParchment().getSingleFile().toPath())
            );
        }
        return config;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getAccessTransformers();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getInjectedInterfaces();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getParchment();

    @Internal
    protected abstract Property<Boolean> getHasAccessTransformers();
    @Internal
    protected abstract Property<Boolean> getHasInjectedInterfaces();
}
