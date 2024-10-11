package dev.lukebemish.crochet.tasks;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.taskgraphrunner.model.Argument;
import dev.lukebemish.taskgraphrunner.model.Config;
import dev.lukebemish.taskgraphrunner.model.Input;
import dev.lukebemish.taskgraphrunner.model.TaskModel;
import dev.lukebemish.taskgraphrunner.model.Value;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class RemapModsConfigMaker implements TaskGraphExecution.ConfigMaker {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemapModsConfigMaker.class);
    private static final String PROPERTY_HIDE_PROJECTARTIFACT_WARNING = "dev.lukebemish.crochet.hidewarnings.remap.projectartifact";

    @Nested
    public abstract ListProperty<ArtifactTarget> getTargets();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getRemappingClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getMappings();

    @Inject
    public RemapModsConfigMaker() {}

    @Override
    public Config makeConfig() throws IOException {
        var config = new Config();

        for (var target : getTargets().get()) {
            var outPath = target.getTarget().get().getAsFile().toPath();
            if (Files.exists(outPath)) {
                Files.delete(outPath);
            }
        }

        List<String> outputNames = new ArrayList<>();
        Map<String, ArtifactTarget> outputMap = new HashMap<>();
        for (var target : getTargets().get()) {
            outputNames.add(target.getSanitizedName().get());
            outputMap.put(target.getSanitizedName().get(), target);
        }

        outputNames.sort(Comparator.naturalOrder());

        var remapTask = new TaskModel.DaemonExecutedTool("remapMods", List.of(
            Argument.direct("remap-mods")
        ), new Input.DirectInput(Value.artifact("dev.lukebemish.crochet:tools:" + CrochetPlugin.VERSION)));

        remapTask.classpathScopedJvm = true;

        for (var name : outputNames) {
            remapTask.args.add(new Argument.FileInput(null, new Input.ParameterInput(name), dev.lukebemish.taskgraphrunner.model.PathSensitivity.NONE));
            remapTask.args.add(new Argument.FileOutput(null, name, "jar"));
        }

        remapTask.args.add(new Argument.FileInput("--mappings={}", new Input.ParameterInput("mappings"), dev.lukebemish.taskgraphrunner.model.PathSensitivity.NONE));
        remapTask.args.add(new Argument.Classpath("--classpath={}", List.of(new Input.ParameterInput("remappingClasspath"))));

        config.tasks.add(remapTask);

        config.parameters.put("mappings", Value.file(getMappings().get().getAsFile().toPath()));
        config.parameters.put("remappingClasspath", new Value.ListValue(getRemappingClasspath().getFiles().stream().<Value>map(f -> Value.file(f.toPath())).toList()));

        for (var entry : outputMap.entrySet()) {
            config.parameters.put(entry.getKey(), Value.file(entry.getValue().getSource().get().getAsFile().toPath()));
        }

        return config;
    }

    public void setup(TaskGraphExecution outer, Configuration source, Configuration exclude, Directory destinationDirectory, ConfigurableFileCollection destinationFiles) {
        destinationFiles.builtBy(this);

        outer.dependsOn(source);
        var sourceArtifacts = source.getIncoming().getArtifacts().getResolvedArtifacts();
        var excludeArtifacts = exclude.getIncoming().getArtifacts().getResolvedArtifacts();
        var targetsProvider = sourceArtifacts.zip(excludeArtifacts, (artifacts, excluded) -> {
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
                    if (artifact.getVariant().getOwner() instanceof ProjectComponentIdentifier id) {
                        if (!outer.getProject().getProviders().gradleProperty(PROPERTY_HIDE_PROJECTARTIFACT_WARNING).map(s -> s.equalsIgnoreCase("true")).getOrElse(false)) {
                            LOGGER.warn("Found project dependency `{}` on the remap source classpath; this may be an unintentional result of using `modImplementation`, etc. for a non-mod project dependency instead of `implementation`, etc.; if this is intentional, this warning may be hidden with the gradle property `{}.", id.getDisplayName(), PROPERTY_HIDE_PROJECTARTIFACT_WARNING);
                        }
                    }
                    var target = outer.getProject().getObjects().newInstance(ArtifactTarget.class);
                    target.getSource().set(artifact.getFile());
                    var targetFile = destinationDirectory.file(artifact.getFile().getName());
                    target.getTarget().set(targetFile);
                    for (var capability : artifact.getVariant().getCapabilities()) {
                        target.getCapabilities().add(capability.getGroup() + ":" + capability.getName());
                    }
                    return target;
                }).toList()
            );
            Map<String, Integer> uniqueNamesMap = new HashMap<>();
            for (var target : targets) {
                var name = target.getTarget().get().getAsFile().getName();
                var sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
                var count = uniqueNamesMap.computeIfAbsent(sanitized, k -> 0);
                uniqueNamesMap.put(sanitized, count + 1);
                target.getTarget().set(destinationDirectory.file(name.substring(0, name.lastIndexOf('.')) + "-" + count + name.substring(name.lastIndexOf('.'))));
                target.getSanitizedName().set(sanitized + "_" + count);

                target.getTarget().finalizeValueOnRead();
                target.getSource().finalizeValueOnRead();
            }
            return targets;
        });
        var property = outer.getProject().getObjects().listProperty(ArtifactTarget.class);
        property.set(targetsProvider);
        property.finalizeValueOnRead();
        destinationFiles.from(property.map(t -> t.stream().map(ArtifactTarget::getTarget).toList()));
        this.getTargets().addAll(property);

        outer.getTargets().addAll(property.map(outer.getProject().getObjects().newInstance(TargetToOutputTransformer.class)));
    }

    public void remapSingleJar(TaskGraphExecution task, Consumer<RegularFileProperty> input, Consumer<RegularFileProperty> output, Consumer<RegularFileProperty> mappings, FileCollection remappingClasspath) {
        task.dependsOn(remappingClasspath);
        var target = task.getProject().getObjects().newInstance(ArtifactTarget.class);
        target.getSanitizedName().set("remapped");
        input.accept(target.getSource());
        output.accept(target.getTarget());
        getTargets().add(target);
        var graphOutput = task.getProject().getObjects().newInstance(TaskGraphExecution.GraphOutput.class);
        graphOutput.getOutputName().set(target.getSanitizedName().map(it -> "remapMods." + it));
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

    public static abstract class TargetToOutputTransformer implements Transformer<List<TaskGraphExecution.GraphOutput>, List<ArtifactTarget>> {
        @Inject
        protected abstract ObjectFactory getObjects();

        @Override
        public List<TaskGraphExecution.GraphOutput> transform(List<ArtifactTarget> artifactTargets) {
            var list = new ArrayList<TaskGraphExecution.GraphOutput>();
            for (var target : artifactTargets) {
                var output = getObjects().newInstance(TaskGraphExecution.GraphOutput.class);
                output.getOutputName().set(target.getSanitizedName().map(it -> "remapMods." + it));
                output.getOutputFile().set(target.getTarget());
                list.add(output);
            }
            return list;
        }
    }
}
