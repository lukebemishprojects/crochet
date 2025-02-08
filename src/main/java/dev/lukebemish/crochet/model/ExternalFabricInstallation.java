package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.Log4jSetup;
import dev.lukebemish.crochet.internal.TaskUtils;
import dev.lukebemish.crochet.internal.tasks.MakeRemapClasspathFile;
import dev.lukebemish.crochet.internal.tasks.TaskGraphExecution;
import dev.lukebemish.crochet.internal.tasks.WriteFile;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedSet;
import java.util.stream.Collectors;

public abstract class ExternalFabricInstallation extends ExternalAbstractVanillaInstallation {
    private final FabricInstallationLogic logic;

    private final DependencyScopeConfiguration loader;
    private final DependencyScopeConfiguration intermediaryMinecraft;
    private final DependencyScopeConfiguration mappingsIntermediaryNamed;
    private final ResolvableConfiguration mappingsIntermediaryNamedPath;
    private final DependencyScopeConfiguration mappingsNamedIntermediary;
    private final ResolvableConfiguration mappingsNamedIntermediaryPath;
    private final DependencyScopeConfiguration injectedInterfaces;
    private final ResolvableConfiguration injectedInterfacesPath;

    private final DependencyScopeConfiguration compileRemapped;
    private final DependencyScopeConfiguration compileExclude;
    private final DependencyScopeConfiguration runtimeRemapped;
    private final DependencyScopeConfiguration runtimeExclude;

    private final ResolvableConfiguration mappingsClasspath;

    final FileCollection writeLog4jConfig;

    @Inject
    public ExternalFabricInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.loader = ConfigurationUtils.dependencyScopeInternal(this, name, "loader", c -> {});
        this.intermediaryMinecraft = ConfigurationUtils.dependencyScopeInternal(this, name, "intermediaryMinecraft", c -> {});
        this.mappingsIntermediaryNamed = ConfigurationUtils.dependencyScopeInternal(this, name, "mappingsIntermediaryNamed", c -> {});
        this.mappingsIntermediaryNamedPath = ConfigurationUtils.resolvableInternal(this, name, "mappingsIntermediaryNamedPath", c -> {
            c.extendsFrom(this.mappingsIntermediaryNamed);
        });
        this.mappingsNamedIntermediary = ConfigurationUtils.dependencyScopeInternal(this, name, "mappingsNamedIntermediary", c -> {});
        this.mappingsNamedIntermediaryPath = ConfigurationUtils.resolvableInternal(this, name, "mappingsNamedIntermediaryPath", c -> {
            c.extendsFrom(this.mappingsNamedIntermediary);
        });
        this.injectedInterfaces = ConfigurationUtils.dependencyScopeInternal(this, name, "injectedInterfaces", c -> {});
        this.injectedInterfacesPath = ConfigurationUtils.resolvableInternal(this, name, "injectedInterfacesPath", c -> {
            c.extendsFrom(this.injectedInterfaces);
        });

        this.compileRemapped = ConfigurationUtils.dependencyScopeInternal(this, name, "compileRemapped", c -> {});
        this.compileExclude = ConfigurationUtils.dependencyScopeInternal(this, name, "compileExclude", c -> {});
        this.runtimeRemapped = ConfigurationUtils.dependencyScopeInternal(this, name, "runtimeRemapped", c -> {});
        this.runtimeExclude = ConfigurationUtils.dependencyScopeInternal(this, name, "runtimeExclude", c -> {});

        this.nonUpgradableClientCompileVersioning.extendsFrom(compileExclude);
        this.nonUpgradableClientRuntimeVersioning.extendsFrom(runtimeExclude);

        this.nonUpgradableServerCompileVersioning.extendsFrom(compileExclude);
        this.nonUpgradableServerRuntimeVersioning.extendsFrom(runtimeExclude);

        var workingDirectory = extension.project.getLayout().getBuildDirectory().dir("crochet/installations/" + name);

