package dev.lukebemish.crochet.tasks;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public abstract class IntermediaryNeoFormConfig extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @TaskAction
    public void execute() {
        String contents = """
            {
              "spec": 4,
              "version": "${MC_VERSION}",
              "java_target": 21,
              "encoding": "UTF-8",
              "data": {
                "mappings": "mappings/mappings.tiny"
              },
              "steps": {
                "joined": [
                  {
                    "type": "downloadManifest"
                  },
                  {
                    "type": "downloadJson"
                  },
                  {
                    "type": "downloadClient"
                  },
                  {
                    "type": "downloadServer"
                  },
                  {
                    "name": "extractServer",
                    "type": "bundleExtractJar",
                    "input": "{downloadServerOutput}"
                  },
                  {
                    "type": "strip",
                    "name": "stripClient",
                    "input": "{downloadClientOutput}"
                  },
                  {
                    "type": "strip",
                    "name": "stripServer",
                    "input": "{extractServerOutput}"
                  },
                  {
                    "type": "merge",
                    "client": "{stripClientOutput}",
                    "server": "{stripServerOutput}",
                    "version": "1.21"
                  },
                  {
                    "type": "listLibraries"
                  },
                  {
                    "type": "rename",
                    "input": "{mergeOutput}",
                    "libraries": "{listLibrariesOutput}",
                    "mappings": "{mappings}"
                  },
                  {
                    "type": "downloadManifest",
                    "name": "patch"
                  }
                ],
                "client": [
                  {
                    "type": "downloadManifest"
                  },
                  {
                    "type": "downloadJson"
                  },
                  {
                    "type": "downloadClient"
                  },
                  {
                    "type": "strip",
                    "input": "{downloadClientOutput}"
                  },
                  {
                    "type": "listLibraries"
                  },
                  {
                    "type": "rename",
                    "input": "{stripOutput}",
                    "libraries": "{listLibrariesOutput}",
                    "mappings": "{mappings}"
                  },
                  {
                    "type": "downloadManifest",
                    "name": "patch"
                  }
                ],
                "server": [
                  {
                    "type": "downloadManifest"
                  },
                  {
                    "type": "downloadJson"
                  },
                  {
                    "type": "downloadServer"
                  },
                  {
                    "name": "extractServer",
                    "type": "bundleExtractJar",
                    "input": "{downloadServerOutput}"
                  },
                  {
                    "type": "strip",
                    "input": "{extractServerOutput}"
                  },
                  {
                    "type": "listLibraries",
                    "bundle": "{downloadServerOutput}"
                  },
                  {
                    "type": "rename",
                    "input": "{stripOutput}",
                    "libraries": "{listLibrariesOutput}",
                    "mappings": "{mappings}"
                  },
                  {
                    "type": "downloadManifest",
                    "name": "patch"
                  }
                ]
              },
              "functions": {
                "merge": {
                  "version": "${MERGETOOL}",
                  "args": [
                    "--client",
                    "{client}",
                    "--server",
                    "{server}",
                    "--ann",
                    "{version}",
                    "--output",
                    "{output}",
                    "--inject",
                    "false"
                  ],
                  "jvmargs": [],
                  "repo": null
                },
                "rename": {
                  "version": "${ART}",
                  "args": [
                    "--input",
                    "{input}",
                    "--output",
                    "{output}",
                    "--map",
                    "{mappings}",
                    "--cfg",
                    "{libraries}",
                    "--ann-fix",
                    "--ids-fix",
                    "--src-fix",
                    "--record-fix",
                    "--unfinal-params"
                  ],
                  "jvmargs": [],
                  "repo": null
                },
                "bundleExtractJar": {
                  "version": "${INSTALLERTOOLS}",
                  "args": [
                    "--task",
                    "bundler_extract",
                    "--input",
                    "{input}",
                    "--output",
                    "{output}",
                    "--jar-only"
                  ],
                  "jvmargs": [],
                  "repo": null
                }
              },
              "libraries": {}
            }
            """
            .replace("${MC_VERSION}", getMinecraftVersion().get())
            .replace("${MERGETOOL}", CrochetPlugin.IntermediaryNeoFormDependencies.MERGETOOL)
            .replace("${ART}", CrochetPlugin.IntermediaryNeoFormDependencies.ART)
            .replace("${INSTALLERTOOLS}", CrochetPlugin.IntermediaryNeoFormDependencies.INSTALLERTOOLS);;
        getOutputFile().get().getAsFile().getParentFile().mkdirs();
        try (var writer = new FileWriter(getOutputFile().get().getAsFile(), StandardCharsets.UTF_8)) {
            writer.write(contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
