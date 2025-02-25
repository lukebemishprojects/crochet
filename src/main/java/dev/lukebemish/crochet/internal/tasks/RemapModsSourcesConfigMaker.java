package dev.lukebemish.crochet.internal.tasks;

import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.Versions;
import dev.lukebemish.taskgraphrunner.model.Argument;
import dev.lukebemish.taskgraphrunner.model.Config;
import dev.lukebemish.taskgraphrunner.model.Input;
import dev.lukebemish.taskgraphrunner.model.ListOrdering;
import dev.lukebemish.taskgraphrunner.model.Output;
import dev.lukebemish.taskgraphrunner.model.TaskModel;
import dev.lukebemish.taskgraphrunner.model.Value;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

public abstract class RemapModsSourcesConfigMaker implements TaskGraphExecution.ConfigMaker {
    @Nested
    public abstract ListProperty<ArtifactTarget> getTargets();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getRemappingClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getMappings();

    @Inject
    public RemapModsSourcesConfigMaker() {}

    @Override
    public Config makeConfig() throws IOException {
        var config = new Config();

        if (getTargets().get().isEmpty()) {
            var emptyJar = new TaskModel.InjectSources("remapSources", List.of());
            config.tasks.add(emptyJar);
            return config;
        }

        for (var target : getTargets().get()) {
            var outPath = target.getTarget().get().getAsFile().toPath();
            if (Files.exists(outPath)) {
                Files.delete(outPath);
            }
        }

        List<String> outputNames = new ArrayList<>();
        SortedMap<String, ArtifactTarget> outputMap = new TreeMap<>();
        for (var target : getTargets().get()) {
            outputNames.add(target.getSanitizedName().get());
            outputMap.put(target.getSanitizedName().get(), target);
        }
        outputNames.sort(Comparator.naturalOrder());

        var combineSourcesTask = new TaskModel.InjectSources("combineSources", List.of());

        for (var name : outputNames) {
            combineSourcesTask.inputs.add(new Input.ParameterInput(name));
        }

        config.tasks.add(combineSourcesTask);

        var remapTask = new TaskModel.DaemonExecutedTool(
            "remapSources",
            List.of(
                new Argument.FileInput(null, new Input.TaskInput(new Output(combineSourcesTask.name(), "output")), dev.lukebemish.taskgraphrunner.model.PathSensitivity.NONE),
                new Argument.FileOutput(null, "output", "zip"),
                new Argument.Classpath("--classpath={}", List.of(new Input.ParameterInput("remappingClasspath"))),
                Argument.direct("--in-format=ARCHIVE"),
                Argument.direct("--out-format=ARCHIVE"),
                Argument.direct("--enable-christen"),
                new Argument.FileInput("--christen-mappings={}", new Input.ParameterInput("mappings"), dev.lukebemish.taskgraphrunner.model.PathSensitivity.NONE)
            ),
            new Input.DirectInput(Value.artifact("dev.lukebemish:christen:" + Versions.CHRISTEN + ":all"))
        );

        config.tasks.add(remapTask);

        config.parameters.put("mappings", Value.file(getMappings().get().getAsFile().toPath()));
        config.parameters.put("remappingClasspath", new Value.ListValue(getRemappingClasspath().getFiles().stream().<Value>map(f -> Value.file(f.toPath())).toList(), ListOrdering.CONTENTS));

        for (var entry : outputMap.entrySet()) {
            config.parameters.put(entry.getKey(), Value.file(entry.getValue().getSource().get().getAsFile().toPath()));
        }

        return config;
    }

