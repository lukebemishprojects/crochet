package dev.lukebemish.crochet.internal.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lukebemish.crochet.internal.TaskGraphRunnerService;
import dev.lukebemish.crochet.internal.Unit;
import dev.lukebemish.taskgraphrunner.daemon.DaemonExecutor;
import dev.lukebemish.taskgraphrunner.model.Config;
import dev.lukebemish.taskgraphrunner.model.Output;
import dev.lukebemish.taskgraphrunner.model.WorkItem;
import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public abstract class TaskGraphExecution extends DefaultTask {
    private static final Gson GSON = new GsonBuilder().create();

    public interface ConfigMaker {
        Config makeConfig() throws IOException;
    }

    @Inject
    public TaskGraphExecution() {
        getRuntimeCacheDirectory().set(
            new File(getProject().getGradle().getGradleUserHomeDir(), "caches/taskgraphrunner")
        );

        getTaskRecordJson().convention(getProject().getLayout().dir(getProject().provider(this::getTemporaryDir)).map(d -> d.file("task-record.json")));

        // Default to java 21 for NFRT
        getJavaLauncher().convention(getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));

        this.getOutputs().upToDateWhen(task -> {
            var taskRecordJson = getTaskRecordJson().get().getAsFile();
            if (taskRecordJson.exists()) {
                // Start the daemon
                makeDaemon();
                getTaskGraphRunnerService().get().addTaskRecordJson(getRuntimeCacheDirectory().get().getAsFile().toPath().toAbsolutePath(), taskRecordJson.toPath().toAbsolutePath());
            }
            return true;
        });
    }

    private DaemonExecutor makeDaemon() {
        return getTaskGraphRunnerService().get().start(getJavaLauncher().get(), getClasspath().getSingleFile().getAbsolutePath());
    }

    @Nested
    public abstract Property<ConfigMaker> getConfigMaker();

    @ServiceReference("taskGraphRunnerDaemon")
    protected abstract Property<TaskGraphRunnerService> getTaskGraphRunnerService();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ListProperty<RegularFile> getArtifactFiles();

    @Input
    public abstract ListProperty<String> getArtifactIdentifiers();

    @Nested
    public abstract ListProperty<GraphOutput> getTargets();

    @Internal
    protected abstract RegularFileProperty getTaskRecordJson();

    public static abstract class GraphOutput {
        @Input
        public abstract Property<String> getOutputName();
        @OutputFile
        public abstract RegularFileProperty getOutputFile();

        public static GraphOutput of(String outputName, Provider<RegularFile> outputFile, ObjectFactory factory) {
            var instance = factory.newInstance(GraphOutput.class);
            instance.getOutputName().set(outputName);
            instance.getOutputFile().set(outputFile);
            return instance;
        }
    }

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Internal
    public abstract DirectoryProperty getRuntimeCacheDirectory();

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    public void copyArtifactsFrom(TaskGraphExecution other) {
        getArtifactFiles().set(other.getArtifactFiles());
        getArtifactIdentifiers().set(other.getArtifactIdentifiers());
    }

    public void copyConfigFrom(TaskGraphExecution other) {
        getConfigMaker().set(other.getConfigMaker());
        getArtifactFiles().set(other.getArtifactFiles());
        getArtifactIdentifiers().set(other.getArtifactIdentifiers());
        getJavaLauncher().set(other.getJavaLauncher());
        getRuntimeCacheDirectory().set(other.getRuntimeCacheDirectory());
        getClasspath().setFrom(other.getClasspath());
    }

    @TaskAction
    public void execute() throws IOException {
        var config = getConfigMaker().get().makeConfig();
        var workItem = new WorkItem();
        getTargets().get().forEach(graphOutput -> {
            var file = graphOutput.getOutputFile().get().getAsFile().toPath();
            var target = graphOutput.getOutputName().get();
            var parts = target.split("\\.", 2);
            workItem.results.put(
                parts.length == 2 ? new WorkItem.Target.OutputTarget(new Output(parts[0], parts[1])) : new WorkItem.Target.AliasTarget(target),
                file
            );
        });
        config.workItems.add(workItem);
        var configPath = getTemporaryDir().toPath().resolve("config.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, GSON.toJson(config));

        Properties properties = new Properties();
        List<RegularFile> files = getArtifactFiles().get();
        List<String> identifiers = getArtifactIdentifiers().get();
        for (int i = 0; i < files.size(); i++) {
            var file = files.get(i);
            var artifactIdentifier = identifiers.get(i);
            if (!artifactIdentifier.isBlank()) {
                properties.setProperty(artifactIdentifier, file.getAsFile().getAbsolutePath());
            }
        }
        var manifest = getTemporaryDir().toPath().resolve("artifact-manifest.properties");
        try (var writer = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
            properties.store(writer, "Generated by Crochet");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var daemon = makeDaemon();
        getTaskGraphRunnerService().get().addCacheDir(getRuntimeCacheDirectory().get().getAsFile().toPath().toAbsolutePath());

        List<String> arguments = new ArrayList<>();

        arguments.add("--cache-dir="+getRuntimeCacheDirectory().get().getAsFile().getAbsolutePath());
        arguments.add("--artifact-manifest="+manifest.toAbsolutePath());
        arguments.add("run");
        arguments.add(configPath.toAbsolutePath().toString());
        arguments.add("--task-record-json="+getTaskRecordJson().get().getAsFile().getAbsolutePath());

        daemon.execute(arguments.toArray(String[]::new));
    }

    public void artifactsConfiguration(Configuration configuration) {
        Provider<Set<ResolvedArtifactResult>> artifacts = configuration.getIncoming().getArtifacts().getResolvedArtifacts();

        getArtifactFiles().addAll(artifacts.map(getProject().getObjects().newInstance(FileExtractor.class, Unit.provider(getProject()))));
        getArtifactIdentifiers().addAll(artifacts.map(new IdExtractor()));
    }

    public void singleFileConfiguration(String identifier, Configuration configuration, Provider<Boolean> provider) {
        Provider<Set<ResolvedArtifactResult>> artifacts = provider.orElse(true).zip(getProject().provider(() -> configuration.getIncoming().getArtifacts().getResolvedArtifacts().map(s -> {
            if (s.size() != 1) {
                throw new IllegalStateException("Expected exactly one artifact for " + identifier + " but got " + s.size());
            }
            return s;
        })), (b, pr) -> {
            if (b) {
                return Optional.of(pr);
            } else {
                return Optional.<Provider<Set<ResolvedArtifactResult>>>empty();
            }
        }).zip(getProject().provider(() -> Unit.provider(getProject())), (o, pr) ->
            o.orElse(pr.map(ignored -> new HashSet<>()))
        ).flatMap(p -> p);

        var artifactProperty = getProject().getObjects().setProperty(ResolvedArtifactResult.class);
        artifactProperty.set(artifacts);

        getArtifactFiles().addAll(artifactProperty.map(getProject().getObjects().newInstance(FileExtractor.class, Unit.provider(getProject()))));
        getArtifactIdentifiers().addAll(artifactProperty.<List<String>>map(a -> new ArrayList<>(a.stream().map(ignored -> identifier).toList())));
    }

    public void singleFileConfiguration(String identifier, Configuration configuration) {
        singleFileConfiguration(identifier, configuration, getProject().provider(() -> true));
    }

    public abstract static class FileExtractor implements Transformer<List<RegularFile>, Collection<ResolvedArtifactResult>> {
        private final Provider<Unit> unitProvider;

        @Inject
        public FileExtractor(Provider<Unit> unitProvider) {
            this.unitProvider = unitProvider;
        }

        @Inject
        protected abstract ProjectLayout getProjectLayout();

        @Override
        public List<RegularFile> transform(Collection<ResolvedArtifactResult> artifacts) {
            return artifacts.stream().map(r -> getProjectLayout().file(unitProvider.map(ignored -> r.getFile().getAbsoluteFile())).get()).collect(Collectors.toList());
        }
    }

    public static class IdExtractor implements Transformer<List<String>, Collection<ResolvedArtifactResult>> {
        @Override
        public List<String> transform(Collection<ResolvedArtifactResult> artifacts) {
            return artifacts.stream().map(resolvedArtifactResult -> {
                var variant = resolvedArtifactResult.getVariant();
                while (variant.getExternalVariant().isPresent()) {
                    variant = variant.getExternalVariant().get();
                }
                var file = resolvedArtifactResult.getFile();
                var identifier = variant.getOwner();
                if (identifier instanceof ModuleComponentIdentifier moduleId) {
                    var key = moduleId.getGroup() + ":" + moduleId.getModule() + ":" + moduleId.getVersion();
                    var name = file.getName();
                    var rest = name.substring(0, name.lastIndexOf('.'));
                    var forClassifier = moduleId.getModule() + "-" + moduleId.getVersion() + "-";
                    if (rest.startsWith(forClassifier)) {
                        key = key + ":" + rest.substring(forClassifier.length());
                    }
                    var extension = name.substring(name.lastIndexOf('.') + 1);
                    if (!extension.equals("jar")) {
                        key = key + "@" + extension;
                    }
                    return key;
                } else {
                    if (variant.getCapabilities().size() == 1) {
                        var capability = variant.getCapabilities().getFirst();
                        return capability.getGroup() + ":" + capability.getName() + ":" + capability.getVersion();
                    }
                }
                return "";
            }).collect(Collectors.toList());
        }
    }
}
