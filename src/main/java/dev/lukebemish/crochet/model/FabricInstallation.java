package dev.lukebemish.crochet.model;

import com.google.common.base.Suppliers;
import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.Log4jSetup;
import dev.lukebemish.crochet.internal.Memoize;
import dev.lukebemish.crochet.internal.TaskUtils;
import dev.lukebemish.crochet.internal.tasks.ExtractFabricDependencies;
import dev.lukebemish.crochet.internal.tasks.FabricInstallationArtifacts;
import dev.lukebemish.crochet.internal.tasks.MakeRemapClasspathFile;
import dev.lukebemish.crochet.internal.tasks.MappingsWriter;
import dev.lukebemish.crochet.internal.tasks.TaskGraphExecution;
import dev.lukebemish.crochet.internal.tasks.WriteFile;
import dev.lukebemish.taskgraphrunner.model.conversion.SingleVersionGenerator;
import net.neoforged.srgutils.IMappingFile;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.attributes.Category;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class FabricInstallation extends AbstractVanillaInstallation {
    static final String ACCESS_WIDENER_CATEGORY = "accesswidener";

    final DependencyScopeConfiguration loaderConfiguration;
    final DependencyScopeConfiguration intermediaryMinecraft;
    final Configuration mappingsClasspath;
    final FileCollection writeLog4jConfig;
    final FabricInstallationArtifacts fabricConfigMaker;

    private final CrochetExtension extension;
    private final TaskProvider<MappingsWriter> intermediaryToNamed;
    private final TaskProvider<MappingsWriter> namedToIntermediary;
    final Configuration accessWideners;
    final Configuration accessWidenersPath;
    final Supplier<Configuration> accessWidenersElements;
    final TaskProvider<ExtractFabricDependencies> extractFabricForDependencies;

    final Memoize<ConsumableConfiguration> compileExclude;
    final Memoize<ConsumableConfiguration> compileRemapped;
    final Memoize<ConsumableConfiguration> runtimeExclude;
    final Memoize<ConsumableConfiguration> runtimeRemapped;

    private final FabricInstallationLogic logic;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public FabricInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.extension = extension;

        var writeLog4jConfigTask = TaskUtils.registerInternal(this, WriteFile.class, name, "writeLog4jConfig", task -> {
            task.getContents().convention(
                Log4jSetup.FABRIC_CONFIG
            );
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml"));
        });
        this.writeLog4jConfig = extension.project.files(writeLog4jConfigTask.flatMap(WriteFile::getOutputFile)).builtBy(writeLog4jConfigTask);

        this.vanillaConfigMaker.getSidedAnnotation().set(SingleVersionGenerator.Options.SidedAnnotation.FABRIC);
        this.fabricConfigMaker = project.getObjects().newInstance(FabricInstallationArtifacts.class);
        fabricConfigMaker.getWrapped().set(vanillaConfigMaker);

        this.extractFabricForDependencies = TaskUtils.registerInternal(this, ExtractFabricDependencies.class, name, "extractFromFabricDependencies", task -> {
            task.getOutputDirectory().set(workingDirectory.get().dir("extracted"));
        });
        fabricConfigMaker.getAccessWideners().from(project.fileTree(extractFabricForDependencies.flatMap(ExtractFabricDependencies::getOutputDirectory)).builtBy(extractFabricForDependencies).filter(it -> it.getName().endsWith(".accesswidener")));
        fabricConfigMaker.getInterfaceInjection().from(project.fileTree(extractFabricForDependencies.flatMap(ExtractFabricDependencies::getOutputDirectory)).builtBy(extractFabricForDependencies).filter(it -> it.getName().equals("interface_injections.json")));
        project.getDependencies().add(this.injectedInterfaces.getName(), project.fileTree(extractFabricForDependencies.flatMap(ExtractFabricDependencies::getOutputDirectory)).builtBy(extractFabricForDependencies).filter(it -> it.getName().equals("neo_interface_injections.json")));

        var intermediaryToNamedFile = workingDirectory.map(dir -> dir.file("runner-intermediary-to-named.tiny"));
        var namedToIntermediaryFile = workingDirectory.map(dir -> dir.file("runner-named-to-intermediary.tiny"));

        this.binaryArtifactsTask.configure(task -> {
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("intermediaryToNamedMappings.output", intermediaryToNamedFile, project.getObjects()));
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("namedToIntermediaryMappings.output", namedToIntermediaryFile, project.getObjects()));
            task.getConfigMaker().set(fabricConfigMaker);
        });

        this.loaderConfiguration = ConfigurationUtils.dependencyScope(this, name, null, "loader", c -> {
            c.fromDependencyCollector(getDependencies().getLoader());
        });
