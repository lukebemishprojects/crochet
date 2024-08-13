package dev.lukebemish.crochet.tasks;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public abstract class RemapJarsTask extends DefaultTask {
    public abstract static class Target {
        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        public abstract RegularFileProperty getSource();

        @OutputFile
        public abstract RegularFileProperty getTarget();
    }

    @Nested
    public abstract ListProperty<Target> getTargets();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getTinyRemapperClasspath();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getRemappingClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getMappings();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    public RemapJarsTask() {
        getTinyRemapperClasspath().from(getProject().getConfigurations().getByName(CrochetPlugin.TINY_REMAPPER_CONFIGURATION_NAME));
    }

    @TaskAction
    public void execute() {
        getExecOperations().javaexec(spec -> {
            spec.classpath(getTinyRemapperClasspath());
            spec.getMainClass().set("dev.lukebemish.crochet.wrappers.tinyremapper.Main");
            for (var target : getTargets().get()) {
                spec.args(
                    target.getSource().get().getAsFile().getAbsolutePath(),
                    target.getTarget().get().getAsFile().getAbsolutePath()
                );
            }
            spec.args("--mappings", getMappings().get().getAsFile().getAbsolutePath());
            spec.args("--classpath", getRemappingClasspath().getAsPath());
        });

        // TODO: strip nested jars
    }

    public void setup(Configuration source, Configuration exclude, Directory destinationDirectory, ConfigurableFileCollection destinationFiles) {
        destinationFiles.builtBy(this);
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
                    var target = getProject().getObjects().newInstance(Target.class);
                    target.getSource().set(artifact.getFile());
                    var targetFile = destinationDirectory.file(artifact.getFile().getName());
                    target.getTarget().set(targetFile);
                    return target;
                }).toList()
            );
            Map<String, Integer> uniqueNamesMap = new HashMap<>();
            for (var target : targets) {
                var name = target.getTarget().get().getAsFile().getName();
                var count = uniqueNamesMap.computeIfAbsent(name, k -> 0);
                uniqueNamesMap.put(name, count + 1);
                target.getTarget().set(destinationDirectory.file(name.substring(0, name.lastIndexOf('.')) + "-" + count + name.substring(name.lastIndexOf('.'))));

                target.getTarget().finalizeValueOnRead();
                target.getSource().finalizeValueOnRead();
            }
            return targets;
        });
        destinationFiles.from(targetsProvider.map(t -> t.stream().map(Target::getTarget).toList()));
        this.getTargets().addAll(targetsProvider);
    }
}
