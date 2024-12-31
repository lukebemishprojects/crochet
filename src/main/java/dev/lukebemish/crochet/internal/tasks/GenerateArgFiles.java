package dev.lukebemish.crochet.internal.tasks;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public abstract class GenerateArgFiles extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getArgFile();

    @OutputFile
    public abstract RegularFileProperty getJvmArgFile();

    @Input
    public abstract ListProperty<String> getJvmArgs();

    @Input
    public abstract ListProperty<String> getArgs();

    @Input
    public abstract Property<String> getMainClass();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    public abstract RegularFileProperty getNeoFormConfig();

    @Input
    @Optional
    public abstract Property<String> getMinecraftVersion();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAssetsProperties();

    @Input
    public abstract Property<String> getRunDirectory();

    @Inject
    public GenerateArgFiles() {
        this.getOutputs().upToDateWhen(t -> {
            var task = (GenerateArgFiles) t;
            return new File(task.getRunDirectory().get()).exists();
        });
    }

    @TaskAction
    public void generate() {
        new File(getRunDirectory().get()).mkdirs();
        getArgFile().get().getAsFile().getParentFile().mkdirs();
        Properties assetsProperties = new Properties();
        try (var argsWriter = new OutputStreamWriter(new FileOutputStream(getArgFile().get().getAsFile()));
             var jvmArgsWriter = new OutputStreamWriter(new FileOutputStream(getJvmArgFile().get().getAsFile()));
             var assetsPropertiesStream = new FileInputStream(getAssetsProperties().get().getAsFile())) {
            String minecraftVersion;
            if (getNeoFormConfig().isPresent()) {
                var neoFormConfigReader = new InputStreamReader(new FileInputStream(getNeoFormConfig().get().getAsFile()), StandardCharsets.UTF_8);
                JsonObject neoFormConfig = new Gson().fromJson(neoFormConfigReader, JsonObject.class);
                minecraftVersion = neoFormConfig.getAsJsonPrimitive("version").getAsString();
            } else {
                minecraftVersion = getMinecraftVersion().get();
            }
            assetsProperties.load(assetsPropertiesStream);
            List<String> argsLines = new ArrayList<>();
            argsLines.add(escapeArgument(getMainClass().get(), assetsProperties, minecraftVersion));
            for (var arg : getArgs().get()) {
                argsLines.add(escapeArgument(arg, assetsProperties, minecraftVersion));
            }
            argsWriter.write(String.join("\n", argsLines));

            List<String> jvmArgsLines = new ArrayList<>();
            for (var jvmArg : getJvmArgs().get()) {
                jvmArgsLines.add(escapeArgument(jvmArg, assetsProperties, minecraftVersion));
            }
            jvmArgsWriter.write(String.join("\n", jvmArgsLines));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write arg file", e);
        }
    }

    private String escapeArgument(String argument, Properties assetProperties, String minecraftVersion) {
        argument = argument.replace("\\", "\\\\");
        argument = argument.replace("\"", "\\\"");
        argument = argument.replace("${assets_root}", Objects.requireNonNull(assetProperties.getProperty("assets_root")));
        argument = argument.replace("${assets_index_name}", Objects.requireNonNull(assetProperties.getProperty("asset_index")));
        argument = argument.replace("${minecraft_version}", Objects.requireNonNull(minecraftVersion));
        if (argument.contains(" ")) {
            argument = "\"" + argument + "\"";
        }
        return argument;
    }
}
