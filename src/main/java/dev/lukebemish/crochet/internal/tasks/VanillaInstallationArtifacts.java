package dev.lukebemish.crochet.internal.tasks;

import dev.lukebemish.crochet.model.mappings.MappingsStructure;
import dev.lukebemish.crochet.model.InstallationDistribution;
import dev.lukebemish.taskgraphrunner.model.Argument;
import dev.lukebemish.taskgraphrunner.model.Config;
import dev.lukebemish.taskgraphrunner.model.Distribution;
import dev.lukebemish.taskgraphrunner.model.ListOrdering;
import dev.lukebemish.taskgraphrunner.model.MappingsFormat;
import dev.lukebemish.taskgraphrunner.model.Output;
import dev.lukebemish.taskgraphrunner.model.TaskModel;
import dev.lukebemish.taskgraphrunner.model.Value;
import dev.lukebemish.taskgraphrunner.model.conversion.AdditionalJst;
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
import org.jspecify.annotations.Nullable;

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

    @Input
    public abstract Property<InstallationDistribution> getDistribution();

    @Override
    public Config makeConfig() throws IOException{
        return makeConfig(getMappings().getOrNull(), getHasAccessTransformers().get(), getHasInjectedInterfaces().get());
    }

    protected Config makeConfig(@Nullable MappingsStructure mappings, boolean hasAccessTransformers, boolean hasInjectedInterfaces) throws IOException {
        var options = SingleVersionGenerator.Options.builder()
            .sidedAnnotation(getSidedAnnotation().getOrNull())
            .distribution(Distribution.JOINED); // for now we only do joined; we'll figure other stuff out later (probably by after-the-fact splitting)
        if (hasAccessTransformers) {
            options.accessTransformersParameter("accessTransformers");
        }
        if (hasInjectedInterfaces) {
            options.interfaceInjectionDataParameter("injectedInterfaces");
        }
        if (mappings != null) {
            options.mappingsParameter("mappings");
        }
        var distribution = getDistribution().get();
        if (distribution != InstallationDistribution.JOINED) {
            options.additionalJst(new AdditionalJst(
                List.of(new dev.lukebemish.taskgraphrunner.model.Input.ListInput(List.of(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.tool("unmergetool-jst"))))),
                List.of(
                    Argument.direct("--enable-unmergetool"),
                    Argument.direct("--distribution="+distribution.name()),
                    new Argument.FileInput("--target-classes={}", new dev.lukebemish.taskgraphrunner.model.Input.TaskInput(new Output("crochet_stripWrongSideBinary", "targetClasses")), dev.lukebemish.taskgraphrunner.model.PathSensitivity.NONE)
                )
            ));
        }

        options.clientMappings(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-mappings")));
        options.versionJson(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-version-json")));
        options.clientJar(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-client-jar")));
        options.serverJar(new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-server-jar")));

        var config = SingleVersionGenerator.convert(getMinecraftVersion().get(), options.build());
        if (!getAccessTransformers().isEmpty()) {
            config.parameters.put("accessTransformers",
                new Value.ListValue(
                    getAccessTransformers().getFiles().stream()
                        .<Value>map(f -> Value.file(f.toPath()))
                        .toList(),
                    ListOrdering.CONTENTS
                )
            );
        } else if (hasAccessTransformers) {
            config.parameters.put("accessTransformers", new Value.ListValue(List.of()));
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
        } else if (hasInjectedInterfaces) {
            config.parameters.put("injectedInterfaces", new Value.ListValue(List.of()));
        }

        if (mappings != null) {
            var clientMappings = new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-mappings"));
            var mappingsModel = MappingsStructure.toModel(mappings, clientMappings);
            var mappingsTask = new TaskModel.TransformMappings("crochetMakeMappings", MappingsFormat.TINY2, mappingsModel);
            mappingsTask.sourceJar = new dev.lukebemish.taskgraphrunner.model.Input.TaskInput(config.aliases.get("binaryObf"));
            config.tasks.add(mappingsTask);
            config.tasks.forEach(task -> {
                task.inputs().forEach(handle -> {
                    if (handle.getInput() instanceof dev.lukebemish.taskgraphrunner.model.Input.ParameterInput parameterInput && parameterInput.parameter().equals("mappings")) {
                        handle.setInput(new dev.lukebemish.taskgraphrunner.model.Input.TaskInput(new Output(mappingsTask.name(), "output")));
                    }
                });
            });
        }

        if (distribution != InstallationDistribution.JOINED) {
            var stripTask = new TaskModel.DaemonExecutedTool(
                "crochet_stripWrongSideBinary",
                List.of(
                    Argument.direct("--input"),
                    new Argument.FileInput(null, new dev.lukebemish.taskgraphrunner.model.Input.TaskInput(config.aliases.get("binarySourceIndependent")), dev.lukebemish.taskgraphrunner.model.PathSensitivity.NONE),
                    Argument.direct("--output"),
                    new Argument.FileOutput(null, "output", "jar"),
                    Argument.direct("--target-classes"),
                    new Argument.FileOutput(null, "targetClasses", "txt"),
                    Argument.direct("--distribution"),
                    Argument.direct(distribution.name())
                ),
                new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.tool("unmergetool"))
            );
            stripTask.classpathScopedJvm = true;
            config.tasks.add(stripTask);
            config.aliases.put("binarySourceIndependent", new Output(stripTask.name(), "output"));

            if (distribution == InstallationDistribution.CLIENT) {
                config.aliases.put("resources", new Output("stripClient", "resources"));
            } else if (distribution == InstallationDistribution.SERVER) {
                config.aliases.put("resources", new Output("stripServer", "resources"));
            }
        }

        return config;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getAccessTransformers();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getInjectedInterfaces();

    @Input
    @Optional
    public abstract Property<MappingsStructure> getMappings();

    @Internal
    protected abstract Property<Boolean> getHasAccessTransformers();
    @Internal
    protected abstract Property<Boolean> getHasInjectedInterfaces();
}
