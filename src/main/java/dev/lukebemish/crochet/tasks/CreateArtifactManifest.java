package dev.lukebemish.crochet.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class CreateArtifactManifest extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getArtifacts();

    @Input
    public abstract ListProperty<String> getArtifactFiles();

    @Input
    public abstract ListProperty<String> getArtifactIdentifiers();

    @TaskAction
    public void execute() {
        Properties properties = new Properties();
        List<String> files = getArtifactFiles().get();
        List<String> identifiers = getArtifactIdentifiers().get();
        for (int i = 0; i < files.size(); i++) {
            var file = files.get(i);
            var artifactIdentifier = identifiers.get(i);
            if (!artifactIdentifier.isBlank()) {
                properties.setProperty(artifactIdentifier, file);
            }
        }
        getOutputFile().get().getAsFile().getParentFile().mkdirs();
        try (var writer = new FileWriter(getOutputFile().get().getAsFile(), StandardCharsets.ISO_8859_1)) {
            properties.store(writer, "Generated by Crochet");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void configuration(Configuration configuration) {
        Provider<Set<ResolvedArtifactResult>> artifacts = configuration.getIncoming().getArtifacts().getResolvedArtifacts();

        getArtifactFiles().addAll(artifacts.map(new FileExtractor()));
        getArtifactIdentifiers().addAll(artifacts.map(new IdExtractor()));
    }

    public static class FileExtractor implements Transformer<List<String>, Collection<ResolvedArtifactResult>> {
        @Override
        public List<String> transform(Collection<ResolvedArtifactResult> artifacts) {
            return artifacts.stream().map(r -> r.getFile().getAbsolutePath()).collect(Collectors.toList());
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