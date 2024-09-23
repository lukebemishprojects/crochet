package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.Log4jSetup;
import dev.lukebemish.crochet.mappings.ChainedMappingsSource;
import dev.lukebemish.crochet.mappings.FileMappingSource;
import dev.lukebemish.crochet.mappings.ReversedMappingsSource;
import dev.lukebemish.crochet.tasks.FabricInstallationArtifacts;
import dev.lukebemish.crochet.tasks.MakeRemapClasspathFile;
import dev.lukebemish.crochet.tasks.MappingsWriter;
import dev.lukebemish.crochet.tasks.RemapJarsTask;
import dev.lukebemish.crochet.tasks.TaskGraphExecution;
import dev.lukebemish.crochet.tasks.WriteFile;
import net.neoforged.srgutils.IMappingFile;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public abstract class FabricInstallation extends AbstractVanillaInstallation {
    final Configuration loaderConfiguration;
    final Configuration intermediaryMinecraft;
    final Configuration remapClasspathConfiguration;
    final Configuration mappingsClasspath;
    final TaskProvider<WriteFile> writeLog4jConfig;
    final FabricInstallationArtifacts fabricConfigMaker;

    private final CrochetExtension extension;
    private final TaskProvider<MappingsWriter> intermediaryToNamed;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public FabricInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.extension = extension;

        this.writeLog4jConfig = project.getTasks().register("writeCrochet"+StringUtils.capitalize(name)+"Log4jConfig", WriteFile.class, task -> {
            task.getContents().convention(
                Log4jSetup.FABRIC_CONFIG
            );
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml"));
        });

        var mappings = workingDirectory.map(dir -> dir.file("mappings.txt"));

        this.fabricConfigMaker = project.getObjects().newInstance(FabricInstallationArtifacts.class);
        fabricConfigMaker.getWrapped().set(vanillaConfigMaker);

        this.artifactsTask.configure(task -> {
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("downloadClientMappings.output", mappings, project.getObjects()));
            task.getConfigMaker().set(fabricConfigMaker);
        });

        this.remapClasspathConfiguration = project.getConfigurations().maybeCreate("crochet"+StringUtils.capitalize(getName())+"RemapClasspath");

        this.loaderConfiguration = project.getConfigurations().maybeCreate(getName()+"FabricLoader");
        this.loaderConfiguration.fromDependencyCollector(getDependencies().getLoader());

        this.getDependencies().getIntermediary().add(project.provider(() ->
            this.getDependencies().module("net.fabricmc", "intermediary", this.getMinecraft().get()))
        );
        var intermediaryConfiguration = project.getConfigurations().maybeCreate(getName()+"Intermediary");
        intermediaryConfiguration.fromDependencyCollector(getDependencies().getIntermediary());
        var intermediaryMappings = project.getTasks().register("crochetExtract"+StringUtils.capitalize(name)+"IntermediaryMappings", Copy.class, task -> {
            task.from(project.zipTree(intermediaryConfiguration.getSingleFile()));
            task.setDestinationDir(workingDirectory.get().dir("intermediary").getAsFile());
        });

        fabricConfigMaker.getIntermediary().fileProvider(intermediaryMappings.map(t -> t.getDestinationDir().toPath().resolve("mappings").resolve("mappings.tiny").toFile()));
        this.artifactsTask.configure(task -> {
            task.dependsOn(intermediaryMappings);
        });

        var intermediaryJar = workingDirectory.map(it -> it.file("intermediary.jar"));
        this.artifactsTask.configure(task -> {
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("intermediary", intermediaryJar, project.getObjects()));
        });

        // To remap dependencies, we need a intermediary -> mojmaps + srg mapping.
        // We have intermediary -> official from intermediary, and official -> srg + mojmaps from neoform
        var objects = project.getObjects();

        var intermediary = objects.newInstance(FileMappingSource.class);
        intermediary.getMappingsFile().set(workingDirectory.map(dir -> dir.file("intermediary/mappings/mappings.tiny")));
        var intermediaryReversed = objects.newInstance(ReversedMappingsSource.class);
        intermediaryReversed.getInputMappings().set(intermediary);
        var named = objects.newInstance(FileMappingSource.class);
        named.getMappingsFile().set(mappings);
        var namedReversed = objects.newInstance(ReversedMappingsSource.class);
        namedReversed.getInputMappings().set(named);
        var chainedSpec = objects.newInstance(ChainedMappingsSource.class);
        chainedSpec.getInputSources().set(List.of(intermediaryReversed, namedReversed));

        this.intermediaryToNamed = project.getTasks().register("crochet"+StringUtils.capitalize(name)+"IntermediaryToNamed", MappingsWriter.class, task -> {
            task.getInputMappings().set(chainedSpec);
            task.dependsOn(intermediaryMappings);
            task.dependsOn(this.artifactsTask);
            task.getTargetFormat().set(IMappingFile.Format.TINY);
            task.getOutputMappings().set(workingDirectory.map(dir -> dir.file("intermediary-to-named.tiny")));
            // Ensure that the header is nicely present for loader
            task.doLast(t -> {
                var mappingWriter = (MappingsWriter) t;
                var file = mappingWriter.getOutputMappings().getAsFile().get().toPath();
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    var firstLine = text.indexOf('\n');
                    var newHeader = "tiny\t2\t0\tintermediary\tnamed";
                    var newText = newHeader + text.substring(firstLine);
                    Files.writeString(file, newText, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        });

        this.mappingsClasspath = project.getConfigurations().maybeCreate("crochet"+StringUtils.capitalize(getName())+"MappingsClasspath");
        var mappingsJar = project.getTasks().register("crochet"+StringUtils.capitalize(getName())+"MappingsJar", Jar.class, task -> {
            task.getDestinationDirectory().set(workingDirectory);
            task.getArchiveFileName().set("intermediary-mappings.jar");
            task.from(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings), spec -> {
                spec.into("mappings");
                spec.rename(s -> "mappings.tiny");
            });
            task.dependsOn(intermediaryToNamed);
        });
        var mappingsJarFiles = project.files(mappingsJar.map(Jar::getArchiveFile));
        mappingsJarFiles.builtBy(mappingsJar);
        project.getDependencies().add(mappingsClasspath.getName(), mappingsJarFiles);

        this.intermediaryMinecraft = project.getConfigurations().maybeCreate("crochet"+StringUtils.capitalize(getName())+"IntermediaryMinecraft");

        var intermediaryJarFiles = project.files();
        intermediaryJarFiles.from(intermediaryJar);
        intermediaryJarFiles.builtBy(this.artifactsTask);
        project.getDependencies().add(intermediaryMinecraft.getName(), intermediaryJarFiles);
    }

    @Override
    public void forSourceSet(SourceSet sourceSet) {
        this.forSourceSet(sourceSet, deps -> {});
    }

    @SuppressWarnings("UnstableApiUsage")
    public void forSourceSet(SourceSet sourceSet, Action<FabricSourceSetDependencies> action) {
        super.forSourceSet(sourceSet);
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(loaderConfiguration);
        });

        var dependencies = project.getObjects().newInstance(FabricSourceSetDependencies.class);
        action.execute(dependencies);

        var modCompileClasspath = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "compileClasspath"));
        var modRuntimeClasspath = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "runtimeClasspath"));
        var runtimeElements = project.getConfigurations().maybeCreate(sourceSet.getRuntimeElementsConfigurationName());
        var apiElements = project.getConfigurations().maybeCreate(sourceSet.getApiElementsConfigurationName());

        var modCompileOnly = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "compileOnly"));
        modCompileOnly.fromDependencyCollector(dependencies.getModCompileOnly());
        modCompileClasspath.extendsFrom(modCompileOnly);
        var modCompileOnlyApi = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "compileOnlyApi"));
        modCompileOnlyApi.fromDependencyCollector(dependencies.getModCompileOnlyApi());
        modCompileClasspath.extendsFrom(modCompileOnlyApi);
        apiElements.extendsFrom(modCompileOnlyApi);
        var modRuntimeOnly = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "runtimeOnly"));
        modRuntimeOnly.fromDependencyCollector(dependencies.getModRuntimeOnly());
        modRuntimeClasspath.extendsFrom(modRuntimeOnly);
        runtimeElements.extendsFrom(modRuntimeOnly);
        var modLocalRuntime = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "localRuntime"));
        modLocalRuntime.fromDependencyCollector(dependencies.getModLocalRuntime());
        modRuntimeClasspath.extendsFrom(modLocalRuntime);
        var modImplementation = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "implementation"));
        modImplementation.fromDependencyCollector(dependencies.getModImplementation());
        modCompileClasspath.extendsFrom(modImplementation);
        modRuntimeClasspath.extendsFrom(modImplementation);
        runtimeElements.extendsFrom(modImplementation);
        var modApi = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "api"));
        modApi.fromDependencyCollector(dependencies.getModApi());
        modCompileClasspath.extendsFrom(modApi);
        modRuntimeClasspath.extendsFrom(modApi);
        runtimeElements.extendsFrom(modApi);
        apiElements.extendsFrom(modApi);

        var remappedCompileClasspath = project.getConfigurations().maybeCreate(sourceSet.getTaskName("remapped", "compileClasspath"));
        project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(remappedCompileClasspath);
        var remappedRuntimeClasspath = project.getConfigurations().maybeCreate(sourceSet.getTaskName("remapped", "runtimeClasspath"));
        project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName()).extendsFrom(remappedRuntimeClasspath);

        var compileRemappingClasspath = project.getConfigurations().maybeCreate(sourceSet.getTaskName("crochetRemapping", "compileClasspath"));
        var runtimeRemappingClasspath = project.getConfigurations().maybeCreate(sourceSet.getTaskName("crochetRemapping", "runtimeClasspath"));

        project.getDependencies().add(remapClasspathConfiguration.getName(), project.files(compileRemappingClasspath));
        project.getDependencies().add(remapClasspathConfiguration.getName(), project.files(runtimeRemappingClasspath));

        compileRemappingClasspath.extendsFrom(modCompileClasspath);
        compileRemappingClasspath.extendsFrom(intermediaryMinecraft);
        compileRemappingClasspath.extendsFrom(loaderConfiguration);

        runtimeRemappingClasspath.extendsFrom(modRuntimeClasspath);
        runtimeRemappingClasspath.extendsFrom(intermediaryMinecraft);
        runtimeRemappingClasspath.extendsFrom(loaderConfiguration);

        var remappedCompileMods = project.files();
        project.getDependencies().add(remappedCompileClasspath.getName(), remappedCompileMods);
        var remappedRuntimeMods = project.files();
        project.getDependencies().add(remappedRuntimeClasspath.getName(), remappedRuntimeMods);

        var remapCompileMods = project.getTasks().register(sourceSet.getTaskName("crochetRemap", "CompileClasspath"), RemapJarsTask.class, task -> {
            task.setup(modCompileClasspath, loaderConfiguration, workingDirectory.get().dir("compileClasspath").dir(sourceSet.getName()), remappedCompileMods);
            task.dependsOn(intermediaryToNamed);
            task.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
        });

        var remapRuntimeMods = project.getTasks().register(sourceSet.getTaskName("crochetRemap", "RuntimeClasspath"), RemapJarsTask.class, task -> {
            task.setup(modRuntimeClasspath, loaderConfiguration, workingDirectory.get().dir("runtimeClasspath").dir(sourceSet.getName()), remappedRuntimeMods);
            task.dependsOn(intermediaryToNamed);
            task.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
        });

        extension.idePostSync.configure(task -> {
            task.dependsOn(remapCompileMods);
            task.dependsOn(remapRuntimeMods);
        });

        // TODO: isolate project dependencies and treat them differently
    }

    @Override
    @Nested
    public abstract FabricInstallationDependencies getDependencies();

    public void dependencies(Action<FabricInstallationDependencies> action) {
        action.execute(getDependencies());
    }

    @Override
    void forRun(Run run, RunType runType) {
        run.argFilesTask.configure(task -> {
            task.getMinecraftVersion().set(getMinecraft());
            task.dependsOn(writeLog4jConfig);
        });
        Configuration runtimeClasspath = project.getConfigurations().maybeCreate("crochet" + StringUtils.capitalize(this.getName()) + StringUtils.capitalize(run.getName()) + "Classpath");
        runtimeClasspath.extendsFrom(loaderConfiguration);
        runtimeClasspath.extendsFrom(mappingsClasspath);
        runtimeClasspath.extendsFrom(project.getConfigurations().getByName(CrochetPlugin.TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME));
        runtimeClasspath.attributes(attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            // We just default to 21 here if nothing is specified -- we'll want to be smarter about this in the future
            // and try and pull it from the source compile tasks I guess?
            attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, run.getToolchain().getLanguageVersion().map(JavaLanguageVersion::asInt).orElse(21));
        });

        var remapClasspathFile = project.getLayout().getBuildDirectory().file("crochet/runs/"+run.getName()+"/remapClasspath.txt");
        var remapClasspath = project.getTasks().register("crochet"+StringUtils.capitalize(getName())+StringUtils.capitalize(run.getName())+"RemapClasspath", MakeRemapClasspathFile.class, task -> {
            task.getRemapClasspathFile().set(remapClasspathFile);
            task.getRemapClasspath().from(remapClasspathConfiguration);
            task.dependsOn(mappingsClasspath);
        });
        run.argFilesTask.configure(task -> {
            task.dependsOn(remapClasspath);
        });

        run.classpath.extendsFrom(runtimeClasspath);
        run.getJvmArgs().addAll(
            "-Dfabric.development=true",
            // TODO: remap classpath file
            "-Dlog4j.configurationFile="+project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml").get().getAsFile().getAbsolutePath(),
            "-Dfabric.log.disableAnsi=false",
            "-Dfabric.gameVersion=${minecraft_version}",
            "-Dfabric.remapClasspathFile="+remapClasspathFile.get().getAsFile().getAbsolutePath()
        );
        run.getJvmArgs().add(project.provider(() -> {
            List<String> groups = new ArrayList<>();

            groups.add(
                this.binary.get().getAsFile().getAbsolutePath()
                    + File.pathSeparator
                    + this.resources.get().getAsFile().getAbsolutePath()
            );

            run.getRunMods().get().forEach(mod -> {
                groups.add(mod.components.getAsPath());
            });

            return "-Dfabric.classPathGroups=" + String.join(File.pathSeparator+File.pathSeparator, groups);
        }));
        switch (runType) {
            case CLIENT -> {
                runtimeClasspath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client"));
                runtimeClasspath.extendsFrom(minecraft);
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
                runtimeClasspath.extendsFrom(minecraft);
                run.getMainClass().convention("net.fabricmc.loader.impl.launch.knot.KnotClient");
            }
        }
    }
}