;
        this.nonUpgradableDependencies.extendsFrom(this.loaderConfiguration);

        this.getDependencies().getIntermediary().add(project.provider(() ->
            this.getDependencies().module("net.fabricmc", "intermediary", this.getMinecraft().get()))
        );

        var intermediaryConfiguration = ConfigurationUtils.dependencyScopeInternal(this, name, "intermediary", c -> {
            c.fromDependencyCollector(getDependencies().getIntermediary());
        });
        var intermediaryConfigurationClasspath = ConfigurationUtils.resolvableInternal(this, name, "intermediaryClasspath", c -> {
            c.extendsFrom(intermediaryConfiguration);
        });

        var intermediaryMappings = TaskUtils.registerInternal(this, Copy.class, name, "extractIntermediaryMappings", task -> {
            task.from(project.zipTree(intermediaryConfigurationClasspath.getSingleFile()));
            task.setDestinationDir(workingDirectory.get().dir("intermediary").getAsFile());
        });

        fabricConfigMaker.getIntermediary().fileProvider(intermediaryMappings.map(t -> t.getDestinationDir().toPath().resolve("mappings").resolve("mappings.tiny").toFile()));
        this.binaryArtifactsTask.configure(task -> {
            task.dependsOn(intermediaryMappings);
        });

        this.accessWideners = ConfigurationUtils.dependencyScope(this, name, null, "accessWideners", c -> {
            c.fromDependencyCollector(getDependencies().getAccessWideners());
        });
        this.accessWidenersElements = Suppliers.memoize(() -> ConfigurationUtils.consumable(this, name, null, "accessWidenersElements", c -> {
            c.extendsFrom(accessWideners);
        }));
        this.accessWidenersPath = ConfigurationUtils.resolvableInternal(this, name, "accessWidenersPath", c -> {
            c.extendsFrom(accessWideners);
            c.attributes(attributes ->
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_WIDENER_CATEGORY))
            );
        });
        fabricConfigMaker.getAccessWideners().from(accessWidenersPath);

        var intermediaryJar = workingDirectory.map(it -> it.file("intermediary.jar"));
        this.binaryArtifactsTask.configure(task -> {
            task.getTargets().add(TaskGraphExecution.GraphOutput.of("intermediary", intermediaryJar, project.getObjects()));
        });

        // To remap dependencies, we need a intermediary -> mojmaps + srg mapping.
        // We have intermediary -> official from intermediary, and official -> srg + mojmaps from neoform
        this.intermediaryToNamed = TaskUtils.registerInternal(this, MappingsWriter.class, name, "intermediaryToNamed", task -> {
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

        this.namedToIntermediary = TaskUtils.registerInternal(this, MappingsWriter.class, name, "namedToIntermediary", task -> {
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

        var mappings = ConfigurationUtils.dependencyScopeInternal(this, name, "mappings", c -> {});
        this.mappingsClasspath = ConfigurationUtils.resolvableInternal(this, name, "mappingsClasspath", c -> {
            c.extendsFrom(mappings);
        });
        var mappingsJar = TaskUtils.registerInternal(this, Jar.class, name, "mappingsJar", task -> {
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
        project.getDependencies().add(mappings.getName(), mappingsJarFiles);

        this.intermediaryMinecraft = ConfigurationUtils.dependencyScopeInternal(this, name, "intermediaryMinecraft", config -> {
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
        });

        var intermediaryJarFiles = project.files();
        intermediaryJarFiles.from(intermediaryJar);
        intermediaryJarFiles.builtBy(this.binaryArtifactsTask);
        project.getDependencies().add(intermediaryMinecraft.getName(), intermediaryJarFiles);

        this.compileRemapped = Memoize.of(() -> ConfigurationUtils.consumableInternal(this, name, "compileRemapped", config -> {
            var group = CROSS_PROJECT_BUNDLE_CAPABILITY_GROUP + sharingInstallationTypeTag();
            config.getOutgoing().capability(group + ":" + name + "-compile-remapped" + ":" + "1.0.0");
            config.getAttributes().attributeProvider(CrochetProjectPlugin.LOCAL_DISTRIBUTION_ATTRIBUTE, getDistribution().map(it -> it.name().toLowerCase(Locale.ROOT)));
        }));
        this.compileExclude = Memoize.of(() -> ConfigurationUtils.consumableInternal(this, name, "compileExclude", config -> {
            var group = CROSS_PROJECT_BUNDLE_CAPABILITY_GROUP + sharingInstallationTypeTag();
            config.getOutgoing().capability(group + ":" + name + "-compile-exclude" + ":" + "1.0.0");
            config.getAttributes().attributeProvider(CrochetProjectPlugin.LOCAL_DISTRIBUTION_ATTRIBUTE, getDistribution().map(it -> it.name().toLowerCase(Locale.ROOT)));
        }));
        this.runtimeRemapped = Memoize.of(() -> ConfigurationUtils.consumableInternal(this, name, "runtimeRemapped", config -> {
            var group = CROSS_PROJECT_BUNDLE_CAPABILITY_GROUP + sharingInstallationTypeTag();
            config.getOutgoing().capability(group + ":" + name + "-runtime-remapped" + ":" + "1.0.0");
            config.getAttributes().attributeProvider(CrochetProjectPlugin.LOCAL_DISTRIBUTION_ATTRIBUTE, getDistribution().map(it -> it.name().toLowerCase(Locale.ROOT)));
        }));
        this.runtimeExclude = Memoize.of(() -> ConfigurationUtils.consumableInternal(this, name, "runtimeExclude", config -> {
            var group = CROSS_PROJECT_BUNDLE_CAPABILITY_GROUP + sharingInstallationTypeTag();
            config.getOutgoing().capability(group + ":" + name + "-runtime-exclude" + ":" + "1.0.0");
            config.getAttributes().attributeProvider(CrochetProjectPlugin.LOCAL_DISTRIBUTION_ATTRIBUTE, getDistribution().map(it -> it.name().toLowerCase(Locale.ROOT)));
        }));

        this.logic = new FabricInstallationLogic(this, project) {
            @Override
            protected DependencyScopeConfiguration loaderConfiguration() {
                return loaderConfiguration;
            }

            @Override
            protected DependencyScopeConfiguration intermediaryMinecraft() {
                return intermediaryMinecraft;
            }

            @Override
            protected void extractFabricForDependencies(ResolvableConfiguration modCompileClasspath, ResolvableConfiguration modRuntimeClasspath) {
                FabricInstallation.this.extractFabricForDependencies.configure(task -> {
                    task.getCompileModJars().from(modCompileClasspath);
                    task.getRuntimeModJars().from(modRuntimeClasspath);
                });
            }

            @Override
            protected Provider<RegularFile> namedToIntermediary() {
                return namedToIntermediary.flatMap(MappingsWriter::getOutputMappings);
            }

            @Override
            protected Provider<RegularFile> intermediaryToNamed() {
                return intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings);
            }

            @Override
            protected FileCollection namedToIntermediaryFlat() {
                var files = project.files();
                files.from(namedToIntermediary.flatMap(MappingsWriter::getOutputMappings));
                files.builtBy(namedToIntermediary);
                return files;
            }

            @Override
            protected FileCollection intermediaryToNamedFlat() {
                var files = project.files();
                files.from(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings));
                files.builtBy(intermediaryToNamed);
                return files;
            }

            @Override
            protected Configuration compileExclude() {
                return null;
            }

            @Override
            protected Configuration runtimeExclude() {
                return null;
            }

            @Override
            protected Configuration compileRemapped() {
                return null;
            }

            @Override
            protected Configuration runtimeRemapped() {
                return null;
            }

            @Override
            protected void includeInterfaceInjections(ConfigurableFileCollection interfaceInjectionFiles) {
                interfaceInjectionFiles.from(injectedInterfacesElements.get().getOutgoing().getArtifacts().getFiles());
                interfaceInjectionFiles.builtBy(injectedInterfacesElements.get().getOutgoing().getArtifacts().getBuildDependencies());
            }

            @Override
            protected Provider<Directory> workingDirectory() {
                return workingDirectory;
            }

            @Override
            protected void addArtifacts(TaskGraphExecution task) {
                task.artifactsConfiguration(project.getConfigurations().getByName(CrochetProjectPlugin.TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME));
            }
        };
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
        forFeatureShared(sourceSet, deps -> {
            for (var bundleAction : bundleActions) {
                bundleAction.execute(deps);
            }
            action.execute(deps);
        }, false);
    }

    public void forLocalFeature(SourceSet sourceSet, Action<FabricSourceSetDependencies> action) {
        super.forLocalFeature(sourceSet);
        forFeatureShared(sourceSet, deps -> {
            for (var bundleAction : bundleActions) {
                bundleAction.execute(deps);
            }
            action.execute(deps);
        }, true);
    }

    @Override
    protected List<String> getInstallationConfigurationNames() {
        var out = new ArrayList<>(super.getInstallationConfigurationNames());
        out.add("AccessWideners");
        return out;
    }

    void makeBundle(FabricDependencyBundle bundle) {
        FabricRemapDependencies dependencies = project.getObjects().newInstance(FabricRemapDependencies.class);
        bundle.action.execute(dependencies);
        this.bundleActions.add(bundle.action);

        logic.forNamedBundle(
            "crochetBundle"+StringUtils.capitalize(bundle.getName()),
            dependencies,
            compileRemapped.get(),
            compileExclude.get(),
            runtimeRemapped.get(),
            runtimeExclude.get()
        );
        compileRemapped.fix();
        runtimeRemapped.fix();
    }

    private final List<Action<? super FabricSourceSetDependencies>> bundleActions = new ArrayList<>();

    private void forFeatureShared(SourceSet sourceSet, Action<FabricSourceSetDependencies> action, boolean local) {
        FeatureUtils.forSourceSetFeature(project, sourceSet.getName(), context -> {
            context.withCapabilities(accessWidenersElements.get());
            accessWidenersElements.get().attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, ACCESS_WIDENER_CATEGORY));
            });
        });

        var dependencies = project.getObjects().newInstance(FabricSourceSetDependencies.class);
        action.execute(dependencies);

        var out = logic.forFeatureShared(sourceSet, dependencies, local);
        var modCompileClasspath = out.modCompileClasspath();
        var modRuntimeClasspath = out.modRuntimeClasspath();

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
    }

    @Override
    protected boolean canPublishInjectedInterfaces() {
        return false;
    }

    @Override
    protected Map<String, Configuration> makeConfigurationsToLink() {
        var map = new LinkedHashMap<>(super.makeConfigurationsToLink());
        map.put("loader", ConfigurationUtils.consumableInternal(this, getName(), "loaderElements", config -> {
            config.extendsFrom(loaderConfiguration);
        }));
        map.put("intermediary", ConfigurationUtils.consumableInternal(this, getName(), "intermediaryMinecraftElements", config -> {
            config.extendsFrom(intermediaryMinecraft);
        }));
        map.put("mappings-intermediary-named", ConfigurationUtils.consumableInternal(this, getName(), "intermediaryToNamedMappings", config -> {
            config.getOutgoing().artifact(intermediaryToNamed.flatMap(MappingsWriter::getOutputMappings), artifact -> {
                artifact.builtBy(intermediaryToNamed);
            });
        }));
        map.put("mappings-named-intermediary", ConfigurationUtils.consumableInternal(this, getName(), "namedToIntermediaryMappings", config -> {
            config.getOutgoing().artifact(namedToIntermediary.flatMap(MappingsWriter::getOutputMappings), artifact -> {
                artifact.builtBy(namedToIntermediary);
            });
        }));
        map.put("injected-interfaces", ConfigurationUtils.consumableInternal(this, getName(), "sharedInjectedInterfacesElements", config -> {
            injectedInterfacesElements.configure(injectedInterfacesElements -> {
                config.getOutgoing().getArtifacts().addAllLater(project.provider(() -> injectedInterfacesElements.getOutgoing().getArtifacts()));
            });
        }));
        return map;
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
        var remapClasspathConfiguration = logic.forRunRemapping(run, runType);

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
            runType.distribution().apply(attributes);
        });

        project.afterEvaluate(p -> {
            if (run.getAvoidNeedlessDecompilation().get()) {
                run.classpath.extendsFrom(minecraft);
            } else {
                run.classpath.extendsFrom(minecraftLineMapped);
            }
        });

        run.classpath.extendsFrom(loaderConfiguration);
        run.classpath.extendsFrom(project.getConfigurations().getByName(CrochetProjectPlugin.TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME));
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
