package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.IdeaModelHandlerPlugin;
import dev.lukebemish.crochet.internal.InheritanceMarker;
import dev.lukebemish.crochet.internal.Log4jSetup;
import dev.lukebemish.crochet.internal.tasks.ArtifactTarget;
import dev.lukebemish.crochet.internal.tasks.ExtractFabricDependencies;
import dev.lukebemish.crochet.internal.tasks.FabricInstallationArtifacts;
import dev.lukebemish.crochet.internal.tasks.MakeRemapClasspathFile;
import dev.lukebemish.crochet.internal.tasks.MappingsWriter;
import dev.lukebemish.crochet.internal.tasks.RemapModsConfigMaker;
import dev.lukebemish.crochet.internal.tasks.RemapModsSourcesConfigMaker;
import dev.lukebemish.crochet.internal.tasks.TaskGraphExecution;
import dev.lukebemish.crochet.internal.tasks.WriteFile;
import dev.lukebemish.taskgraphrunner.model.conversion.SingleVersionGenerator;
import net.neoforged.srgutils.IMappingFile;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static dev.lukebemish.crochet.internal.ConfigurationUtils.copyAttributes;

public abstract class FabricInstallation extends AbstractVanillaInstallation {
    static final String ACCESS_WIDENER_CATEGORY = "accesswidener";

    final Configuration loaderConfiguration;
    final Configuration intermediaryMinecraft;
    final Configuration mappingsClasspath;
    final FileCollection writeLog4jConfig;
    final FabricInstallationArtifacts fabricConfigMaker;

    private final CrochetExtension extension;
    private final TaskProvider<MappingsWriter> intermediaryToNamed;
    private final TaskProvider<MappingsWriter> namedToIntermediary;
    final Configuration accessWideners;
    final Configuration accessWidenersElements;
    final TaskProvider<ExtractFabricDependencies> extractFabricForDependencies;

    final Configuration installationModCompileOnly;
    final Configuration installationModCompileOnlyApi;
    final Configuration installationModRuntimeOnly;
    final Configuration installationModLocalRuntime;
    final Configuration installationModLocalImplementation;
    final Configuration installationModImplementation;
    final Configuration installationModApi;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public FabricInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.extension = extension;

        var writeLog4jConfigTask = project.getTasks().register("writeCrochet"+StringUtils.capitalize(name)+"Log4jConfig", WriteFile.class, task -> {
            task.getContents().convention(
                Log4jSetup.FABRIC_CONFIG
            );
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml"));
        });
        this.writeLog4jConfig = extension.project.files(writeLog4jConfigTask.flatMap(WriteFile::getOutputFile)).builtBy(writeLog4jConfigTask);

        this.vanillaConfigMaker.getSidedAnnotation().set(SingleVersionGenerator.Options.SidedAnnotation.FABRIC);
        this.fabricConfigMaker = project.getObjects().newInstance(FabricInstallationArtifacts.class);
        fabricConfigMaker.getWrapped().set(vanillaConfigMaker);

        this.extractFabricForDependencies = project.getTasks().register("crochet"+StringUtils.capitalize(getName())+"ExtractForDependencies", ExtractFabricDependencies.class, task -> {
            task.getOutputDirectory().set(workingDirectory.get().dir("extracted"));
        });
        fabricConfigMaker.getAccessWideners().from(project.fileTree(extractFabricForDependencies.flatMap(ExtractFabricDependencies::getOutputDirectory)).builtBy(extractFabricForDependencies).filter(it -> it.getName().endsWith(".accesswidener")));
        fabricConfigMaker.getInterfaceInjection().from(project.fileTree(extractFabricForDependencies.flatMap(ExtractFabricDependencies::getOutputDirectory)).builtBy(extractFabricForDependencies).filter(it -> it.getName().equals("interface_injections.json")));
        project.getDependencies().add(this.injectedInterfaces.get().getName(), project.fileTree(extractFabricForDependencies.flatMap(ExtractFabricDependencies::getOutputDirectory)).builtBy(extractFabricForDependencies).filter(it -> it.getName().equals("neo_interface_injections.json")));

        var intermediaryToNamedFile = workingDirectory.map(dir -> dir.file("runner-intermediary-to-named.tiny"));
        var namedToIntermediaryFile = workingDirectory.map(dir -> dir.file("runner-named-to-intermediary.tiny"));

