package dev.lukebemish.crochet.tasks;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
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

    @Internal
    public abstract Property<String> getLogLevel();

    @Internal
    public abstract Property<Boolean> getShowStackTrace();

    @Inject
    public RemapJarsTask() {
        getTinyRemapperClasspath().from(getProject().getConfigurations().getByName(CrochetPlugin.TINY_REMAPPER_CONFIGURATION_NAME));

        getLogLevel().convention(switch (getProject().getGradle().getStartParameter().getLogLevel()) {
            case DEBUG -> "debug";
            case INFO, LIFECYCLE -> "info";
            case WARN -> "warn";
            case QUIET, ERROR -> "error";
        });
        getShowStackTrace().convention(getProject().getGradle().getStartParameter().getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS);
    }

    @TaskAction
    public void execute() throws IOException {
        if (getTargets().get().isEmpty()) {
            this.setDidWork(false);
            return;
        }

        for (var target : getTargets().get()) {
            var outPath = target.getTarget().get().getAsFile().toPath();
            if (Files.exists(outPath)) {
                Files.delete(outPath);
            }
        }
        getExecOperations().javaexec(spec -> {
            spec.classpath(getTinyRemapperClasspath());
            spec.getMainClass().set("dev.lukebemish.crochet.wrappers.tinyremapper.RemapMods");
            spec.systemProperty("org.slf4j.simpleLogger.defaultLogLevel", getLogLevel().get());
            spec.systemProperty("dev.lukebemish.crochet.wrappers.hidestacktrace", !getShowStackTrace().get());
            for (var target : getTargets().get()) {
                spec.args(
                    target.getSource().get().getAsFile().getAbsolutePath(),
                    target.getTarget().get().getAsFile().getAbsolutePath()
                );
            }
            spec.args("--mappings", getMappings().get().getAsFile().getAbsolutePath());
            spec.args("--classpath", getRemappingClasspath().getAsPath());
        });
    }

    public void setup(Configuration source, Configuration exclude, Directory destinationDirectory, ConfigurableFileCollection destinationFiles) {
        destinationFiles.builtBy(this);

        this.dependsOn(source);
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
