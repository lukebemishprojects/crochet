package dev.lukebemish.crochet.tasks;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.taskgraphrunner.model.Argument;
import dev.lukebemish.taskgraphrunner.model.Config;
import dev.lukebemish.taskgraphrunner.model.Input;
import dev.lukebemish.taskgraphrunner.model.Input.DirectInput;
import dev.lukebemish.taskgraphrunner.model.Input.ParameterInput;
import dev.lukebemish.taskgraphrunner.model.Input.TaskInput;
import dev.lukebemish.taskgraphrunner.model.MappingsFormat;
import dev.lukebemish.taskgraphrunner.model.MappingsSource;
import dev.lukebemish.taskgraphrunner.model.Output;
import dev.lukebemish.taskgraphrunner.model.PathSensitivity;
import dev.lukebemish.taskgraphrunner.model.TaskModel;
import dev.lukebemish.taskgraphrunner.model.Value;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public abstract class FabricInstallationArtifacts implements TaskGraphExecution.ConfigMaker {
    @Nested
    public abstract Property<VanillaInstallationArtifacts> getWrapped();

    @InputFile
    @PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    public abstract RegularFileProperty getIntermediary();

    @InputFiles
    @PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getExtractedAccessWideners();

    @InputFiles
    @PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getAccessWideners();

    @Override
    public Config makeConfig() throws IOException {
        var wrappedArtifacts = getWrapped().get();
        wrappedArtifacts.getHasAccessTransformers().set(wrappedArtifacts.getHasAccessTransformers().get() || !getAccessWideners().isEmpty() || !getExtractedAccessWideners().isEmpty());
        var wrapped = wrappedArtifacts.makeConfig();

        wrapped.parameters.put("intermediary", Value.file(getIntermediary().get().getAsFile().toPath()));
        wrapped.tasks.add(new TaskModel.TransformMappings(
            "mojangToIntermediaryMappings",
            MappingsFormat.TINY2,
            new MappingsSource.Chained(List.of(
                new MappingsSource.File(new TaskInput(new Output("downloadClientMappings", "output"))),
                new MappingsSource.File(new ParameterInput("intermediary"))
            ))
        ));

        wrapped.tasks.add(new TaskModel.TransformMappings(
            "intermediaryToMojangMappings",
            MappingsFormat.TINY2,
            new MappingsSource.Reversed(
                new MappingsSource.File(new TaskInput(new Output("mojangToIntermediaryMappings", "output")))
            )
        ));

        if (!getAccessWideners().isEmpty() || !getExtractedAccessWideners().isEmpty()) {
            var transformAccessWideners = new TaskModel.Tool(
                "transformAccessWideners",
                List.of(
                    Argument.direct("-jar"),
                    new Argument.FileInput(null, new Input.DirectInput(Value.artifact("dev.lukebemish.crochet:tools:" + CrochetPlugin.VERSION)), PathSensitivity.NONE),
                    Argument.direct("transform-access-wideners"),
                    new Argument.FileOutput("--output={}", "output", "cfg"),
                    new Argument.FileInput("--mappings={}", new TaskInput(new Output("intermediaryToMojangMappings", "output")), PathSensitivity.NONE)
                )
            );

            wrapped.tasks.add(transformAccessWideners);

            if (!getAccessWideners().isEmpty()) {
                wrapped.parameters.put("injectedInterfaces",
                    new Value.ListValue(
                        getAccessWideners().getFiles().stream()
                            .<Value>map(f -> Value.file(f.toPath()))
                            .toList()
                    )
                );
                transformAccessWideners.args.add(new Argument.Zip("--input={}", List.of(new ParameterInput("accessWideners")), PathSensitivity.NONE));
            }

            if (!getExtractedAccessWideners().isEmpty()) {
                wrapped.parameters.put("extractedAccessWideners",
                    new Value.ListValue(
                        getExtractedAccessWideners().getFiles().stream()
                            .<Value>map(f -> Value.file(f.toPath()))
                            .toList()
                    )
                );
                transformAccessWideners.args.add(new Argument.Zip("--input={}", List.of(new ParameterInput("extractedAccessWideners")), PathSensitivity.NONE));
            }

            var existing = (TaskModel.Tool) wrapped.tasks.stream().filter(t -> t.name().equals("accessTransformers")).findFirst().orElseThrow();
            Input target = null;
            boolean capture = false;
            for (var arg : existing.args) {
                if (arg instanceof Argument.ValueInput valueInput && valueInput.input instanceof DirectInput directInput && directInput.value() instanceof Value.StringValue stringValue && "--inJar".equals(stringValue.value())) {
                    capture = true;
                } else if (capture && arg instanceof Argument.FileInput fileInput) {
                    target = fileInput.input;
                    break;
                }
            }
            transformAccessWideners.args.add(new Argument.FileInput("--target={}", Objects.requireNonNull(target), PathSensitivity.NONE));
            existing.args.add(new Argument.FileInput("--atFile={}", new Input.TaskInput(new Output("transformAccessWideners", "output")), PathSensitivity.NONE));
        }

        wrapped.tasks.add(new TaskModel.Tool("intermediaryRename", List.of(
            Argument.direct("-jar"),
            new Argument.FileInput(null, new Input.DirectInput(Value.tool("autorenamingtool")), PathSensitivity.NONE),
            Argument.direct("--input"),
            new Argument.FileInput(null, new TaskInput(wrapped.aliases.get("binary")), PathSensitivity.NONE),
            Argument.direct("--output"),
            new Argument.FileOutput(null, "output", "jar"),
            Argument.direct("--map"),
            new Argument.FileInput(null, new TaskInput(new Output("mojangToIntermediaryMappings", "output")), PathSensitivity.NONE),
            Argument.direct("--cfg"),
            new Argument.LibrariesFile(null, List.of(new TaskInput(new Output("listLibraries", "output"))), new DirectInput(new Value.StringValue("-e=")))
        )));
        wrapped.aliases.put("intermediary", new Output("intermediaryRename", "output"));

        return wrapped;
    }
}