        this.binaryArtifactsTask.configure(task -> {
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("intermediaryToNamedMappings.output", intermediaryToNamedFile, project.getObjects()));
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("namedToIntermediaryMappings.output", namedToIntermediaryFile, project.getObjects()));
            task.getConfigMaker().set(fabricConfigMaker);
        });

        this.loaderConfiguration = project.getConfigurations().maybeCreate(getName()+"FabricLoader");
        this.loaderConfiguration.fromDependencyCollector(getDependencies().getLoader());
        this.nonUpgradableDependencies.extendsFrom(this.loaderConfiguration);

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
        this.binaryArtifactsTask.configure(task -> {
            task.dependsOn(intermediaryMappings);
        });

        this.accessWideners = project.getConfigurations().register(name+"AccessWideners", config -> {
            config.fromDependencyCollector(getDependencies().getAccessWideners());
            config.setCanBeConsumed(false);
        }).get();
        this.accessWidenersElements = project.getConfigurations().register(name+"AccessWidenersElements", config -> {
            config.setCanBeResolved(false);
            config.setCanBeDeclared(false);
            config.setCanBeConsumed(false);
            config.extendsFrom(this.accessWideners);
        }).get();
        fabricConfigMaker.getAccessWideners().from(accessWideners);

        var intermediaryJar = workingDirectory.map(it -> it.file("intermediary.jar"));
        this.binaryArtifactsTask.configure(task -> {
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("intermediary", intermediaryJar, project.getObjects()));
        });

        // To remap dependencies, we need a intermediary -> mojmaps + srg mapping.
        // We have intermediary -> official from intermediary, and official -> srg + mojmaps from neoform
        var objects = project.getObjects();

        this.intermediaryToNamed = project.getTasks().register("crochet"+StringUtils.capitalize(name)+"IntermediaryToNamed", MappingsWriter.class, task -> {
            task.getInputMappings().from(intermediaryToNamedFile);
            task.dependsOn(this.binaryArtifactsTask);
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

        this.namedToIntermediary = project.getTasks().register("crochet"+StringUtils.capitalize(name)+"NamedToIntermediary", MappingsWriter.class, task -> {
            task.getInputMappings().from(namedToIntermediaryFile);
            task.dependsOn(this.binaryArtifactsTask);
            task.getTargetFormat().set(IMappingFile.Format.TINY);
            task.getOutputMappings().set(workingDirectory.map(dir -> dir.file("named-to-intermediary.tiny")));
            // Ensure that the header is nicely present for various tools
            task.doLast(t -> {
                var mappingWriter = (MappingsWriter) t;
                var file = mappingWriter.getOutputMappings().getAsFile().get().toPath();
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    var firstLine = text.indexOf('\n');
                    var newHeader = "tiny\t2\t0\tnamed\tintermediary";
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

        this.intermediaryMinecraft = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"IntermediaryMinecraft", config -> {
            config.setCanBeConsumed(false);
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
            config.attributes(attributes -> attributes.attributeProvider(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::neoAttributeValue)));
        });

        var intermediaryJarFiles = project.files();
        intermediaryJarFiles.from(intermediaryJar);
        intermediaryJarFiles.builtBy(this.binaryArtifactsTask);
        project.getDependencies().add(intermediaryMinecraft.getName(), intermediaryJarFiles);

        this.installationModCompileOnly = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"ModCompileOnly").get();
        this.installationModCompileOnlyApi = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"ModCompileOnlyApi").get();
        this.installationModRuntimeOnly = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"ModRuntimeOnly").get();
        this.installationModLocalRuntime = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"ModLocalRuntime").get();
        this.installationModLocalImplementation = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"ModLocalImplementation").get();
        this.installationModImplementation = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"ModImplementation").get();
        this.installationModApi = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"ModApi").get();
    }

    @Override
    public void forFeature(SourceSet sourceSet) {
        this.forFeature(sourceSet, deps -> {});
    }

    @Override
    public void forLocalFeature(SourceSet sourceSet) {
        this.forLocalFeature(sourceSet, deps -> {});
    }

    public void forFeature(SourceSet sourceSet, Action<FabricSourceSetDependencies> action) {
        super.forFeature(sourceSet);
        forFeatureShared(sourceSet, action, false);
    }

    public void forLocalFeature(SourceSet sourceSet, Action<FabricSourceSetDependencies> action) {
        super.forLocalFeature(sourceSet);
        forFeatureShared(sourceSet, action, true);
    }

    @Override
    protected List<String> getInstallationConfigurationNames() {
        var out = new ArrayList<>(super.getInstallationConfigurationNames());
        out.add("AccessWideners");
        return out;
    }

    private Configuration forRunRemapping(Run run, RunType runType) {
        /*
        General architecture
        - run.classpath -- the normal run classpath. We make it select "not-to-remap" dependencies
        - modClasspath -- collect dependencies for remapping. Resolve remap type "to-remap"
        - remappedClasspath -- output of remapping, used to insert stuff into run classpath

        To exclude deps, we make another classpath:
        - excludedClasspath -- run.classpath with only project/module dependencies

        And we still need to pin versions:
        - versioningClasspath
        And have every resolving configuration, run.classpath and modClasspath, be shouldResolveConsistentlyWith it
         */

        var runClasspath = run.classpath;

        var modClasspath = project.getConfigurations().register("crochet"+StringUtils.capitalize(run.getName())+"RunModClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(runClasspath.getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
            config.setCanBeConsumed(false);
        }).get();
        modClasspath.fromDependencyCollector(run.getImplementation());
        runClasspath.fromDependencyCollector(run.getImplementation());

        var remappedClasspath = project.getConfigurations().maybeCreate("crochet"+StringUtils.capitalize(run.getName())+"RunRemappedModClasspath");

        var excludedClasspath = project.getConfigurations().register("crochet"+StringUtils.capitalize(run.getName())+"RunExcludedClasspath", config -> {
            config.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
                copyAttributes(runClasspath.getAttributes(), attributes, project.getProviders());
            });
            config.setCanBeConsumed(false);
        }).get();
        excludedClasspath.fromDependencyCollector(run.getImplementation());

        var versioningClasspath = project.getConfigurations().register("crochet"+StringUtils.capitalize(run.getName())+"RunVersioningClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(runClasspath.getAttributes(), attributes, project.getProviders());
            });
            config.shouldResolveConsistentlyWith(switch (runType) {
                case CLIENT -> nonUpgradableClientRuntimeDependencies;
                case SERVER -> nonUpgradableServerRuntimeDependencies;
                case DATA -> nonUpgradableClientRuntimeDependencies;
            });
            config.setCanBeConsumed(false);
        }).get();

        versioningClasspath.extendsFrom(modClasspath);
        versioningClasspath.extendsFrom(runClasspath);
        runClasspath.shouldResolveConsistentlyWith(versioningClasspath);
        modClasspath.shouldResolveConsistentlyWith(versioningClasspath);
        excludedClasspath.shouldResolveConsistentlyWith(versioningClasspath);

        runClasspath.attributes(attributes -> {
            attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
        });

        run.classpath.extendsFrom(remappedClasspath);

        var remappingClasspath = project.getConfigurations().register("crochet"+StringUtils.capitalize(run.getName())+"RunRemappingClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(runClasspath.getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
            config.setCanBeConsumed(false);
        }).get();
        remappingClasspath.extendsFrom(modClasspath);

        remappingClasspath.extendsFrom(modClasspath);
        remappingClasspath.extendsFrom(intermediaryMinecraft);
        remappingClasspath.shouldResolveConsistentlyWith(versioningClasspath);

        var remappedMods = project.files();
        project.getDependencies().add(remappedClasspath.getName(), remappedMods);

        var remapMods = project.getTasks().register("crochetRemap"+StringUtils.capitalize(run.getName())+"RunClasspath", TaskGraphExecution.class, task -> {
            var configMaker = project.getObjects().newInstance(RemapModsConfigMaker.class);
            configMaker.setup(task, modClasspath, excludedClasspath, workingDirectory.get().dir("runClasspath").dir(run.getName()), remappedMods);
            task.dependsOn(intermediaryToNamed);
            configMaker.getDistribution().set(getDistribution());
            configMaker.getRemappingClasspath().from(remappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
            task.getConfigMaker().set(configMaker);

            task.copyArtifactsFrom(this.binaryArtifactsTask.get());
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });
        remappedMods.builtBy(remapMods);

        var remapModSources = project.getTasks().register("crochetRemap"+StringUtils.capitalize(run.getName())+"RunClasspathSources", TaskGraphExecution.class, task -> {
            var configMaker = project.getObjects().newInstance(RemapModsSourcesConfigMaker.class);
            configMaker.setup(task, modClasspath, excludedClasspath, workingDirectory.get().dir("runClasspathSources").dir(run.getName()));
            task.dependsOn(intermediaryToNamed);
            configMaker.getRemappingClasspath().from(remappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
            task.getConfigMaker().set(configMaker);

            task.copyArtifactsFrom(this.binaryArtifactsTask.get());
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        linkSources(remapMods, remapModSources);

        extension.idePostSync.configure(task -> {
            task.dependsOn(remapMods);

            task.dependsOn(remapModSources);
        });

        return remappingClasspath;
    }

    private static final List<String> MOD_CONFIGURATION_NAMES = List.of(
        "compileOnly",
        "compileOnlyApi",
        "runtimeOnly",
        "implementation",
        "api",
        "localRuntime",
        "localImplementation"
    );

    @SuppressWarnings("UnstableApiUsage")
    private void forFeatureShared(SourceSet sourceSet, Action<FabricSourceSetDependencies> action, boolean local) {
        /*
        General architecture:
        - compileClasspath -- the normal classpath for the mod. Specifically selects "not-to-remap" dependencies
        - modCompileClasspath and modRuntimeClasspath -- collect dependencies for remapping. Resolve remap type "to-remap"
        - modApiElements and modRuntimeElements -- remap type "to-remap", these expose to-remap dependencies, but no artifacts, transitively
        - nonModApiElements and nonModRuntimeElements -- remap type "not-ro-remap", these expose the non-remapped project artifacts/variants, as well as any non-remapped deps, transitively
        - remappedCompileClasspath -- output of remapping, used to insert stuff into compile classpath

        ATs and IIs are grabbed from anything that is on both modRuntime and modCompile classpaths
        TODO: see if we can limit that to only care about compile by having a separate MC jar for runtime

        Remapping is set up here only for the compile classpath. The excluded deps should be anything on compileClasspath that _isn't_ the remapped dependencies (any project/module deps, perhaps?)
        To handle exclusions, we make another classpath:
        - excludedCompileClasspath -- compileClasspath with only project/module dependencies

        And finally, to handle versioning right, we make a version-determining classpath:
        - versioningCompileClasspath
        And have every resolving configuration, compileClasspath and modCompileClasspath, be shouldResolveConsistentlyWith it

        If this is non-local
        - remapJar, remapSourcesJar -- remapped jar dependencies
         */

        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(loaderConfiguration);
        });

        var dependencies = project.getObjects().newInstance(FabricSourceSetDependencies.class);
        action.execute(dependencies);

        var modCompileClasspath = project.getConfigurations().register(sourceSet.getTaskName("crochetMod", "compileClasspath"), config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
            config.setCanBeDeclared(false);
            config.setCanBeConsumed(false);
        }).get();
        var modRuntimeClasspath = project.getConfigurations().register(sourceSet.getTaskName("crochetMod", "runtimeClasspath"), config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
            config.setCanBeDeclared(false);
            config.setCanBeConsumed(false);
        }).get();

        var versioningCompileClasspath = project.getConfigurations().register(sourceSet.getTaskName("crochetVersioning", "compileClasspath"), config -> {
            config.attributes(attributes -> {
                // Does not have the remap type attribute at this point
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
            });
            config.setCanBeConsumed(false);
            config.shouldResolveConsistentlyWith(switch (getDistribution().get()) {
                case CLIENT, JOINED -> nonUpgradableClientCompileDependencies;
                case SERVER, COMMON -> nonUpgradableServerCompileDependencies;
            });
        }).get();

        var excludedCompileClasspath = project.getConfigurations().register(sourceSet.getTaskName("crochetExcluded", "compileClasspath"), config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
            });
            config.setCanBeConsumed(false);
        }).get();

        var compileClasspath = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
        excludedCompileClasspath.getDependencyConstraints().addAllLater(project.provider(compileClasspath::getDependencyConstraints));
        // Only non-file-collection dependencies
        excludedCompileClasspath.getDependencies().addAllLater(project.provider(compileClasspath::getAllDependencies).map(deps -> new ArrayList<>(deps.stream().filter(dep -> (dep instanceof ModuleDependency)).toList())));

        versioningCompileClasspath.extendsFrom(modCompileClasspath);
        versioningCompileClasspath.extendsFrom(compileClasspath);
        compileClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);
        modCompileClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);
        excludedCompileClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);

        this.extractFabricForDependencies.configure(task -> {
            task.getCompileModJars().from(modCompileClasspath);
            task.getRuntimeModJars().from(modRuntimeClasspath);
        });

        var runtimeElements = project.getConfigurations().maybeCreate(sourceSet.getRuntimeElementsConfigurationName());
        var apiElements = project.getConfigurations().maybeCreate(sourceSet.getApiElementsConfigurationName());

        var modRuntimeElements = project.getConfigurations().register(sourceSet.getTaskName("crochetMod", "runtimeElements")).get();
        modRuntimeElements.setCanBeDeclared(false);
        var modApiElements = project.getConfigurations().register(sourceSet.getTaskName("crochetMod", "apiElements")).get();
        modApiElements.setCanBeDeclared(false);

        var nonModRuntimeElements = project.getConfigurations().maybeCreate(sourceSet.getTaskName("crochetNonMod", "runtimeElements"));
        nonModRuntimeElements.setCanBeDeclared(false);
        nonModRuntimeElements.setCanBeResolved(false);
        var nonModApiElements = project.getConfigurations().maybeCreate(sourceSet.getTaskName("crochetNonMod", "apiElements"));
        nonModApiElements.setCanBeDeclared(false);
        nonModApiElements.setCanBeResolved(false);

        FeatureUtils.forSourceSetFeature(project, sourceSet.getName(), context -> {
            context.withCapabilities(accessWidenersElements);
            accessWidenersElements.setCanBeConsumed(true);
            accessWidenersElements.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_WIDENER_CATEGORY));
            });

            context.withCapabilities(modRuntimeElements);
            context.withCapabilities(modApiElements);
            context.withCapabilities(nonModRuntimeElements);
            context.withCapabilities(nonModApiElements);

            modRuntimeElements.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                copyAttributes(runtimeElements.getAttributes(), attributes, project.getProviders());
            });
            modRuntimeElements.setCanBeConsumed(true);
            modRuntimeElements.setCanBeResolved(false);

            nonModRuntimeElements.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                copyAttributes(runtimeElements.getAttributes(), attributes, project.getProviders());
            });
            nonModRuntimeElements.setCanBeConsumed(true);
            nonModRuntimeElements.setCanBeResolved(false);

            modApiElements.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                copyAttributes(apiElements.getAttributes(), attributes, project.getProviders());
            });
            modApiElements.setCanBeConsumed(true);
            modApiElements.setCanBeResolved(false);

            nonModApiElements.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                copyAttributes(apiElements.getAttributes(), attributes, project.getProviders());
            });
            nonModApiElements.setCanBeConsumed(true);
            nonModApiElements.setCanBeResolved(false);

            nonModApiElements.getDependencyConstraints().addAllLater(project.provider(apiElements::getDependencyConstraints));
            nonModRuntimeElements.getDependencyConstraints().addAllLater(project.provider(runtimeElements::getDependencyConstraints));

            nonModApiElements.getDependencies().addAllLater(project.provider(() ->
                apiElements.getAllDependencies().stream().filter(dep -> (dep instanceof ProjectDependency) || !modCompileClasspath.getAllDependencies().contains(dep)).toList()
            ));
            nonModRuntimeElements.getDependencies().addAllLater(project.provider(() ->
                runtimeElements.getAllDependencies().stream().filter(dep -> (dep instanceof ProjectDependency) || !modRuntimeClasspath.getAllDependencies().contains(dep)).toList()
            ));

            var remappedSourcesElements = project.getConfigurations().maybeCreate(sourceSet.getTaskName("crochetRemapped", "sourcesElements"));

            if (!local) {
                {
                    // Remap jar
                    var jarTask = project.getTasks().named(sourceSet.getJarTaskName(), Jar.class);

                    AtomicBoolean hasSetup = new AtomicBoolean(false);
                    AtomicReference<String> classifier = new AtomicReference<>();
                    AtomicReference<RegularFile> oldJarLocation = new AtomicReference<>();

                    BiConsumer<TaskGraphExecution, Jar> configurator = (remapJar, jar) -> {
                        if (hasSetup.getAndSet(true)) {
                            return;
                        }
                        var configMaker = project.getObjects().newInstance(RemapModsConfigMaker.class);
                        var oldArchiveFile = jarTask.get().getArchiveFile().get();
                        var existingClassifier = jarTask.get().getArchiveClassifier().get();

                        classifier.set(existingClassifier);
                        oldJarLocation.set(oldArchiveFile);

                        jarTask.get().getArchiveClassifier().set(existingClassifier.isEmpty() ? "dev" : existingClassifier + "-dev");
                        var remappingClasspath = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getIncoming().artifactView(view -> view.attributes(attributes ->
                            attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                        )).getFiles();
                        configMaker.remapSingleJar(remapJar, input -> {
                            input.set(jarTask.flatMap(AbstractArchiveTask::getArchiveFile));
                        }, output -> {
                            output.set(oldArchiveFile);
                        }, mappings -> {
                        }, remappingClasspath);
                        // Change direction and whatnot
                        configMaker.getIsReObf().set(true);

                        configMaker.getStripNestedJars().set(false);
                        configMaker.getIncludedInterfaceInjections().from(injectedInterfacesElements.get().getOutgoing().getArtifacts().getFiles());

                        configMaker.getMappings().set(namedToIntermediary.flatMap(MappingsWriter::getOutputMappings));
                        remapJar.getConfigMaker().set(configMaker);

                        remapJar.artifactsConfiguration(project.getConfigurations().getByName(CrochetProjectPlugin.TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME));
                        remapJar.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
                        remapJar.dependsOn(jarTask);

                        // Set up mixin remapping flags via jar manifest
                        jar.manifest(m -> m.attributes(Map.of(
                            "Fabric-Loom-Mixin-Remap-Type", "STATIC"
                        )));
                    };

                    var remapJarTask = project.getTasks().register(sourceSet.getTaskName("remap", "jar"), TaskGraphExecution.class, task -> {
                        configurator.accept(task, jarTask.get());
                    });

                    jarTask.configure(task -> {
                        configurator.accept(remapJarTask.get(), task);
                    });

                    project.getTasks().named("build", t -> t.dependsOn(remapJarTask));

                    nonModRuntimeElements.getOutgoing().getArtifacts().addAll(runtimeElements.getAllArtifacts());
                    runtimeElements.getOutgoing().getArtifacts().clear();
                    nonModApiElements.getOutgoing().getArtifacts().addAll(apiElements.getAllArtifacts());
                    apiElements.getOutgoing().getArtifacts().clear();

                    project.artifacts(artifacts -> {
                        configurator.accept(remapJarTask.get(), jarTask.get());
                        artifacts.add(runtimeElements.getName(), oldJarLocation.get(), spec -> {
                            spec.setClassifier(classifier.get());
                            spec.setType(ArtifactTypeDefinition.JAR_TYPE);
                            spec.builtBy(remapJarTask);
                        });
                        artifacts.add(apiElements.getName(), oldJarLocation.get(), spec -> {
                            spec.setClassifier(classifier.get());
                            spec.setType(ArtifactTypeDefinition.JAR_TYPE);
                            spec.builtBy(remapJarTask);
                        });
                    });
                }

                {
                    // Remap sources jar
                    AtomicBoolean hasSetup = new AtomicBoolean(false);
                    AtomicReference<String> classifier = new AtomicReference<>();
                    AtomicReference<RegularFile> oldJarLocation = new AtomicReference<>();

                    BiConsumer<TaskGraphExecution, Jar> configurator = (remapSourcesJar, sourcesJar) -> {
                        if (hasSetup.getAndSet(true)) {
                            return;
                        }
                        var configMaker = project.getObjects().newInstance(RemapModsSourcesConfigMaker.class);
                        var oldArchiveFile = sourcesJar.getArchiveFile().get();
                        var existingClassifier = sourcesJar.getArchiveClassifier().get();

                        classifier.set(existingClassifier);
                        oldJarLocation.set(oldArchiveFile);

                        sourcesJar.getArchiveClassifier().set(existingClassifier.isEmpty() ? "dev" : existingClassifier + "-dev");
                        var remappingClasspath = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getIncoming().artifactView(view -> view.attributes(attributes ->
                            attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                        )).getFiles();
                        configMaker.remapSingleJar(remapSourcesJar, input -> {
                            input.set(sourcesJar.getArchiveFile());
                        }, output -> {
                            output.set(oldArchiveFile);
                        }, mappings -> {
                        }, remappingClasspath);

                        configMaker.getMappings().set(namedToIntermediary.flatMap(MappingsWriter::getOutputMappings));
                        remapSourcesJar.getConfigMaker().set(configMaker);

                        remapSourcesJar.artifactsConfiguration(project.getConfigurations().getByName(CrochetProjectPlugin.TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME));
                        remapSourcesJar.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
                        remapSourcesJar.dependsOn(sourcesJar);
                    };

                    AtomicBoolean registered = new AtomicBoolean(false);

                    var remapTaskName = sourceSet.getTaskName("remap", "SourcesJar");

                    Consumer<Jar> maybeConfigure = jarTask -> {
                        if (registered.compareAndSet(false, true)) {
                            var remapTask = project.getTasks().register(remapTaskName, TaskGraphExecution.class);
                            configurator.accept(remapTask.get(), jarTask);
                            project.getTasks().named("build", t -> t.dependsOn(remapTask));

                            var sourcesElements = project.getConfigurations().maybeCreate(sourceSet.getSourcesElementsConfigurationName());
                            sourcesElements.getOutgoing().getArtifacts().clear();

                            // An empty configuration, so that the RemapModsSourcesConfigMaker will skip the actual sources jar
                            remappedSourcesElements.attributes(attributes -> {
                                copyAttributes(sourcesElements.getAttributes(), attributes, project.getProviders());
                                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                            });
                            remappedSourcesElements.setCanBeResolved(false);
                            remappedSourcesElements.setCanBeConsumed(true);
                            remappedSourcesElements.setCanBeDeclared(false);

                            project.artifacts(artifacts -> {
                                artifacts.add(sourcesElements.getName(), oldJarLocation.get(), spec -> {
                                    spec.setClassifier(classifier.get());
                                    spec.setType(ArtifactTypeDefinition.JAR_TYPE);
                                    spec.builtBy(remapTask);
                                });
                            });
                        }
                    };

                    project.getTasks().withType(Jar.class, sourceJarTask -> {
                        if (sourceJarTask.getName().equals(sourceSet.getSourcesJarTaskName())) {
                            maybeConfigure.accept(sourceJarTask);
                        }
                    });

                    project.afterEvaluate(p -> {
                        var sourceJarTask = project.getTasks().findByName(sourceSet.getSourcesJarTaskName());
                        if (sourceJarTask instanceof Jar jarTask) {
                            maybeConfigure.accept(jarTask);
                        }
                    });
                }
            } else {
                nonModApiElements.getOutgoing().getArtifacts().addAllLater(project.provider(apiElements::getAllArtifacts));
                nonModRuntimeElements.getOutgoing().getArtifacts().addAllLater(project.provider(runtimeElements::getAllArtifacts));
            }
            apiElements.getOutgoing().getVariants().forEach(variant -> {
                var newVariant = nonModApiElements.getOutgoing().getVariants().create(variant.getName());
                copyAttributes(variant.getAttributes(), newVariant.getAttributes(), project.getProviders());
                newVariant.getArtifacts().addAllLater(project.provider(variant::getArtifacts));
                newVariant.getAttributes().attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, dev.lukebemish.crochet.internal.CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
            });
            apiElements.getOutgoing().getVariants().clear();
            runtimeElements.getOutgoing().getVariants().forEach(variant -> {
                var newVariant = nonModRuntimeElements.getOutgoing().getVariants().create(variant.getName());
                copyAttributes(variant.getAttributes(), newVariant.getAttributes(), project.getProviders());
                newVariant.getArtifacts().addAllLater(project.provider(variant::getArtifacts));
                newVariant.getAttributes().attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
            });
            runtimeElements.getOutgoing().getVariants().clear();
        });

        var modCompileOnly = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "compileOnly"));
        modCompileOnly.fromDependencyCollector(dependencies.getModCompileOnly());
        modCompileOnly.extendsFrom(installationModCompileOnly);
        modCompileClasspath.extendsFrom(modCompileOnly);
        var modCompileOnlyApi = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "compileOnlyApi"));
        modCompileOnlyApi.fromDependencyCollector(dependencies.getModCompileOnlyApi());
        modCompileOnlyApi.extendsFrom(installationModCompileOnlyApi);
        modCompileClasspath.extendsFrom(modCompileOnlyApi);
        apiElements.extendsFrom(modCompileOnlyApi);
        modApiElements.extendsFrom(modCompileOnlyApi);
        var modRuntimeOnly = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "runtimeOnly"));
        modRuntimeOnly.fromDependencyCollector(dependencies.getModRuntimeOnly());
        modRuntimeOnly.extendsFrom(installationModRuntimeOnly);
        modRuntimeClasspath.extendsFrom(modRuntimeOnly);
        runtimeElements.extendsFrom(modRuntimeOnly);
        modRuntimeElements.extendsFrom(modRuntimeOnly);
        var modLocalRuntime = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "localRuntime"));
        modLocalRuntime.fromDependencyCollector(dependencies.getModLocalRuntime());
        modLocalRuntime.extendsFrom(installationModLocalRuntime);
        modRuntimeClasspath.extendsFrom(modLocalRuntime);
        var modLocalImplementation = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "localImplementation"));
        modLocalImplementation.fromDependencyCollector(dependencies.getModLocalImplementation());
        modLocalImplementation.extendsFrom(installationModLocalImplementation);
        modCompileClasspath.extendsFrom(modLocalImplementation);
        modRuntimeClasspath.extendsFrom(modLocalImplementation);
        var modImplementation = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "implementation"));
        modImplementation.fromDependencyCollector(dependencies.getModImplementation());
        modImplementation.extendsFrom(installationModImplementation);
        modCompileClasspath.extendsFrom(modImplementation);
        modRuntimeClasspath.extendsFrom(modImplementation);
        runtimeElements.extendsFrom(modImplementation);
        modRuntimeElements.extendsFrom(modImplementation);
        apiElements.extendsFrom(modImplementation);
        modApiElements.extendsFrom(modImplementation);
        var modApi = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "api"));
        modApi.fromDependencyCollector(dependencies.getModApi());
        modApi.extendsFrom(installationModApi);
        modCompileClasspath.extendsFrom(modApi);
        modRuntimeClasspath.extendsFrom(modApi);
        runtimeElements.extendsFrom(modApi);
        modRuntimeElements.extendsFrom(modApi);
        apiElements.extendsFrom(modApi);
        modApiElements.extendsFrom(modApi);

        // Link up inheritance via CrochetFeatureContexts for the injected configurations
        var marker = InheritanceMarker.getOrCreate(project.getObjects(), sourceSet);
        marker.getShouldTakeConfigurationsFrom().configureEach(name -> {
            var otherSourceSet = project.getExtensions().getByType(SourceSetContainer.class).findByName(name);
            var otherInstallation = extension.findInstallation(otherSourceSet);
            if (otherInstallation instanceof FabricInstallation) {
                for (var confName : MOD_CONFIGURATION_NAMES) {
                    var thisConf = project.getConfigurations().getByName(sourceSet.getTaskName("mod", confName));
                    var otherConf = project.getConfigurations().getByName(otherSourceSet.getTaskName("mod", confName));
                    thisConf.extendsFrom(otherConf);
                }
            }
        });
        marker.getShouldGiveConfigurationsTo().configureEach(name -> {
            var otherSourceSet = project.getExtensions().getByType(SourceSetContainer.class).findByName(name);
            var otherInstallation = extension.findInstallation(otherSourceSet);
            if (otherInstallation instanceof FabricInstallation) {
                for (var confName : MOD_CONFIGURATION_NAMES) {
                    var thisConf = project.getConfigurations().getByName(sourceSet.getTaskName("mod", confName));
                    var otherConf = project.getConfigurations().getByName(otherSourceSet.getTaskName("mod", confName));
                    otherConf.extendsFrom(thisConf);
                }
            }
        });

        var interfaceInjectionCompile = modCompileClasspath.getIncoming().artifactView(config -> {
            config.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, MinecraftInstallation.INTERFACE_INJECTION_CATEGORY));
            });
            config.withVariantReselection();
            config.setLenient(true);
        });
        var interfaceInjectionRuntime = modRuntimeClasspath.getIncoming().artifactView(config -> {
            config.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, MinecraftInstallation.INTERFACE_INJECTION_CATEGORY));
            });
            config.withVariantReselection();
            config.setLenient(true);
        });
        var accessWidenersCompile = modCompileClasspath.getIncoming().artifactView(config -> {
            config.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_WIDENER_CATEGORY));
            });
            config.withVariantReselection();
            config.setLenient(true);
        });
        var accessWidenersRuntime = modRuntimeClasspath.getIncoming().artifactView(config -> {
            config.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_WIDENER_CATEGORY));
            });
            config.withVariantReselection();
            config.setLenient(true);
        });
        this.extractFabricForDependencies.configure(task -> {
            task.getFloatingCompileNeoInterfaceInjections().from(interfaceInjectionCompile.getFiles());
            task.getFloatingRuntimeNeoInterfaceInjections().from(interfaceInjectionRuntime.getFiles());
            task.getFloatingCompileAccessWideners().from(accessWidenersCompile.getFiles());
            task.getFloatingRuntimeAccessWideners().from(accessWidenersRuntime.getFiles());
        });

        var remappedCompileClasspath = project.getConfigurations().maybeCreate(sourceSet.getTaskName("crochetRemapped", "compileClasspath"));
        project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(remappedCompileClasspath);

        var compileRemappingClasspath = project.getConfigurations().register(sourceSet.getTaskName("crochetRemapping", "compileClasspath"), config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
            config.setCanBeConsumed(false);
            config.setCanBeDeclared(false);
        }).get();

        project.getConfigurations().named(sourceSet.getCompileClasspathConfigurationName(), config -> {
            config.attributes(attributes ->
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP)
            );
            config.getDependencies().addAllLater(project.provider(() ->
                modCompileClasspath.getAllDependencies().stream().filter(dep -> dep instanceof ProjectDependency).toList()
            ));
        });

        compileRemappingClasspath.extendsFrom(modCompileClasspath);
        compileRemappingClasspath.extendsFrom(intermediaryMinecraft);
        compileRemappingClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);

        var remappedCompileMods = project.files();
        project.getDependencies().add(remappedCompileClasspath.getName(), remappedCompileMods);

        var remapCompileMods = project.getTasks().register(sourceSet.getTaskName("crochetRemap", "CompileClasspath"), TaskGraphExecution.class, task -> {
            var configMaker = project.getObjects().newInstance(RemapModsConfigMaker.class);
            configMaker.setup(task, modCompileClasspath, excludedCompileClasspath, workingDirectory.get().dir("compileClasspath").dir(sourceSet.getName()), remappedCompileMods);
            task.dependsOn(intermediaryToNamed);
            configMaker.getDistribution().set(getDistribution());
            configMaker.getRemappingClasspath().from(compileRemappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
            task.getConfigMaker().set(configMaker);

            task.copyArtifactsFrom(this.binaryArtifactsTask.get());
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });
        remappedCompileMods.builtBy(remapCompileMods);

        var remapCompileModSources = project.getTasks().register(sourceSet.getTaskName("crochetRemap", "CompileClasspathSources"), TaskGraphExecution.class, task -> {
            var configMaker = project.getObjects().newInstance(RemapModsSourcesConfigMaker.class);
            configMaker.setup(task, modCompileClasspath, excludedCompileClasspath, workingDirectory.get().dir("compileClasspathSources").dir(sourceSet.getName()));
            task.dependsOn(intermediaryToNamed);
            configMaker.getRemappingClasspath().from(compileRemappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
            task.getConfigMaker().set(configMaker);

            task.copyArtifactsFrom(this.binaryArtifactsTask.get());
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        linkSources(remapCompileMods, remapCompileModSources);

        extension.idePostSync.configure(task -> {
            task.dependsOn(remapCompileMods);

            task.dependsOn(remapCompileModSources);
        });
    }

    private void linkSources(TaskProvider<TaskGraphExecution> remapJars, TaskProvider<TaskGraphExecution> remapSourceJars) {
        record Pair<T, U>(T first, U second) implements Serializable {}

        if (IdeaModelHandlerPlugin.isIdeaSyncRelated(project)) {
            Provider<Map<String, ArtifactTarget>> sources = remapSourceJars.get().getConfigMaker().map(configMaker -> {
                var targets = ((RemapModsSourcesConfigMaker) configMaker).getTargets().get();
                Map<String, ArtifactTarget> map = new HashMap<>();
                targets.forEach(target -> target.getCapabilities().get().forEach(cap -> map.put(cap, target)));
                return map;
            });

            Provider<List<Pair<ArtifactTarget, ArtifactTarget>>> binariesToSources = sources.zip(remapJars.get().getConfigMaker().flatMap(configMaker -> ((RemapModsConfigMaker) configMaker).getTargets()), (sourceMap, binaryTargets) -> {
                List<Pair<ArtifactTarget, ArtifactTarget>> map = new ArrayList<>();
                binaryTargets.forEach(binary -> {
                    Set<ArtifactTarget> sourceTargets = new HashSet<>();
                    binary.getCapabilities().get().forEach(cap -> {
                        ArtifactTarget source = sourceMap.get(cap);
                        if (source != null) {
                            sourceTargets.add(source);
                        }
                    });
                    if (sourceTargets.size() == 1) {
                        map.add(new Pair<>(binary, sourceTargets.iterator().next()));
                    }
                });
                return map;
            });

            IdeaModelHandlerPlugin.retrieve(project).mapBinariesToSources(
                binariesToSources.map(pairs -> pairs.stream().map(p -> p.first().getTarget().get()).toList()),
                binariesToSources.map(pairs -> pairs.stream().map(p -> p.second().getTarget().get()).toList())
            );
        }
    }

    @Override
    protected boolean canPublishInjectedInterfaces() {
        return false;
    }

    @Override
    protected FabricInstallationDependencies makeDependencies(Project project) {
        return project.getObjects().newInstance(FabricInstallationDependencies.class, this);
    }

    @Override
    @Nested
    public FabricInstallationDependencies getDependencies() {
        return (FabricInstallationDependencies) dependencies;
    }

    public void dependencies(Action<? super FabricInstallationDependencies> action) {
        action.execute(getDependencies());
    }

    @Override
    void forRun(Run run, RunType runType) {
        super.forRun(run, runType);
        run.argFilesTask.configure(task -> {
            task.getMinecraftVersion().set(getMinecraft());
            task.dependsOn(writeLog4jConfig);
        });
        var remapClasspathConfiguration = forRunRemapping(run, runType);

        // TODO: figure out setting java version attribute on run classpath?

        var remapClasspathFile = project.getLayout().getBuildDirectory().file("crochet/runs/"+run.getName()+"/remapClasspath.txt");
        var remapClasspath = project.getTasks().register("crochet"+StringUtils.capitalize(getName())+StringUtils.capitalize(run.getName())+"RemapClasspath", MakeRemapClasspathFile.class, task -> {
            task.getRemapClasspathFile().set(remapClasspathFile);
            task.getRemapClasspath().from(remapClasspathConfiguration);
            task.dependsOn(mappingsClasspath);
        });
        run.argFilesTask.configure(task -> {
            task.dependsOn(remapClasspath);
        });
        run.getJvmArgs().addAll(
            "-Dfabric.development=true",
            "-Dlog4j.configurationFile="+project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml").get().getAsFile().getAbsolutePath(),
            "-Dfabric.log.disableAnsi=false",
            "-Dfabric.gameVersion=${minecraft_version}",
            "-Dfabric.remapClasspathFile="+remapClasspathFile.get().getAsFile().getAbsolutePath()
        );
        Provider<SequencedSet<File>> excluded = project.provider(() -> {
            var excludesSet = new LinkedHashSet<File>();
            if (run.getAvoidNeedlessDecompilation().get()) {
                excludesSet.add(this.binary.get().getAsFile());
            } else {
                excludesSet.add(this.binaryLineMapped.get().getAsFile());
            }
            excludesSet.add(this.resources.get().getAsFile());
            return excludesSet;
        });

        // The ide sync task does not depend on the arg files task, since this provider will create a task dependency from the arg files task on the run contents
        var fileGroups = ClasspathGroupUtilities.modGroupsFromDependencies(run.classpath, excluded);
        run.getJvmArgs().add(fileGroups.map(groups ->
            "-Dfabric.classPathGroups=" + groups.stream().map(set -> set.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator))).collect(Collectors.joining(File.pathSeparator+File.pathSeparator))
        ));
        run.classpath.attributes(attributes -> {
            attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, switch (runType) {
                case CLIENT -> "client";
                case SERVER -> "server";
                default -> throw new IllegalArgumentException("Unsupported run type: "+runType);
            });
            attributes.attribute(CrochetProjectPlugin.CROCHET_DISTRIBUTION_ATTRIBUTE, runType.attributeName());
        });

        Configuration runMinecraft = project.getConfigurations().create("crochet"+StringUtils.capitalize(run.getName())+"RunMinecraft", config -> {
            config.setCanBeConsumed(false);
            project.afterEvaluate(p -> {
                if (run.getAvoidNeedlessDecompilation().get()) {
                    config.extendsFrom(minecraft);
                } else {
                    config.extendsFrom(minecraftLineMapped);
                }
            });
        });

        run.classpath.extendsFrom(loaderConfiguration);
        run.classpath.extendsFrom(project.getConfigurations().getByName(CrochetProjectPlugin.TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME));
        run.classpath.extendsFrom(runMinecraft);
        run.classpath.extendsFrom(mappingsClasspath);

        switch (runType) {
            case CLIENT -> {
                run.getMainClass().convention("net.fabricmc.loader.impl.launch.knot.KnotClient");
                run.getArgs().addAll(
                    "--assetIndex",
                    "${assets_index_name}",
                    "--assetsDir",
                    "${assets_root}"
                );
            }
            case SERVER -> {
                run.getMainClass().convention("net.fabricmc.loader.impl.launch.knot.KnotServer");
            }
            default -> throw new IllegalArgumentException("Unsupported run type: "+runType);
        }
    }

    @Override
    protected String sharingInstallationTypeTag() {
        return "fabric";
    }
}
