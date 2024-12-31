package dev.lukebemish.crochet.internal.tasks;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.mappings.FileMappingsStructure;
import dev.lukebemish.crochet.mappings.MergedMappingsStructure;
import dev.lukebemish.crochet.mappings.MojangOfficialMappingsStructure;
import dev.lukebemish.taskgraphrunner.model.Argument;
import dev.lukebemish.taskgraphrunner.model.Config;
import dev.lukebemish.taskgraphrunner.model.Input;
import dev.lukebemish.taskgraphrunner.model.InputValue;
import dev.lukebemish.taskgraphrunner.model.ListOrdering;
import dev.lukebemish.taskgraphrunner.model.MappingsFormat;
import dev.lukebemish.taskgraphrunner.model.MappingsSource;
import dev.lukebemish.taskgraphrunner.model.Output;
import dev.lukebemish.taskgraphrunner.model.PathSensitivity;
import dev.lukebemish.taskgraphrunner.model.TaskModel;
import dev.lukebemish.taskgraphrunner.model.Value;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;

import javax.inject.Inject;
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
    public abstract ConfigurableFileCollection getInterfaceInjection();

    @InputFiles
    @PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getAccessWideners();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    public FabricInstallationArtifacts() {}

    @Override
    public Config makeConfig() throws IOException {
        var wrappedArtifacts = getWrapped().get();

        boolean hasAccessTransformers = wrappedArtifacts.getHasAccessTransformers().get() || !getAccessWideners().isEmpty();
        boolean hasInjectedInterfaces = wrappedArtifacts.getHasInjectedInterfaces().get() || !getInterfaceInjection().isEmpty();

        var mappings = wrappedArtifacts.getMappings().getOrNull();
        var mergedMappings = getObjects().newInstance(MergedMappingsStructure.class);
        mergedMappings.getInputMappings().add(Objects.requireNonNullElse(mappings, MojangOfficialMappingsStructure.INSTANCE));
        var intermediaryMappings = getObjects().newInstance(FileMappingsStructure.class);
        intermediaryMappings.getMappingsFile().from(getIntermediary());
        mergedMappings.getInputMappings().add(intermediaryMappings);

        var wrapped = wrappedArtifacts.makeConfig(mappings, hasAccessTransformers, hasInjectedInterfaces);

        wrapped.parameters.put("intermediary", Value.file(getIntermediary().get().getAsFile().toPath()));

        dev.lukebemish.taskgraphrunner.model.Input mappingsInput = new dev.lukebemish.taskgraphrunner.model.Input.DirectInput(Value.artifact("dev.lukebemish.crochet.internal:minecraft-mappings"));
        boolean vanillaMappings = true;
        boolean reversedMappings = true;
        if (wrappedArtifacts.getMappings().isPresent()) {
            mappingsInput = new Input.TaskInput(new Output("crochetMakeMappings", "output"));
            reversedMappings = false;
            vanillaMappings = false;
        }

        MappingsSource.File namedToObjMappingsFile = new MappingsSource.File(mappingsInput);
        MappingsSource namedToObfMappings = namedToObjMappingsFile;
        if (vanillaMappings) {
            namedToObjMappingsFile.extension = new InputValue.DirectInput(new Value.DirectStringValue("txt"));
        }
        if (!reversedMappings) {
            namedToObfMappings = new MappingsSource.Reversed(namedToObfMappings);
        }

        var obfToIntermediaryMappings = new TaskModel.TransformMappings(
            "obfToIntermediaryMappings",
            MappingsFormat.TINY2,
            new MappingsSource.File(new Input.ParameterInput("intermediary"))
        );
        obfToIntermediaryMappings.sourceJar = new Input.TaskInput(wrapped.aliases.get("binaryObf"));
        wrapped.tasks.add(obfToIntermediaryMappings);

        wrapped.tasks.add(new TaskModel.TransformMappings(
            "namedToIntermediaryMappings",
            MappingsFormat.TINY2,
            new MappingsSource.Chained(List.of(
                namedToObfMappings,
                new MappingsSource.File(new Input.TaskInput(new Output(obfToIntermediaryMappings.name(), "output")))
            ))
        ));

        wrapped.tasks.add(new TaskModel.TransformMappings(
            "intermediaryToNamedMappings",
            MappingsFormat.TINY2,
            new MappingsSource.Reversed(
                new MappingsSource.File(new Input.TaskInput(new Output("namedToIntermediaryMappings", "output")))
            )
        ));

        if (!getAccessWideners().isEmpty()) {
            var transformAccessWideners = new TaskModel.DaemonExecutedTool(
                "transformAccessWideners",
                List.of(
                    Argument.direct("transform-access-wideners"),
                    new Argument.FileOutput("--output={}", "output", "cfg"),
                    new Argument.FileInput("--mappings={}", new Input.TaskInput(new Output("intermediaryToNamedMappings", "output")), PathSensitivity.NONE)
                ),
                new Input.DirectInput(Value.artifact("dev.lukebemish.crochet:tools:" + CrochetPlugin.VERSION))
            );
            transformAccessWideners.classpathScopedJvm = true;

            wrapped.tasks.add(transformAccessWideners);

            wrapped.parameters.put("accessWideners",
                new Value.ListValue(
                    getAccessWideners().getFiles().stream()
                        .<Value>map(f -> Value.file(f.toPath()))
                        .toList(),
                    ListOrdering.CONTENTS
                )
            );
            transformAccessWideners.args.add(new Argument.Zip("--input={}", List.of(new Input.ParameterInput("accessWideners")), PathSensitivity.NONE));

            var existing = (TaskModel.DaemonExecutedTool) wrapped.tasks.stream().filter(t -> t.name().equals("accessTransformers")).findFirst().orElseThrow();
            Input target = null;
            boolean capture = false;
            for (var arg : existing.args) {
                if (arg instanceof Argument.ValueInput valueInput && valueInput.input instanceof InputValue.DirectInput directInput && directInput.value() instanceof Value.StringValue stringValue && "--inJar".equals(stringValue.value())) {
                    capture = true;
                } else if (capture && arg instanceof Argument.FileInput fileInput) {
                    target = fileInput.input;
                    break;
                }
            }
            transformAccessWideners.args.add(new Argument.FileInput("--target={}", Objects.requireNonNull(target), PathSensitivity.NONE));
            existing.args.add(new Argument.FileInput("--atFile={}", new Input.TaskInput(new Output("transformAccessWideners", "output")), PathSensitivity.NONE));
        }

        if (!getInterfaceInjection().isEmpty()) {
            var transformInterfaceInjection = new TaskModel.DaemonExecutedTool(
                "transformInterfaceInjection",
                List.of(
                    Argument.direct("transform-interface-injection"),
                    new Argument.FileOutput("--output={}", "output", "json"),
                    new Argument.FileInput("--mappings={}", new Input.TaskInput(new Output("intermediaryToNamedMappings", "output")), PathSensitivity.NONE)
                ),
                new Input.DirectInput(Value.artifact("dev.lukebemish.crochet:tools:" + CrochetPlugin.VERSION))
            );
            transformInterfaceInjection.classpathScopedJvm = true;

            wrapped.tasks.add(transformInterfaceInjection);

            wrapped.parameters.put("fabricInjectedInterfaces",
                new Value.ListValue(
                    getInterfaceInjection().getFiles().stream()
                        .<Value>map(f -> Value.file(f.toPath()))
                        .toList(),
                    ListOrdering.CONTENTS
                )
            );

            transformInterfaceInjection.args.add(new Argument.Zip("--input={}", List.of(new Input.ParameterInput("fabricInjectedInterfaces")), PathSensitivity.NONE));

            var existing = (TaskModel.InterfaceInjection) wrapped.tasks.stream().filter(t -> t.name().equals("interfaceInjection")).findFirst().orElseThrow();
            transformInterfaceInjection.args.add(new Argument.Zip("--neo-input={}", List.of(existing.interfaceInjection), PathSensitivity.NONE));
            existing.interfaceInjection = new Input.ListInput(List.of(new Input.TaskInput(new Output(transformInterfaceInjection.name(), "output"))));
        }

        var intermediaryRename = new TaskModel.DaemonExecutedTool("intermediaryRename", List.of(
            Argument.direct("--input"),
            new Argument.FileInput(null, new Input.TaskInput(wrapped.aliases.get("binarySourceIndependent")), PathSensitivity.NONE),
            Argument.direct("--output"),
            new Argument.FileOutput(null, "output", "jar"),
            Argument.direct("--map"),
            new Argument.FileInput(null, new Input.TaskInput(new Output("namedToIntermediaryMappings", "output")), PathSensitivity.NONE),
            Argument.direct("--cfg"),
            new Argument.LibrariesFile(null, List.of(new Input.TaskInput(new Output("listLibraries", "output"))), new InputValue.DirectInput(new Value.DirectStringValue("-e=")))
        ), new Input.DirectInput(Value.tool("autorenamingtool")));
        intermediaryRename.classpathScopedJvm = true;
        wrapped.tasks.add(intermediaryRename);
        wrapped.aliases.put("intermediary", new Output("intermediaryRename", "output"));

        return wrapped;
    }
}
