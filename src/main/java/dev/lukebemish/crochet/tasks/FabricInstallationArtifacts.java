package dev.lukebemish.crochet.tasks;

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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;

import java.io.IOException;
import java.util.List;

public abstract class FabricInstallationArtifacts implements TaskGraphExecution.ConfigMaker {
    @Nested
    public abstract Property<TaskGraphExecution.ConfigMaker> getWrapped();

    @InputFile
    @PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    public abstract RegularFileProperty getIntermediary();

    @Override
    public Config makeConfig() throws IOException {
        var wrapped = getWrapped().get().makeConfig();

        wrapped.parameters.put("intermediary", Value.file(getIntermediary().get().getAsFile().toPath()));
        wrapped.tasks.add(new TaskModel.TransformMappings(
            "intermediaryMappings",
            MappingsFormat.TINY1,
            new MappingsSource.Chained(List.of(
                new MappingsSource.File(new TaskInput(new Output("downloadClientMappings", "output"))),
                new MappingsSource.File(new ParameterInput("intermediary"))
            ))
        ));

        wrapped.tasks.add(new TaskModel.Tool("intermediaryRename", List.of(
            Argument.direct("-jar"),
            new Argument.FileInput(null, new Input.DirectInput(Value.tool("autorenamingtool")), PathSensitivity.NONE),
            Argument.direct("--input"),
            new Argument.FileInput(null, new TaskInput(wrapped.aliases.get("binary")), PathSensitivity.NONE),
            Argument.direct("--output"),
            new Argument.FileOutput(null, "output", "jar"),
            Argument.direct("--map"),
            new Argument.FileInput(null, new TaskInput(new Output("intermediaryMappings", "output")), PathSensitivity.NONE),
            Argument.direct("--cfg"),
            new Argument.LibrariesFile(null, List.of(new TaskInput(new Output("listLibraries", "output"))), new DirectInput(new Value.StringValue("-e=")))
        )));
        wrapped.aliases.put("intermediary", new Output("intermediaryRename", "output"));

        return wrapped;
    }
}
