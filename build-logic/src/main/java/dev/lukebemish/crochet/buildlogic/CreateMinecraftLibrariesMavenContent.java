package dev.lukebemish.crochet.buildlogic;

import groovy.json.JsonSlurper;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class CreateMinecraftLibrariesMavenContent extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getClassName();

    @Internal
    public abstract DirectoryProperty getOutputDir();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getVersions();

    private record Module(String group, String name) {}

    @Inject
    public CreateMinecraftLibrariesMavenContent() {
        getOutputFile().convention(getOutputDir().zip(getClassName(), (dir, clazz) -> dir.file(clazz.replace('.', '/') + ".java")));
    }

    @TaskAction
    public void run() {
        var modules = new LinkedHashSet<Module>();
        var json = new JsonSlurper();
        var sourceFile = getOutputFile().getAsFile().get();
        sourceFile.getParentFile().mkdirs();
        if (sourceFile.exists()) {
            sourceFile.delete();
        }
        for (var versionFile : getVersions().getFiles()) {
            var versionData = (Map<?, ?>) json.parse(versionFile);
            for (var library : (List<?>) versionData.get("libraries")) {
                var gav = ((String) ((Map<?, ?>) library).get("name")).split(":");
                modules.add(new Module(gav[0], gav[1]));
            }
        }
        var classNameParts = getClassName().get().split("\\.");
        var packageNameParts = new String[classNameParts.length - 1];
        System.arraycopy(classNameParts, 0, packageNameParts, 0, classNameParts.length - 1);
        var packageName = String.join(".", packageNameParts);
        var text = String.format("""
            package %s;

            import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;

            class %s {
                static void applyContent(RepositoryContentDescriptor descriptor) {
            %s
                    descriptor.includeGroupAndSubgroups("com.mojang");
                }
            }
            """, packageName, classNameParts[classNameParts.length - 1], modules.stream().map(module -> String.format(
                "        descriptor.includeModule(\"%s\", \"%s\");", module.group(), module.name()
            )).collect(Collectors.joining("\n"))
        );
        try (var writer = new OutputStreamWriter(new FileOutputStream(sourceFile), StandardCharsets.UTF_8)) {
            writer.write(text);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        sourceFile.setReadOnly();
    }
}
