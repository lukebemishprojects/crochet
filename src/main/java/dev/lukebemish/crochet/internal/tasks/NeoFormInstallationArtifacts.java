package dev.lukebemish.crochet.internal.tasks;

import dev.lukebemish.taskgraphrunner.model.Config;
import dev.lukebemish.taskgraphrunner.model.Distribution;
import dev.lukebemish.taskgraphrunner.model.ListOrdering;
import dev.lukebemish.taskgraphrunner.model.Value;
import dev.lukebemish.taskgraphrunner.model.conversion.NeoFormGenerator;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;
import java.io.IOException;

public abstract class NeoFormInstallationArtifacts implements TaskGraphExecution.ConfigMaker {
    @Inject
    public NeoFormInstallationArtifacts() {}

    @Override
    public Config makeConfig() throws IOException {
        boolean hasAccessTransformers = !getAccessTransformers().isEmpty();
        boolean hasInjectedInterfaces = !getInjectedInterfaces().isEmpty();
        boolean hasParchment = !getParchment().isEmpty();

        var options = NeoFormGenerator.Options.builder()
            .distribution(Distribution.JOINED); // for now we only do joined; we'll figure other stuff out later (probably by after-the-fact splitting)

        if (hasAccessTransformers) {
            options.accessTransformersParameter("accessTransformers");
        }
        if (hasInjectedInterfaces) {
            options.interfaceInjectionDataParameter("injectedInterfaces");
        }
        if (hasParchment) {
            options.parchmentDataParameter("parchment");
        }

        options.recompile(getRecompile().get());
        options.fixLineNumbers(!getRecompile().get());

        options.clientMappings(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-client-mappings")));
        options.serverMappings(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-server-mappings")));
        options.versionJson(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-version-json")));
        options.clientJar(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-client-jar")));
        options.serverJar(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-server-jar")));

        var neoFormConfig = getNeoForm().getSingleFile().toPath();

        var config = NeoFormGenerator.convert(neoFormConfig, Value.file(neoFormConfig), options.build());

        if (!getAccessTransformers().isEmpty()) {
            config.parameters.put("accessTransformers",
                new Value.ListValue(
                    getAccessTransformers().getFiles().stream()
                        .<Value>map(f -> Value.file(f.toPath()))
                        .toList(),
                    ListOrdering.CONTENTS
                )
            );
        }
        if (!getInjectedInterfaces().isEmpty()) {
            config.parameters.put("injectedInterfaces",
                new Value.ListValue(
                    getInjectedInterfaces().getFiles().stream()
                        .<Value>map(f -> Value.file(f.toPath()))
                        .toList(),
                    ListOrdering.CONTENTS
                )
            );
        }
        if (!getParchment().isEmpty()) {
            config.parameters.put("parchment",
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

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getNeoForm();

    @Input
    public abstract Property<Boolean> getRecompile();
}
