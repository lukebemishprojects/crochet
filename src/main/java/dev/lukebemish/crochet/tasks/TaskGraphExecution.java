package dev.lukebemish.crochet.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lukebemish.crochet.internal.PropertiesUtils;
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
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

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

        // Default to java 21 for NFRT
        getJavaLauncher().convention(getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
    }

    @Nested
    public abstract Property<ConfigMaker> getConfigMaker();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ListProperty<RegularFile> getArtifactFiles();

    @Input
    public abstract ListProperty<String> getArtifactIdentifiers();

    @Nested
    public abstract ListProperty<GraphOutput> getTargets();

    public static abstract class GraphOutput {
        @Input
        abstract Property<String> getOutputName();
        @OutputFile
        abstract RegularFileProperty getOutputFile();

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

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

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

        List<String> arguments = new ArrayList<>();

        arguments.add("--cache-dir="+getRuntimeCacheDirectory().get().getAsFile().getAbsolutePath());
        arguments.add("--artifact-manifest="+manifest.toAbsolutePath());
        arguments.add("run");
        arguments.add(configPath.toAbsolutePath().toString());

        getExecOperations().javaexec(execSpec -> {
            // Network properties should be the same as TaskGraphRunner may be doing network stuff
            execSpec.systemProperties(PropertiesUtils.networkProperties(getProviderFactory()).get());

            // See https://github.com/gradle/gradle/issues/28959
            execSpec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");

            execSpec.executable(getJavaLauncher().get().getExecutablePath().getAsFile());
            execSpec.classpath(getClasspath());
            execSpec.args(arguments);
        }).rethrowFailure().assertNormalExitValue();
    }

    public void artifactsConfiguration(Configuration configuration) {
        Provider<Set<ResolvedArtifactResult>> artifacts = configuration.getIncoming().getArtifacts().getResolvedArtifacts();

        getArtifactFiles().addAll(artifacts.map(new FileExtractor()));
        getArtifactIdentifiers().addAll(artifacts.map(new IdExtractor()));
    }

    public class FileExtractor implements Transformer<List<RegularFile>, Collection<ResolvedArtifactResult>> {
        @Override
        public List<RegularFile> transform(Collection<ResolvedArtifactResult> artifacts) {
            return artifacts.stream().map(r -> getProjectLayout().file(getProject().provider(() -> r.getFile().getAbsoluteFile())).get()).collect(Collectors.toList());
        }
    }

    public static class IdExtractor implements Transformer<List<String>, Collection<ResolvedArtifactResult>> {
        @Override
        public List<String> transform(Collection<ResolvedArtifactResult> artifacts) {
            return artifacts.stream().map(resolvedArtifactResult -> {
                var file = resolvedArtifactResult.getFile();
                var artifactIdentifier = resolvedArtifactResult.getId();
                var identifier = artifactIdentifier.getComponentIdentifier();
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
                }
                return "";
            }).collect(Collectors.toList());
        }
    }
}