        var writeLog4jConfigTask = TaskUtils.registerInternal(this, WriteFile.class, name, "writeLog4jConfig", task -> {
            task.getContents().convention(
                Log4jSetup.FABRIC_CONFIG
            );
            task.getOutputFile().convention(extension.project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml"));
        });
        this.writeLog4jConfig = extension.project.files(writeLog4jConfigTask.flatMap(WriteFile::getOutputFile)).builtBy(writeLog4jConfigTask);

        var mappings = ConfigurationUtils.dependencyScopeInternal(this, name, "mappings", c -> {});
        this.mappingsClasspath = ConfigurationUtils.resolvableInternal(this, name, "mappingsClasspath", c -> {
            c.extendsFrom(mappings);
        });
        var mappingsJar = TaskUtils.registerInternal(this, Jar.class, name, "mappingsJar", task -> {
            task.getDestinationDirectory().set(workingDirectory);
            task.getArchiveFileName().set("intermediary-mappings.jar");
            task.from(mappingsIntermediaryNamedPath, spec -> {
                spec.into("mappings");
                spec.rename(s -> "mappings.tiny");
            });
            task.dependsOn(mappingsIntermediaryNamedPath);
        });
        var mappingsJarFiles = extension.project.files(mappingsJar.map(Jar::getArchiveFile));
        mappingsJarFiles.builtBy(mappingsJar);
        extension.project.getDependencies().add(mappings.getName(), mappingsJarFiles);

        this.logic = new FabricInstallationLogic(this, extension.project) {
            @Override
            protected DependencyScopeConfiguration loaderConfiguration() {
                return loader;
            }

            @Override
            protected DependencyScopeConfiguration intermediaryMinecraft() {
                return intermediaryMinecraft;
            }

            @Override
            protected void extractFabricForDependencies(ResolvableConfiguration modCompileClasspath, ResolvableConfiguration modRuntimeClasspath) {
                // We do not pull AWs or IIs from these dependencies
            }

            @Override
            protected Provider<RegularFile> namedToIntermediary() {
                var files = extension.project.files(mappingsNamedIntermediaryPath);
                return extension.project.getLayout().file(extension.project.provider(files::getSingleFile));
            }

            @Override
            protected Provider<RegularFile> intermediaryToNamed() {
                var files = extension.project.files(mappingsIntermediaryNamedPath);
                return extension.project.getLayout().file(extension.project.provider(files::getSingleFile));
            }

            @Override
            protected FileCollection namedToIntermediaryFlat() {
                return extension.project.files(mappingsNamedIntermediaryPath);
            }

            @Override
            protected FileCollection intermediaryToNamedFlat() {
                return extension.project.files(mappingsIntermediaryNamedPath);
            }

            @Override
            protected Configuration compileExclude() {
                return compileExclude;
            }

            @Override
            protected Configuration runtimeExclude() {
                return runtimeExclude;
            }

            @Override
            protected Configuration compileRemapped() {
                return compileRemapped;
            }

            @Override
            protected Configuration runtimeRemapped() {
                return runtimeRemapped;
            }

            @Override
            protected void includeInterfaceInjections(ConfigurableFileCollection interfaceInjectionFiles) {
                interfaceInjectionFiles.from(injectedInterfacesPath);
            }

            @Override
            protected Provider<Directory> workingDirectory() {
                return workingDirectory;
            }

            @Override
            protected void addArtifacts(TaskGraphExecution task) {
                task.artifactsConfiguration(extension.project.getConfigurations().getByName(CrochetProjectPlugin.TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME));
            }
        };
    }

    public void consume(SharedInstallation sharedInstallation) {
        super.consume(sharedInstallation);
        this.useBundle(crochetExtension.getBundle(sharedInstallation.getName()));
    }

