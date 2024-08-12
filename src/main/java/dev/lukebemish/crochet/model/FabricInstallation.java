package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.Log4jSetup;
import dev.lukebemish.crochet.tasks.ExtractConfigTask;
import dev.lukebemish.crochet.tasks.IntermediaryNeoFormConfig;
import dev.lukebemish.crochet.tasks.WriteFile;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class FabricInstallation extends AbstractVanillaInstallation {
    final Configuration loaderConfiguration;
    final TaskProvider<WriteFile> writeLog4jConfig;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public FabricInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.writeLog4jConfig = project.getTasks().register("writeCrochet"+StringUtils.capitalize(name)+"Log4jConfig", WriteFile.class, task -> {
            task.getContents().convention(
                Log4jSetup.FABRIC_CONFIG
            );
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml"));
        });

        var mappings = workingDirectory.map(dir -> dir.file("mappings.txt"));

        this.artifactsTask.configure(task -> {
            // TODO: needs a NFRT update
            //task.getCustomResults().put("officialMappings", new AbstractRuntimeArtifactsTask.StepOutput("downloadClientMappings"));
            //task.getTargets().put("officialMappings", mappings);
            task.getOutputs().file(mappings);
        });

        this.loaderConfiguration = project.getConfigurations().maybeCreate(getName()+"FabricLoader");
        this.loaderConfiguration.fromDependencyCollector(getDependencies().getLoader());

        this.getMinecraftVersion().convention(getNeoFormModule().map(module -> {
            var parts = module.split("[:@]");
            if (parts.length >= 3) {
                var nf = parts[2];
                return nf.substring(0, nf.lastIndexOf('-'));
            }
            throw new IllegalArgumentException("Cannot extract minecraft version from `"+module+"` -- you may need to manually specify it");
        }));
        this.getDependencies().getIntermediary().add(project.provider(() ->
            this.getDependencies().module("net.fabricmc", "intermediary", this.getMinecraftVersion().get()))
        );
        var intermediaryConfiguration = project.getConfigurations().maybeCreate(getName()+"Intermediary");
        intermediaryConfiguration.fromDependencyCollector(getDependencies().getIntermediary());
        var neoFormConfig = project.getTasks().register("crochetCreate"+StringUtils.capitalize(name)+"IntermediaryNFConfig", IntermediaryNeoFormConfig.class, task -> {
            task.getOutputFile().set(workingDirectory.get().file("intermediary-neoform-config.json"));
            task.getMinecraftVersion().set(this.getMinecraftVersion());
        });
        var neoFormZip = project.getTasks().register("crochetCreate"+StringUtils.capitalize(name)+"IntermediaryNeoFormZip", Zip.class, task -> {
            task.getDestinationDirectory().set(workingDirectory);
            task.getArchiveFileName().set("intermediary-neoform-config.zip");
            task.from(neoFormConfig.get().getOutputFile(), spec -> spec.rename("intermediary-neoform-config.json", "config.json"));
            task.from(project.zipTree(intermediaryConfiguration.getSingleFile()), spec -> spec.exclude("META-INF"));
        });

        var intemediaryNeoFormModule = getMinecraftVersion().map(v -> "dev.lukebemish.crochet.internal:intermediary-neoform:"+v+"+crochet."+CrochetPlugin.VERSION+"@zip");

        createArtifactManifestTask.configure(task -> {
            task.getArtifactIdentifiers().add(intemediaryNeoFormModule);
            task.getArtifactFiles().add(neoFormZip.map(t -> t.getArchiveFile().get().getAsFile().getAbsolutePath()));
            task.configuration(project.getConfigurations().getByName(CrochetPlugin.INTERMEDIARY_NEOFORM_DEPENDENCIES_CONFIGURATION_NAME));
        });
    }

    public abstract Property<String> getMinecraftVersion();

    @Override
    public void forSourceSet(SourceSet sourceSet) {
        super.forSourceSet(sourceSet);
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(loaderConfiguration);
        });
    }

    @Override
    @Nested
    public abstract FabricInstallationDependencies getDependencies();

    public void dependencies(Action<FabricInstallationDependencies> action) {
        action.execute(getDependencies());
    }

    @Override
    protected void forRun(Run run, RunType runType) {
        run.argFilesTask.configure(task -> {
            task.dependsOn(extractConfig);
            task.getNeoFormConfig().set(extractConfig.flatMap(ExtractConfigTask::getNeoFormConfig));
            task.dependsOn(writeLog4jConfig);
        });
        Configuration runtimeClasspath = project.getConfigurations().maybeCreate("crochet" + StringUtils.capitalize(this.getName()) + StringUtils.capitalize(run.getName()) + "Classpath");
        runtimeClasspath.extendsFrom(loaderConfiguration);
        runtimeClasspath.extendsFrom(project.getConfigurations().getByName(CrochetPlugin.TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME));
        runtimeClasspath.attributes(attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            // We just default to 21 here if nothing is specified -- we'll want to be smarter about this in the future
            // and try and pull it from the source compile tasks I guess?
            attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, run.getToolchain().getLanguageVersion().map(JavaLanguageVersion::asInt).orElse(21));
        });
        run.classpath.extendsFrom(runtimeClasspath);
        run.getJvmArgs().addAll(
            "-Dfabric.development=true",
            // TODO: remap classpath file
            "-Dlog4j.configurationFile="+project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml").get().getAsFile().getAbsolutePath(),
            "-Dfabric.log.disableAnsi=false",
            "-Dfabric.gameVersion=${minecraft_version}"
        );
        run.getJvmArgs().add(project.provider(() -> {
            List<String> groups = new ArrayList<>();

            groups.add(
                (Boolean.getBoolean("idea.active") ? artifactsTask.get().getSourcesAndCompiled() : artifactsTask.get().getCompiled()).getAsFile().get().getAbsolutePath()
                    + File.pathSeparator
                    + artifactsTask.get().getClientResources().getAsFile().get().getAbsolutePath()
            );

            run.getRunMods().get().forEach(mod -> {
                groups.add(mod.components.getAsPath());
            });

            return "-Dfabric.classPathGroups=" + String.join(File.pathSeparator+File.pathSeparator, groups);
        }));
        switch (runType) {
            case CLIENT -> {
                runtimeClasspath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client"));
                runtimeClasspath.extendsFrom(clientMinecraft.get());
                run.getMainClass().convention("net.fabricmc.loader.impl.launch.knot.KnotClient");
                run.getArgs().addAll(
                    "--assetIndex",
                    "${assets_index_name}",
                    "--assetsDir",
                    "${assets_root}"
                );
            }
            case SERVER -> {
                runtimeClasspath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server"));
                runtimeClasspath.extendsFrom(serverMinecraft.get());
                run.getMainClass().convention("net.fabricmc.loader.impl.launch.knot.KnotClient");
            }
        }
    }
}