    @SuppressWarnings("UnstableApiUsage")
    public void setup(TaskGraphExecution outer, Configuration source, Configuration exclude, Directory destinationDirectory) {
        var targetFile = destinationDirectory.file("sources.zip");

        Action<ArtifactView.ViewConfiguration> action = view -> {
            view.lenient(true);
            view.withVariantReselection();
            view.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, outer.getProject().getObjects().named(Category.class, Category.DOCUMENTATION));
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, outer.getProject().getObjects().named(DocsType.class, DocsType.SOURCES));
            });
        };

        var sourceSources = source.getIncoming().artifactView(action);
        var excludeSources = exclude.getIncoming().artifactView(action);

        outer.dependsOn(sourceSources.getFiles());
        var sourceArtifacts = sourceSources.getArtifacts().getResolvedArtifacts();
        var excludeArtifacts = excludeSources.getArtifacts().getResolvedArtifacts();
        var targetsProvider = outer.getProject().provider(() -> {
            var artifacts = sourceArtifacts.get();
            var excluded = excludeArtifacts.get();
            var excludeCapabilities = new HashSet<String>();
            excluded.forEach(result -> {
                result.getVariant().getCapabilities().forEach(capability -> {
                    excludeCapabilities.add(capability.getGroup() + ":" + capability.getName());
                });
            });
            var targets = new ArrayList<>(artifacts.stream()
                .filter(artifact ->
                    artifact.getVariant().getCapabilities().stream()
                        .noneMatch(it -> excludeCapabilities.contains(it.getGroup() + ":" + it.getName()))
                )
                .map(artifact -> {
                    var target = outer.getProject().getObjects().newInstance(ArtifactTarget.class);
                    target.getSource().set(artifact.getFile());
                    var singleTargetFile = destinationDirectory.file(artifact.getFile().getName());
                    target.getTarget().set(singleTargetFile);
                    for (var capability : artifact.getVariant().getCapabilities()) {
                        target.getCapabilities().add(capability.getGroup() + ":" + capability.getName());
                    }
                    return target;
                }).toList()
            );
            Map<String, Integer> uniqueNamesMap = new LinkedHashMap<>();
            for (var target : targets) {
                var name = target.getTarget().get().getAsFile().getName();
                var sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
                /*var count = uniqueNamesMap.computeIfAbsent(sanitized, k -> 0);
                uniqueNamesMap.put(sanitized, count + 1);
                target.getTarget().set(destinationDirectory.file(name.substring(0, name.lastIndexOf('.')) + "-" + count + name.substring(name.lastIndexOf('.'))));
                target.getSanitizedName().set(sanitized + "_" + count);*/
                // These all share the same name -- so for no, no unique name map (as long as this is true)
                target.getSanitizedName().set(sanitized);
                target.getTarget().set(targetFile);

                target.getTarget().finalizeValueOnRead();
                target.getSource().finalizeValueOnRead();
            }
            return targets;
        });
        var property = outer.getProject().getObjects().listProperty(ArtifactTarget.class);
        property.set(targetsProvider);
        property.finalizeValueOnRead();
        this.getTargets().addAll(property);

        var output = outer.getProject().getObjects().newInstance(TaskGraphExecution.GraphOutput.class);
        output.getOutputFile().set(targetFile);
        output.getOutputName().set("remapSources.output");
        outer.getTargets().add(output);
    }

    public void remapSingleJar(TaskGraphExecution task, Consumer<RegularFileProperty> input, Consumer<RegularFileProperty> output, Consumer<RegularFileProperty> mappings, FileCollection remappingClasspath) {
        task.dependsOn(remappingClasspath);
        var target = task.getProject().getObjects().newInstance(ArtifactTarget.class);
        target.getSanitizedName().set("remapped");
        input.accept(target.getSource());
        output.accept(target.getTarget());
        getTargets().add(target);
        var graphOutput = task.getProject().getObjects().newInstance(TaskGraphExecution.GraphOutput.class);
        graphOutput.getOutputName().set("remapSources.output");
        graphOutput.getOutputFile().set(target.getTarget());
        task.getTargets().add(graphOutput);
        mappings.accept(getMappings());
        getRemappingClasspath().from(remappingClasspath);
        task.doFirst(rawTask -> {
            var t = (TaskGraphExecution) rawTask;
            var cacheDir = t.getTemporaryDir().toPath().resolve("singleUseCache");
            try {
                if (Files.exists(cacheDir)) {
                    FileUtils.deleteDirectory(cacheDir.toFile());
                }
                Files.createDirectories(cacheDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            t.getRuntimeCacheDirectory().set(cacheDir.toFile());
        });
        task.doLast(t -> {
            var cacheDir = t.getTemporaryDir().toPath().resolve("singleUseCache");
            try {
                if (Files.exists(cacheDir)) {
                    FileUtils.deleteDirectory(cacheDir.toFile());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