    void useBundle(FabricDependencyBundle bundle) {
        this.bundleActions.add(bundle.action);

        for (var entry : Map.of(
            "compile-remapped", compileRemapped,
            "compile-exclude", compileExclude,
            "runtime-remapped", runtimeRemapped,
            "runtime-exclude", runtimeExclude
        ).entrySet()) {
            var group = CROSS_PROJECT_BUNDLE_CAPABILITY_GROUP + sharingInstallationTypeTag();
            var dep = (ModuleDependency) crochetExtension.project.getDependencies().project(Map.of("path", ":"));
            dep.capabilities(caps -> {
                caps.requireCapability(group + ":" + bundle.getName() + "-" + entry.getKey());
            });
            dep.attributes(attribute -> {
                attribute.attributeProvider(CrochetProjectPlugin.LOCAL_DISTRIBUTION_ATTRIBUTE, getDistribution().map(it -> it.name().toLowerCase(Locale.ROOT)));
            });
            dep.endorseStrictVersions();
            crochetExtension.project.getDependencies().add(
                entry.getValue().getName(),
                dep
            );
        }
    }

    private final List<Action<? super FabricSourceSetDependencies>> bundleActions = new ArrayList<>();

    @Override
    protected Map<String, Configuration> getConfigurationsToLink() {
        var map = new LinkedHashMap<>(super.getConfigurationsToLink());
        map.put("loader", loader);
        map.put("intermediary", intermediaryMinecraft);
        map.put("mappings-intermediary-named", mappingsIntermediaryNamed);
        map.put("mappings-named-intermediary", mappingsNamedIntermediary);
        map.put("injected-interfaces", injectedInterfaces);
        return map;
    }

    @Override
    protected String sharingInstallationTypeTag() {
        return "fabric";
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

    private void forFeatureShared(SourceSet sourceSet, Action<FabricSourceSetDependencies> action, boolean local) {
        var dependencies = crochetExtension.project.getObjects().newInstance(FabricSourceSetDependencies.class);
        action.execute(dependencies);

        logic.forFeatureShared(sourceSet, dependencies, local);
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

        var remapClasspathFile = crochetExtension.project.getLayout().getBuildDirectory().file("crochet/runs/"+run.getName()+"/remapClasspath.txt");
        var remapClasspath = crochetExtension.project.getTasks().register("crochet"+ StringUtils.capitalize(getName())+StringUtils.capitalize(run.getName())+"RemapClasspath", MakeRemapClasspathFile.class, task -> {
            task.getRemapClasspathFile().set(remapClasspathFile);
            task.getRemapClasspath().from(remapClasspathConfiguration);
            task.dependsOn(mappingsClasspath);
        });
        run.argFilesTask.configure(task -> {
            task.dependsOn(remapClasspath);
        });
        run.getJvmArgs().addAll(
            "-Dfabric.development=true",
            "-Dlog4j.configurationFile="+crochetExtension.project.getLayout().getBuildDirectory().file("crochet/installations/"+this.getName()+"/log4j2.xml").get().getAsFile().getAbsolutePath(),
            "-Dfabric.log.disableAnsi=false",
            "-Dfabric.gameVersion=${minecraft_version}",
            "-Dfabric.remapClasspathFile="+remapClasspathFile.get().getAsFile().getAbsolutePath()
        );
        Provider<SequencedSet<File>> excluded = crochetExtension.project.provider(() -> {
            var excludesSet = new LinkedHashSet<File>();
            if (run.getAvoidNeedlessDecompilation().get()) {
                excludesSet.addAll(this.binaryPath.getFiles());
            } else {
                excludesSet.addAll(this.binaryLineMappedPath.getFiles());
            }
            excludesSet.addAll(this.resourcesPath.getFiles());
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

        crochetExtension.project.afterEvaluate(p -> {
            if (run.getAvoidNeedlessDecompilation().get()) {
                run.classpath.extendsFrom(minecraft);
            } else {
                run.classpath.extendsFrom(minecraftLineMapped);
            }
        });

        run.classpath.extendsFrom(loader);
        run.classpath.extendsFrom(crochetExtension.project.getConfigurations().getByName(CrochetProjectPlugin.TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME));
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
}
