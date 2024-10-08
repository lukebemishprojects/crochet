package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.IdeaModelHandlerPlugin;
import dev.lukebemish.crochet.internal.Log4jSetup;
import dev.lukebemish.crochet.mappings.ChainedMappingsSource;
import dev.lukebemish.crochet.mappings.FileMappingSource;
import dev.lukebemish.crochet.mappings.ReversedMappingsSource;
import dev.lukebemish.crochet.tasks.ArtifactTarget;
import dev.lukebemish.crochet.tasks.ExtractFabricDependencies;
import dev.lukebemish.crochet.tasks.FabricInstallationArtifacts;
import dev.lukebemish.crochet.tasks.MakeRemapClasspathFile;
import dev.lukebemish.crochet.tasks.MappingsWriter;
import dev.lukebemish.crochet.tasks.RemapModsConfigMaker;
import dev.lukebemish.crochet.tasks.RemapSourceJarsTask;
import dev.lukebemish.crochet.tasks.TaskGraphExecution;
import dev.lukebemish.crochet.tasks.WriteFile;
import dev.lukebemish.taskgraphrunner.model.conversion.SingleVersionGenerator;
import net.neoforged.srgutils.IMappingFile;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public abstract class FabricInstallation extends AbstractVanillaInstallation {
    final Configuration loaderConfiguration;
    final Configuration intermediaryMinecraft;
    final Configuration remapClasspathConfiguration;
    final Configuration mappingsClasspath;
    final TaskProvider<WriteFile> writeLog4jConfig;
    final FabricInstallationArtifacts fabricConfigMaker;

    private final CrochetExtension extension;
    private final TaskProvider<MappingsWriter> intermediaryToNamed;
    private final TaskProvider<MappingsWriter> namedToIntermediary;
    final Configuration accessWideners;
    final TaskProvider<ExtractFabricDependencies> extractFabricForDependencies;

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

        this.vanillaConfigMaker.getSidedAnnotation().set(SingleVersionGenerator.Options.SidedAnnotation.FABRIC);
        this.fabricConfigMaker = project.getObjects().newInstance(FabricInstallationArtifacts.class);
        fabricConfigMaker.getWrapped().set(vanillaConfigMaker);

        this.extractFabricForDependencies = project.getTasks().register("crochet"+StringUtils.capitalize(getName())+"ExtractForDependencies", ExtractFabricDependencies.class, task -> {
            task.getOutputDirectory().set(workingDirectory.get().dir("extracted"));
        });
        fabricConfigMaker.getAccessWideners().from(project.fileTree(extractFabricForDependencies.flatMap(ExtractFabricDependencies::getOutputDirectory)).builtBy(extractFabricForDependencies).filter(it -> it.getName().endsWith(".accesswidener")));
        fabricConfigMaker.getInterfaceInjection().from(project.fileTree(extractFabricForDependencies.flatMap(ExtractFabricDependencies::getOutputDirectory)).builtBy(extractFabricForDependencies).filter(it -> it.getName().equals("interface_injections.json")));

        this.binaryArtifactsTask.configure(task -> {
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("downloadClientMappings.output", mappings, project.getObjects()));
            task.getConfigMaker().set(fabricConfigMaker);
        });

        this.remapClasspathConfiguration = project.getConfigurations().register("crochet"+StringUtils.capitalize(getName())+"RemapClasspath", config ->
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_REMAP_CLASSPATH);
            })
        ).get();
        this.remapClasspathConfiguration.setCanBeConsumed(false);

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
        this.binaryArtifactsTask.configure(task -> {
            task.dependsOn(intermediaryMappings);
        });

        this.accessWideners = project.getConfigurations().register(name+"AccessWideners", config -> {
            config.fromDependencyCollector(getDependencies().getAccessWideners());
            config.setCanBeConsumed(false);
        }).get();
        fabricConfigMaker.getAccessWideners().from(accessWideners);

        var intermediaryJar = workingDirectory.map(it -> it.file("intermediary.jar"));
        this.binaryArtifactsTask.configure(task -> {
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
        var reversedChainedSpec = objects.newInstance(ReversedMappingsSource.class);
        reversedChainedSpec.getInputMappings().set(chainedSpec);

        this.intermediaryToNamed = project.getTasks().register("crochet"+StringUtils.capitalize(name)+"IntermediaryToNamed", MappingsWriter.class, task -> {
            task.getInputMappings().set(chainedSpec);
            task.dependsOn(intermediaryMappings);
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
            task.getInputMappings().set(reversedChainedSpec);
            task.dependsOn(intermediaryMappings);
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

        this.intermediaryMinecraft = project.getConfigurations().maybeCreate("crochet"+StringUtils.capitalize(getName())+"IntermediaryMinecraft");

        var intermediaryJarFiles = project.files();
        intermediaryJarFiles.from(intermediaryJar);
        intermediaryJarFiles.builtBy(this.binaryArtifactsTask);
        project.getDependencies().add(intermediaryMinecraft.getName(), intermediaryJarFiles);
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

    @SuppressWarnings("UnstableApiUsage")
    private void forFeatureShared(SourceSet sourceSet, Action<FabricSourceSetDependencies> action, boolean local) {
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(loaderConfiguration);
        });

        var dependencies = project.getObjects().newInstance(FabricSourceSetDependencies.class);
        action.execute(dependencies);

        var modCompileClasspath = project.getConfigurations().register(sourceSet.getTaskName("crochetMod", "compileClasspath"), config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes);
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
            config.setCanBeDeclared(false);
            config.setCanBeConsumed(false);
        }).get();
        var modRuntimeClasspath = project.getConfigurations().register(sourceSet.getTaskName("crochetMod", "runtimeClasspath"), config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName()).getAttributes(), attributes);
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
            config.setCanBeDeclared(false);
            config.setCanBeConsumed(false);
        }).get();

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
        var nonModApiElements = project.getConfigurations().maybeCreate(sourceSet.getTaskName("crochetNonMod", "apiElements"));
        nonModApiElements.setCanBeDeclared(false);

        project.getConfigurations().configureEach(config -> {
            if (config.getName().equals(sourceSet.getSourcesElementsConfigurationName())) {
                config.attributes(attributes -> {
                    attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_IGNORE);
                });
            }
        });

        FeatureUtils.forSourceSetFeature(project, sourceSet.getName(), context -> {
            context.withCapabilities(modRuntimeElements);
            context.withCapabilities(modApiElements);
            context.withCapabilities(nonModRuntimeElements);
            context.withCapabilities(nonModApiElements);

            modRuntimeElements.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_REMAP);
                copyAttributes(runtimeElements.getAttributes(), attributes);
            });
            modRuntimeElements.setCanBeConsumed(true);
            modRuntimeElements.setCanBeResolved(false);

            nonModRuntimeElements.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
                copyAttributes(runtimeElements.getAttributes(), attributes);
            });
            nonModRuntimeElements.setCanBeConsumed(true);
            nonModRuntimeElements.setCanBeResolved(false);

            modApiElements.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_REMAP);
                copyAttributes(apiElements.getAttributes(), attributes);
            });
            modApiElements.setCanBeConsumed(true);
            modApiElements.setCanBeResolved(false);

            nonModApiElements.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
                copyAttributes(apiElements.getAttributes(), attributes);
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

            if (!local) {
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

                    configMaker.getMappings().set(namedToIntermediary.flatMap(MappingsWriter::getOutputMappings));
                    remapJar.getConfigMaker().set(configMaker);

                    remapJar.artifactsConfiguration(project.getConfigurations().getByName(CrochetPlugin.TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME));
                    remapJar.getClasspath().from(project.getConfigurations().named(CrochetPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
                    remapJar.dependsOn(jarTask);
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
            } else {
                nonModApiElements.getOutgoing().getArtifacts().addAllLater(project.provider(apiElements::getAllArtifacts));
                nonModRuntimeElements.getOutgoing().getArtifacts().addAllLater(project.provider(runtimeElements::getAllArtifacts));
            }
        });

        var modCompileOnly = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "compileOnly"));
        modCompileOnly.fromDependencyCollector(dependencies.getModCompileOnly());
        modCompileClasspath.extendsFrom(modCompileOnly);
        var modCompileOnlyApi = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "compileOnlyApi"));
        modCompileOnlyApi.fromDependencyCollector(dependencies.getModCompileOnlyApi());
        modCompileClasspath.extendsFrom(modCompileOnlyApi);
        apiElements.extendsFrom(modCompileOnlyApi);
        modApiElements.extendsFrom(modCompileOnlyApi);
        var modRuntimeOnly = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "runtimeOnly"));
        modRuntimeOnly.fromDependencyCollector(dependencies.getModRuntimeOnly());
        modRuntimeClasspath.extendsFrom(modRuntimeOnly);
        runtimeElements.extendsFrom(modRuntimeOnly);
        modRuntimeElements.extendsFrom(modRuntimeOnly);
        var modLocalRuntime = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "localRuntime"));
        modLocalRuntime.fromDependencyCollector(dependencies.getModLocalRuntime());
        modRuntimeClasspath.extendsFrom(modLocalRuntime);
        var modImplementation = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "implementation"));
        modImplementation.fromDependencyCollector(dependencies.getModImplementation());
        modCompileClasspath.extendsFrom(modImplementation);
        modRuntimeClasspath.extendsFrom(modImplementation);
        runtimeElements.extendsFrom(modImplementation);
        apiElements.extendsFrom(modImplementation);
        var modApi = project.getConfigurations().maybeCreate(sourceSet.getTaskName("mod", "api"));
        modApi.fromDependencyCollector(dependencies.getModApi());
        modCompileClasspath.extendsFrom(modApi);
        modRuntimeClasspath.extendsFrom(modApi);
        runtimeElements.extendsFrom(modApi);
        modRuntimeElements.extendsFrom(modApi);
        apiElements.extendsFrom(modApi);
        modApiElements.extendsFrom(modApi);

        var remappedCompileClasspath = project.getConfigurations().maybeCreate(sourceSet.getTaskName("crochetRemapped", "compileClasspath"));
        project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(remappedCompileClasspath);
        var remappedRuntimeClasspath = project.getConfigurations().maybeCreate(sourceSet.getTaskName("crochetRemapped", "runtimeClasspath"));
        project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName()).extendsFrom(remappedRuntimeClasspath);

        var compileRemappingClasspath = project.getConfigurations().register(sourceSet.getTaskName("crochetRemapping", "compileClasspath"), config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes);
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
            config.setCanBeConsumed(false);
            config.setCanBeDeclared(false);
        }).get();
        var runtimeRemappingClasspath = project.getConfigurations().register(sourceSet.getTaskName("crochetRemapping", "runtimeClasspath"), config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName()).getAttributes(), attributes);
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
            config.setCanBeConsumed(false);
            config.setCanBeDeclared(false);
        }).get();

        project.getConfigurations().named(sourceSet.getCompileClasspathConfigurationName(), config -> {
            config.attributes(attributes ->
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_NON_REMAP)
            );
            config.getDependencies().addAllLater(project.provider(() ->
                modCompileClasspath.getAllDependencies().stream().filter(dep -> dep instanceof ProjectDependency).toList()
            ));
        });
        project.getConfigurations().named(sourceSet.getRuntimeClasspathConfigurationName(), config -> {
            config.attributes(attributes ->
                attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_NON_REMAP)
            );
            config.getDependencies().addAllLater(project.provider(() ->
                modRuntimeClasspath.getAllDependencies().stream().filter(dep -> dep instanceof ProjectDependency).toList()
            ));
        });

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

        var remapCompileMods = project.getTasks().register(sourceSet.getTaskName("crochetRemap", "CompileClasspath"), TaskGraphExecution.class, task -> {
            var configMaker = project.getObjects().newInstance(RemapModsConfigMaker.class);
            configMaker.setup(task, modCompileClasspath, loaderConfiguration, workingDirectory.get().dir("compileClasspath").dir(sourceSet.getName()), remappedCompileMods);
            task.dependsOn(intermediaryToNamed);
            configMaker.getRemappingClasspath().from(minecraft);
            configMaker.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
            task.getConfigMaker().set(configMaker);

            task.copyArtifactsFrom(this.binaryArtifactsTask.get());
            task.getClasspath().from(project.getConfigurations().named(CrochetPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        var remapRuntimeMods = project.getTasks().register(sourceSet.getTaskName("crochetRemap", "RuntimeClasspath"), TaskGraphExecution.class, task -> {
            var configMaker = project.getObjects().newInstance(RemapModsConfigMaker.class);
            configMaker.setup(task, modRuntimeClasspath, loaderConfiguration, workingDirectory.get().dir("runtimeClasspath").dir(sourceSet.getName()), remappedRuntimeMods);
            task.dependsOn(intermediaryToNamed);
            configMaker.getRemappingClasspath().from(minecraft);
            configMaker.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
            task.getConfigMaker().set(configMaker);

            task.copyArtifactsFrom(this.binaryArtifactsTask.get());
            task.getClasspath().from(project.getConfigurations().named(CrochetPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        var remapCompileModSources = project.getTasks().register(sourceSet.getTaskName("crochetRemap", "CompileClasspathSources"), RemapSourceJarsTask.class, task -> {
            task.setup(modCompileClasspath, loaderConfiguration, workingDirectory.get().dir("compileClasspathSources").dir(sourceSet.getName()));
            task.dependsOn(intermediaryToNamed);
            task.getRemappingClasspath().from(minecraft);
            task.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
        });

        var remapRuntimeModSources = project.getTasks().register(sourceSet.getTaskName("crochetRemap", "RuntimeClasspathSources"), RemapSourceJarsTask.class, task -> {
            task.setup(modRuntimeClasspath, loaderConfiguration, workingDirectory.get().dir("runtimeClasspathSources").dir(sourceSet.getName()));
            task.dependsOn(intermediaryToNamed);
            task.getRemappingClasspath().from(minecraft);
            task.getMappings().set(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
        });

        linkSources(remapCompileMods, remapCompileModSources);
        linkSources(remapRuntimeMods, remapRuntimeModSources);

        extension.idePostSync.configure(task -> {
            task.dependsOn(remapCompileMods);
            task.dependsOn(remapRuntimeMods);

            // TODO: these are disabled for now
            //task.dependsOn(remapCompileModSources);
            //task.dependsOn(remapRuntimeModSources);
        });
    }

    private void linkSources(TaskProvider<TaskGraphExecution> remapJars, TaskProvider<RemapSourceJarsTask> remapSourceJars) {
        record Pair<T, U>(T first, U second) implements Serializable {}

        if (IdeaModelHandlerPlugin.isIdeaSyncRelated(project)) {
            Provider<Map<String, ArtifactTarget>> sources = remapSourceJars.get().getTargets().map(targets -> {
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

    @SuppressWarnings({"rawtypes", "DataFlowIssue", "unchecked"})
    private static void copyAttributes(AttributeContainer source, AttributeContainer destination) {
        source.keySet().forEach(key -> destination.attribute((Attribute) key, source.getAttribute(key)));
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
        // TODO: project deps mod groups
        run.classpath.attributes(attributes ->
            // Handle project dependencies correctly, hopefully?
            attributes.attribute(CrochetPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetPlugin.CROCHET_REMAP_TYPE_NON_REMAP)
        );
        run.classpath.extendsFrom(loaderConfiguration);
        run.classpath.extendsFrom(mappingsClasspath);
        run.classpath.extendsFrom(project.getConfigurations().getByName(CrochetPlugin.TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME));
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
            // TODO: remap classpath file
            "-Dlog4j.configurationFile="+project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml").get().getAsFile().getAbsolutePath(),
            "-Dfabric.log.disableAnsi=false",
            "-Dfabric.gameVersion=${minecraft_version}",
            "-Dfabric.remapClasspathFile="+remapClasspathFile.get().getAsFile().getAbsolutePath()
        );
        run.getJvmArgs().add(project.provider(() -> {
            List<String> groups = new ArrayList<>();

            groups.add(
                (run.getAvoidNeedlessDecompilation().get() ? this.binary : this.binaryLineMapped).get().getAsFile().getAbsolutePath()
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
                run.classpath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client"));
                project.afterEvaluate(p -> {
                    if (run.getAvoidNeedlessDecompilation().get()) {
                        run.classpath.extendsFrom(minecraft);
                    } else {
                        run.classpath.extendsFrom(minecraftLineMapped);
                    }
                });
                run.getMainClass().convention("net.fabricmc.loader.impl.launch.knot.KnotClient");
                run.getArgs().addAll(
                    "--assetIndex",
                    "${assets_index_name}",
                    "--assetsDir",
                    "${assets_root}"
                );
            }
            case SERVER -> {
                run.classpath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server"));
                project.afterEvaluate(p -> {
                    if (run.getAvoidNeedlessDecompilation().get()) {
                        run.classpath.extendsFrom(minecraft);
                    } else {
                        run.classpath.extendsFrom(minecraftLineMapped);
                    }
                });
                run.getMainClass().convention("net.fabricmc.loader.impl.launch.knot.KnotClient");
            }
        }
    }
}
