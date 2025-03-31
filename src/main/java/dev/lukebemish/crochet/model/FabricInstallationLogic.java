package dev.lukebemish.crochet.model;

import com.google.common.base.Suppliers;
import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.IdeaModelHandlerPlugin;
import dev.lukebemish.crochet.internal.InheritanceMarker;
import dev.lukebemish.crochet.internal.TaskUtils;
import dev.lukebemish.crochet.internal.tasks.ArtifactTarget;
import dev.lukebemish.crochet.internal.tasks.RemapModsConfigMaker;
import dev.lukebemish.crochet.internal.tasks.RemapModsSourcesConfigMaker;
import dev.lukebemish.crochet.internal.tasks.TaskGraphExecution;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static dev.lukebemish.crochet.internal.ConfigurationUtils.copyAttributes;

abstract class FabricInstallationLogic {
    private final MinecraftInstallation minecraftInstallation;
    private final Project project;

    FabricInstallationLogic(MinecraftInstallation minecraftInstallation, Project project) {
        this.minecraftInstallation = minecraftInstallation;
        this.project = project;
    }

    protected abstract DependencyScopeConfiguration loaderConfiguration();
    protected abstract DependencyScopeConfiguration intermediaryMinecraft();

    protected abstract void extractFabricForDependencies(
        ResolvableConfiguration modCompileClasspath,
        ResolvableConfiguration modRuntimeClasspath
    );

    protected abstract Provider<RegularFile> namedToIntermediary();
    protected abstract Provider<RegularFile> intermediaryToNamed();
    protected abstract FileCollection namedToIntermediaryFlat();
    protected abstract FileCollection intermediaryToNamedFlat();

    protected abstract @Nullable Configuration compileExclude();
    protected abstract @Nullable Configuration runtimeExclude();
    protected abstract @Nullable Configuration compileRemapped();
    protected abstract @Nullable Configuration runtimeRemapped();

    protected abstract void includeInterfaceInjections(ConfigurableFileCollection interfaceInjectionFiles);

    protected abstract Provider<Directory> workingDirectory();

    private static final List<String> MOD_CONFIGURATION_NAMES = List.of(
        "compileOnly",
        "compileOnlyApi",
        "runtimeOnly",
        "implementation",
        "api",
        "localRuntime",
        "localImplementation"
    );

    record OutConfigurations(ResolvableConfiguration modCompileClasspath, ResolvableConfiguration modRuntimeClasspath) {}

    private String getTaskName(String name, @Nullable String prefix, @Nullable String suffix) {
        return StringUtils.uncapitalize((prefix == null ? "" : StringUtils.capitalize(prefix)) + StringUtils.capitalize(name) + (suffix == null ? "" : StringUtils.capitalize(suffix)));
    }

    private boolean bundled = false;

    void forNamedBundle(String name, FabricRemapDependencies dependencies, ConsumableConfiguration remappedCompileClasspath, ConsumableConfiguration excludeCompile, ConsumableConfiguration remappedRuntimeClasspath, ConsumableConfiguration excludeRuntime) {
        if (bundled) {
            throw new IllegalStateException("Bundle already made for installation "+minecraftInstallation.getName()+" in "+project.getPath());
        } else {
            bundled = true;
        }
        /*
        General architecture:
        - modCompileClasspath and modRuntimeClasspath -- collect dependencies for remapping. Resolve remap type "to-remap"
        - remappedCompileClasspath and remappedRuntimeClasspath -- output of remapping

        To handle exclusions, we make another classpath:
        - excludedCompileClasspath -- compileClasspath with only project/module dependencies
        - excludedRuntimeClasspath -- compileClasspath with only project/module dependencies

        And finally, to handle versioning right, we make a version-determining classpath:
        - versioningCompileClasspath
        - versioningRuntimeClasspath
        And have every resolving configuration, compileClasspath and modCompileClasspath, be shouldResolveConsistentlyWith it
         */

        Action<AttributeContainer> compileAttributes = attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
            InstallationDistribution.applyLazy(minecraftInstallation.getDistribution(), attributes);
        };

        Action<AttributeContainer> runtimeAttributes = attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
            InstallationDistribution.applyLazy(minecraftInstallation.getDistribution(), attributes);
        };

        var modCompileClasspath = ConfigurationUtils.resolvableInternal(project, name, "modCompileClasspath", config -> {
            config.attributes(attributes -> {
                compileAttributes.execute(attributes);
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
        });
        var modRuntimeClasspath = ConfigurationUtils.resolvableInternal(project, name, "modRuntimeClasspath", config -> {
            config.attributes(attributes -> {
                runtimeAttributes.execute(attributes);
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
        });

        var versioningCompileClasspath = ConfigurationUtils.resolvableInternal(project, name, "versioningCompileClasspath", config -> {
            config.attributes(attributes -> {
                // Does not have the remap type attribute at this point
                compileAttributes.execute(attributes);
            });
            config.shouldResolveConsistentlyWith(switch (minecraftInstallation.getDistribution().get()) {
                case CLIENT, JOINED -> minecraftInstallation.nonUpgradableClientCompileVersioning;
                case SERVER, COMMON -> minecraftInstallation.nonUpgradableServerCompileVersioning;
            });
        });

        var versioningRuntimeClasspath = ConfigurationUtils.resolvableInternal(project, name, "versioningRuntimeClasspath", config -> {
            config.attributes(attributes -> {
                // Does not have the remap type attribute at this point
                runtimeAttributes.execute(attributes);
            });
            config.shouldResolveConsistentlyWith(switch (minecraftInstallation.getDistribution().get()) {
                case CLIENT, JOINED -> minecraftInstallation.nonUpgradableClientRuntimeVersioning;
                case SERVER, COMMON -> minecraftInstallation.nonUpgradableServerRuntimeVersioning;
            });
        });

        var excludedCompileClasspath = ConfigurationUtils.resolvableInternal(project, name, "excludedCompileClasspath", config -> {
            config.attributes(attributes -> {
                compileAttributes.execute(attributes);
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
            });
        });

        var excludedRuntimeClasspath = ConfigurationUtils.resolvableInternal(project, name, "excludedRuntimeClasspath", config -> {
            config.attributes(attributes -> {
                runtimeAttributes.execute(attributes);
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
            });
        });

        excludedCompileClasspath.attributes(attributes -> {
            compileAttributes.execute(attributes);
            attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
        });

        excludedRuntimeClasspath.attributes(attributes -> {
            runtimeAttributes.execute(attributes);
            attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
        });

        excludedCompileClasspath.extendsFrom(loaderConfiguration());
        excludedCompileClasspath.extendsFrom(minecraftInstallation.minecraftDependencies);
        excludedRuntimeClasspath.extendsFrom(loaderConfiguration());
        excludedRuntimeClasspath.extendsFrom(minecraftInstallation.minecraftDependencies);

        versioningCompileClasspath.extendsFrom(modCompileClasspath);
        versioningCompileClasspath.extendsFrom(loaderConfiguration());
        versioningCompileClasspath.extendsFrom(minecraftInstallation.minecraftDependencies);
        versioningRuntimeClasspath.extendsFrom(modRuntimeClasspath);
        versioningRuntimeClasspath.extendsFrom(loaderConfiguration());
        versioningRuntimeClasspath.extendsFrom(minecraftInstallation.minecraftDependencies);

        modCompileClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);
        excludedCompileClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);

        modRuntimeClasspath.shouldResolveConsistentlyWith(versioningRuntimeClasspath);
        excludedRuntimeClasspath.shouldResolveConsistentlyWith(versioningRuntimeClasspath);

        excludeRuntime.extendsFrom(modRuntimeClasspath);

        excludeCompile.extendsFrom(modCompileClasspath);

        extractFabricForDependencies(modCompileClasspath, modRuntimeClasspath);

        var modCompileOnly = ConfigurationUtils.dependencyScope(project, name, "mod", "compileOnly", c -> {});
        modCompileOnly.fromDependencyCollector(dependencies.getModCompileOnly());
        modCompileClasspath.extendsFrom(modCompileOnly);
        var modCompileOnlyApi = ConfigurationUtils.dependencyScope(project, name, "mod", "compileOnlyApi", c -> {});
        modCompileOnlyApi.fromDependencyCollector(dependencies.getModCompileOnlyApi());
        modCompileClasspath.extendsFrom(modCompileOnlyApi);
        var modRuntimeOnly = ConfigurationUtils.dependencyScope(project, name, "mod", "runtimeOnly", c -> {});
        modRuntimeOnly.fromDependencyCollector(dependencies.getModRuntimeOnly());
        modRuntimeClasspath.extendsFrom(modRuntimeOnly);
        var modLocalRuntime = ConfigurationUtils.dependencyScope(project, name, "mod", "localRuntime", c -> {});
        modLocalRuntime.fromDependencyCollector(dependencies.getModLocalRuntime());
        modRuntimeClasspath.extendsFrom(modLocalRuntime);
        var modLocalImplementation = ConfigurationUtils.dependencyScope(project, name, "mod", "localImplementation", c -> {});
        modLocalImplementation.fromDependencyCollector(dependencies.getModLocalImplementation());
        modCompileClasspath.extendsFrom(modLocalImplementation);
        modRuntimeClasspath.extendsFrom(modLocalImplementation);
        var modImplementation = ConfigurationUtils.dependencyScope(project, name, "mod", "implementation", c -> {});
        modImplementation.fromDependencyCollector(dependencies.getModImplementation());
        modCompileClasspath.extendsFrom(modImplementation);
        modRuntimeClasspath.extendsFrom(modImplementation);
        var modApi = ConfigurationUtils.dependencyScope(project, name, "mod", "api", c -> {});
        modApi.fromDependencyCollector(dependencies.getModApi());
        modCompileClasspath.extendsFrom(modApi);
        modRuntimeClasspath.extendsFrom(modApi);

        // Link up inheritance via CrochetFeatureContexts for the injected configurations

        var compileRemappingClasspath = ConfigurationUtils.resolvableInternal(project, name, "remappingCompileClasspath", config -> {
            config.attributes(attributes -> {
                compileAttributes.execute(attributes);
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
        });

        var runtimeRemappingClasspath = ConfigurationUtils.resolvableInternal(project, name, "remappingRuntimeClasspath", config -> {
            config.attributes(attributes -> {
                runtimeAttributes.execute(attributes);
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
        });

        compileRemappingClasspath.extendsFrom(modCompileClasspath);
        compileRemappingClasspath.extendsFrom(intermediaryMinecraft());
        compileRemappingClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);

        runtimeRemappingClasspath.extendsFrom(modRuntimeClasspath);
        runtimeRemappingClasspath.extendsFrom(intermediaryMinecraft());
        runtimeRemappingClasspath.shouldResolveConsistentlyWith(versioningRuntimeClasspath);

        var artifactDummy = ConfigurationUtils.resolvableInternal(project, name, "artifactDummy", c -> {});
        var artifacts = project.getArtifacts();

        ListProperty<PublishArtifact> compileArtifactsList = project.getObjects().listProperty(PublishArtifact.class);
        var remapCompileMods = TaskUtils.registerInternal(project, TaskGraphExecution.class, name, "remapCompileClasspath", task -> {
            var configMaker = project.getObjects().newInstance(RemapModsConfigMaker.class);
            configMaker.setup(task, modCompileClasspath, excludedCompileClasspath, workingDirectory().get().dir("compileClasspath").dir(name), (t, files) -> {
                compileArtifactsList.set(files.map(fileList -> {
                    var out = new ArrayList<PublishArtifact>();
                    for (var file : fileList) {
                        out.add(artifacts.add(artifactDummy.getName(), file, a -> {
                            a.builtBy(t);
                        }));
                        artifactDummy.getArtifacts().clear();
                    }
                    return out;
                }));
            });
            task.dependsOn(intermediaryToNamedFlat());
            configMaker.getDistribution().set(minecraftInstallation.getDistribution());
            configMaker.getRemappingClasspath().from(compileRemappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed());
            task.getConfigMaker().set(configMaker);

            addArtifacts(task);
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });
        remappedCompileClasspath.getArtifacts().addAllLater(remapCompileMods.zip(compileArtifactsList, (t, l) -> l));

        ListProperty<PublishArtifact> runtimeArtifactsList = project.getObjects().listProperty(PublishArtifact.class);
        var remapRuntimeMods = TaskUtils.registerInternal(project, TaskGraphExecution.class, name, "remapRuntimeClasspath", task -> {
            var configMaker = project.getObjects().newInstance(RemapModsConfigMaker.class);
            configMaker.setup(task, modRuntimeClasspath, excludedRuntimeClasspath, workingDirectory().get().dir("runtimeClasspath").dir(name), (t, files) -> {
                runtimeArtifactsList.set(files.map(fileList -> {
                    var out = new ArrayList<PublishArtifact>();
                    for (var file : fileList) {
                        out.add(artifacts.add(artifactDummy.getName(), file, a -> {
                            a.builtBy(t);
                        }));
                        artifactDummy.getArtifacts().clear();
                    }
                    return out;
                }));
            });
            task.dependsOn(intermediaryToNamedFlat());
            configMaker.getDistribution().set(minecraftInstallation.getDistribution());
            configMaker.getRemappingClasspath().from(runtimeRemappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed());
            task.getConfigMaker().set(configMaker);

            addArtifacts(task);
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });
        remappedRuntimeClasspath.getArtifacts().addAllLater(remapRuntimeMods.zip(runtimeArtifactsList, (t, l) -> l));

        var remapCompileModSources = TaskUtils.registerInternal(project, TaskGraphExecution.class, name, "remapCompileClasspathSources", task -> {
            var configMaker = project.getObjects().newInstance(RemapModsSourcesConfigMaker.class);
            configMaker.setup(task, modCompileClasspath, excludedCompileClasspath, workingDirectory().get().dir("compileClasspathSources").dir(name));
            task.dependsOn(intermediaryToNamedFlat());
            configMaker.getRemappingClasspath().from(compileRemappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed());
            task.getConfigMaker().set(configMaker);

            addArtifacts(task);
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        var remapRuntimeModSources = TaskUtils.registerInternal(project, TaskGraphExecution.class, name, "remapRuntimeClasspathSources", task -> {
            var configMaker = project.getObjects().newInstance(RemapModsSourcesConfigMaker.class);
            configMaker.setup(task, modRuntimeClasspath, excludedRuntimeClasspath, workingDirectory().get().dir("runtimeClasspathSources").dir(name));
            task.dependsOn(intermediaryToNamedFlat());
            configMaker.getRemappingClasspath().from(runtimeRemappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed());
            task.getConfigMaker().set(configMaker);

            addArtifacts(task);
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        linkSources(remapCompileMods, remapCompileModSources);
        linkSources(remapRuntimeMods, remapRuntimeModSources);

        minecraftInstallation.crochetExtension.generateSources.configure(task -> {
            task.dependsOn(remapCompileModSources);
            task.dependsOn(remapRuntimeModSources);
        });

        minecraftInstallation.crochetExtension.idePostSync.configure(task -> {
            task.dependsOn(remapCompileMods);
            task.dependsOn(remapRuntimeMods);
        });
    }

    OutConfigurations forFeatureShared(SourceSet sourceSet, FabricRemapDependencies dependencies, boolean local) {
        /*
        General architecture:
        - compileClasspath -- the normal classpath for the mod. Specifically selects "not-to-remap" dependencies
        - modCompileClasspath and modRuntimeClasspath -- collect dependencies for remapping. Resolve remap type "to-remap"
        - modApiElements and modRuntimeElements -- remap type "to-remap", these expose to-remap dependencies, but no artifacts, transitively
        - nonModApiElements and nonModRuntimeElements -- remap type "not-ro-remap", these expose the non-remapped project artifacts/variants, as well as any non-remapped deps, transitively
        - remappedCompileClasspath -- output of remapping, used to insert stuff into compile classpath

        ATs and IIs are grabbed from anything that is on both modRuntime and modCompile classpaths
        TODO: see if we can limit that to only care about compile by having a separate MC jar for runtime

        Remapping is set up here only for the compile classpath. The excluded deps should be anything on compileClasspath that _isn't_ the remapped dependencies (or file dependencies)
        To handle exclusions, we make another classpath:
        - excludedCompileClasspath -- compileClasspath with only project/module dependencies
          - this should also be given everything on the relevant non-upgradable classpath

        And finally, to handle versioning right, we make a version-determining classpath:
        - versioningCompileClasspath
        And have every resolving configuration, compileClasspath and modCompileClasspath, be shouldResolveConsistentlyWith it

        If this is non-local
        - remapJar, remapSourcesJar -- remapped jar dependencies
         */

        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(loaderConfiguration());
        });

        var modCompileClasspath = ConfigurationUtils.resolvableInternal(project, sourceSet.getName(), "modCompileClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
        });
        var modRuntimeClasspath = ConfigurationUtils.resolvableInternal(project, sourceSet.getName(), "modRuntimeClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
        });


        var versioningCompileDependencies = ConfigurationUtils.dependencyScopeInternal(project, sourceSet.getName(), "versioningCompileDependencies", config -> {});
        var versioningCompileClasspath = ConfigurationUtils.resolvableInternal(project, sourceSet.getName(), "versioningCompileClasspath", config -> {
            config.attributes(attributes -> {
                // Does not have the remap type attribute at this point
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
            });
            config.extendsFrom(versioningCompileDependencies);
            config.shouldResolveConsistentlyWith(switch (minecraftInstallation.getDistribution().get()) {
                case CLIENT, JOINED -> minecraftInstallation.nonUpgradableClientCompileVersioning;
                case SERVER, COMMON -> minecraftInstallation.nonUpgradableServerCompileVersioning;
            });
        });

        var excludedCompileDependencies = ConfigurationUtils.dependencyScopeInternal(project, sourceSet.getName(), "excludedCompileDependencies", config -> {});
        var excludedCompileClasspath = ConfigurationUtils.resolvableInternal(project, sourceSet.getName(), "excludedCompileClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
            });
            config.extendsFrom(excludedCompileDependencies);
        });

        if (compileExclude() != null) {
            modCompileClasspath.extendsFrom(compileExclude());
        }

        var compileClasspath = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
        excludedCompileClasspath.getDependencyConstraints().addAllLater(project.provider(compileClasspath::getDependencyConstraints));
        // Only non-file-collection dependencies
        excludedCompileClasspath.getDependencies().addAllLater(project.provider(compileClasspath::getAllDependencies).map(deps -> new ArrayList<>(deps.stream().filter(dep -> (dep instanceof ModuleDependency)).toList())));
        if (compileExclude() != null) {
            excludedCompileClasspath.extendsFrom(compileExclude());
        }

        versioningCompileClasspath.extendsFrom(modCompileClasspath);
        versioningCompileClasspath.extendsFrom(compileClasspath);
        if (compileExclude() != null) {
            versioningCompileClasspath.extendsFrom(compileExclude());
        }
        compileClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);
        modCompileClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);
        excludedCompileClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);

        extractFabricForDependencies(modCompileClasspath, modRuntimeClasspath);

        class ConfigurationExtension implements Consumer<Configuration> {
            private final List<Configuration> configurations = new ArrayList<>();
            private @Nullable Consumer<Configuration> next = null;

            @Override
            public synchronized void accept(Configuration configuration) {
                if (next == null) {
                    configurations.add(configuration);
                } else {
                    next.accept(configuration);
                }
            }

            public void setNext(Consumer<Configuration> next) {
                this.next = next;
                configurations.forEach(next);
                configurations.clear();
            }
        }

        var modApiElementsExtendsFrom = new ConfigurationExtension();
        var modRuntimeElementsExtendsFrom = new ConfigurationExtension();
        var apiElementsExtendsFrom = new ConfigurationExtension();
        var runtimeElementsExtendsFrom = new ConfigurationExtension();

        FeatureUtils.forSourceSetFeature(project, sourceSet.getName(), context -> {
            var runtimeElements = context.getRuntimeElements();
            runtimeElementsExtendsFrom.setNext(runtimeElements::extendsFrom);
            var apiElements = context.getApiElements();
            apiElementsExtendsFrom.setNext(apiElements::extendsFrom);

            var modRuntimeElements = ConfigurationUtils.consumableInternal(project, sourceSet.getName(), "modRuntimeElements", config -> {
                config.attributes(attributes -> {
                    attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                    copyAttributes(runtimeElements.getAttributes(), attributes, project.getProviders());
                });
            });
            modRuntimeElementsExtendsFrom.setNext(modRuntimeElements::extendsFrom);
            var modApiElements = ConfigurationUtils.consumableInternal(project, sourceSet.getName(), "modApiElements", config -> {
                config.attributes(attributes -> {
                    attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                    copyAttributes(apiElements.getAttributes(), attributes, project.getProviders());
                });
            });
            modApiElementsExtendsFrom.setNext(modApiElements::extendsFrom);
            var nonModRuntimeElements = ConfigurationUtils.consumableInternal(project, sourceSet.getName(), "nonModRuntimeElements", config -> {
                config.attributes(attributes -> {
                    attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                    copyAttributes(runtimeElements.getAttributes(), attributes, project.getProviders());
                });
            });
            var nonModApiElements = ConfigurationUtils.consumableInternal(project, sourceSet.getName(), "nonModApiElements", config -> {
                config.attributes(attributes -> {
                    attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                    copyAttributes(apiElements.getAttributes(), attributes, project.getProviders());
                });
            });

            context.withCapabilities(modRuntimeElements);
            context.withCapabilities(modApiElements);
            context.withCapabilities(nonModRuntimeElements);
            context.withCapabilities(nonModApiElements);

            nonModApiElements.getDependencyConstraints().addAllLater(project.provider(apiElements::getDependencyConstraints));
            nonModRuntimeElements.getDependencyConstraints().addAllLater(project.provider(runtimeElements::getDependencyConstraints));

            nonModApiElements.getDependencies().addAllLater(project.provider(() ->
                apiElements.getAllDependencies().stream().filter(dep -> (dep instanceof ProjectDependency) || !modCompileClasspath.getAllDependencies().contains(dep)).toList()
            ));
            nonModRuntimeElements.getDependencies().addAllLater(project.provider(() ->
                runtimeElements.getAllDependencies().stream().filter(dep -> (dep instanceof ProjectDependency) || !modRuntimeClasspath.getAllDependencies().contains(dep)).toList()
            ));

            // An empty configuration, so that the RemapModsSourcesConfigMaker will skip the actual sources jar
            var remappedSourcesElements = Suppliers.memoize(() ->
                ConfigurationUtils.consumableInternal(project, sourceSet.getName(), "remappedSourcesElements", config -> {
                    var sourcesElements = project.getConfigurations().getByName(sourceSet.getSourcesElementsConfigurationName());
                    config.attributes(attributes -> {
                        copyAttributes(sourcesElements.getAttributes(), attributes, project.getProviders());
                        attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                    });
                })
            );

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
                        includeInterfaceInjections(configMaker.getIncludedInterfaceInjections());

                        configMaker.getMappings().set(namedToIntermediary());
                        remapJar.dependsOn(namedToIntermediaryFlat());
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

                        configMaker.getMappings().set(namedToIntermediary());
                        remapSourcesJar.dependsOn(namedToIntermediaryFlat());
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

                            var sourcesElements = project.getConfigurations().getByName(sourceSet.getSourcesElementsConfigurationName());
                            sourcesElements.getOutgoing().getArtifacts().clear();

                            remappedSourcesElements.get();

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

        var modCompileOnly = ConfigurationUtils.dependencyScope(project, sourceSet.getName(), "mod", "compileOnly", c -> {});
        modCompileOnly.fromDependencyCollector(dependencies.getModCompileOnly());
        modCompileClasspath.extendsFrom(modCompileOnly);
        var modCompileOnlyApi = ConfigurationUtils.dependencyScope(project, sourceSet.getName(), "mod", "compileOnlyApi", c -> {});
        modCompileOnlyApi.fromDependencyCollector(dependencies.getModCompileOnlyApi());
        modCompileClasspath.extendsFrom(modCompileOnlyApi);
        apiElementsExtendsFrom.accept(modCompileOnlyApi);
        modApiElementsExtendsFrom.accept(modCompileOnlyApi);
        var modRuntimeOnly = ConfigurationUtils.dependencyScope(project, sourceSet.getName(), "mod", "runtimeOnly", c -> {});
        modRuntimeOnly.fromDependencyCollector(dependencies.getModRuntimeOnly());
        modRuntimeClasspath.extendsFrom(modRuntimeOnly);
        runtimeElementsExtendsFrom.accept(modRuntimeOnly);
        modRuntimeElementsExtendsFrom.accept(modRuntimeOnly);
        var modLocalRuntime = ConfigurationUtils.dependencyScope(project, sourceSet.getName(), "mod", "localRuntime", c -> {});
        modLocalRuntime.fromDependencyCollector(dependencies.getModLocalRuntime());
        modRuntimeClasspath.extendsFrom(modLocalRuntime);
        var modLocalImplementation = ConfigurationUtils.dependencyScope(project, sourceSet.getName(), "mod", "localImplementation", c -> {});
        modLocalImplementation.fromDependencyCollector(dependencies.getModLocalImplementation());
        modCompileClasspath.extendsFrom(modLocalImplementation);
        modRuntimeClasspath.extendsFrom(modLocalImplementation);
        var modImplementation = ConfigurationUtils.dependencyScope(project, sourceSet.getName(), "mod", "implementation", c -> {});
        modImplementation.fromDependencyCollector(dependencies.getModImplementation());
        modCompileClasspath.extendsFrom(modImplementation);
        modRuntimeClasspath.extendsFrom(modImplementation);
        runtimeElementsExtendsFrom.accept(modImplementation);
        modRuntimeElementsExtendsFrom.accept(modImplementation);
        var modApi = ConfigurationUtils.dependencyScope(project, sourceSet.getName(), "mod", "api", c -> {});
        modApi.fromDependencyCollector(dependencies.getModApi());
        modCompileClasspath.extendsFrom(modApi);
        modRuntimeClasspath.extendsFrom(modApi);
        apiElementsExtendsFrom.accept(modApi);
        modApiElementsExtendsFrom.accept(modApi);
        runtimeElementsExtendsFrom.accept(modApi);
        modRuntimeElementsExtendsFrom.accept(modApi);

        // Link up inheritance via CrochetFeatureContexts for the injected configurations
        var marker = InheritanceMarker.getOrCreate(project.getObjects(), sourceSet);
        marker.getShouldTakeConfigurationsFrom().configureEach(name -> {
            var otherSourceSet = project.getExtensions().getByType(SourceSetContainer.class).findByName(name);
            var otherInstallation = minecraftInstallation.crochetExtension.findInstallation(otherSourceSet);
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
            var otherInstallation = minecraftInstallation.crochetExtension.findInstallation(otherSourceSet);
            if (otherInstallation instanceof FabricInstallation) {
                for (var confName : MOD_CONFIGURATION_NAMES) {
                    var thisConf = project.getConfigurations().getByName(sourceSet.getTaskName("mod", confName));
                    var otherConf = project.getConfigurations().getByName(otherSourceSet.getTaskName("mod", confName));
                    otherConf.extendsFrom(thisConf);
                }
            }
        });

        var remappedCompileClasspath = ConfigurationUtils.dependencyScopeInternal(project, sourceSet.getName(), "remappedCompileClasspath", c -> {
            if (compileRemapped() != null) {
                c.extendsFrom(compileRemapped());
            }
        });
        project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(remappedCompileClasspath);

        var compileRemappingClasspath = ConfigurationUtils.resolvableInternal(project, sourceSet.getName(), "remappingCompileClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName()).getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
        });

        project.getConfigurations().named(sourceSet.getCompileClasspathConfigurationName(), config -> {
            config.attributes(attributes ->
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP)
            );
            config.getDependencies().addAllLater(project.provider(() ->
                modCompileClasspath.getAllDependencies().stream().filter(dep -> {
                    // We explicitly do _not_ want to accidentally include special exclusion dependencies within this
                    if (compileExclude() != null) {
                        return (dep instanceof ProjectDependency) && !compileExclude().getAllDependencies().contains(dep);
                    } else {
                        return dep instanceof ProjectDependency;
                    }
                }).toList()
            ));
        });

        compileRemappingClasspath.extendsFrom(modCompileClasspath);
        compileRemappingClasspath.extendsFrom(intermediaryMinecraft());
        compileRemappingClasspath.shouldResolveConsistentlyWith(versioningCompileClasspath);

        var remappedCompileMods = project.files();
        project.getDependencies().add(remappedCompileClasspath.getName(), remappedCompileMods);

        var remapCompileMods = TaskUtils.registerInternal(project, TaskGraphExecution.class, sourceSet.getName(), "remapCompileClasspath", task -> {
            var configMaker = project.getObjects().newInstance(RemapModsConfigMaker.class);
            configMaker.setup(task, modCompileClasspath, excludedCompileClasspath, workingDirectory().get().dir("compileClasspath").dir(sourceSet.getName()), remappedCompileMods);
            task.dependsOn(intermediaryToNamedFlat());
            configMaker.getDistribution().set(minecraftInstallation.getDistribution());
            configMaker.getRemappingClasspath().from(compileRemappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed());
            task.getConfigMaker().set(configMaker);

            addArtifacts(task);
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });
        remappedCompileMods.builtBy(remapCompileMods);

        var remapCompileModSources = TaskUtils.registerInternal(project, TaskGraphExecution.class, sourceSet.getName(), "remapCompileClasspathSources", task -> {
            var configMaker = project.getObjects().newInstance(RemapModsSourcesConfigMaker.class);
            configMaker.setup(task, modCompileClasspath, excludedCompileClasspath, workingDirectory().get().dir("compileClasspathSources").dir(sourceSet.getName()));
            task.dependsOn(intermediaryToNamedFlat());
            configMaker.getRemappingClasspath().from(compileRemappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed());
            task.getConfigMaker().set(configMaker);

            addArtifacts(task);
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        linkSources(remapCompileMods, remapCompileModSources);

        minecraftInstallation.crochetExtension.idePostSync.configure(task -> {
            task.dependsOn(remapCompileMods);
        });
        minecraftInstallation.crochetExtension.generateSources.configure(task -> {
            task.dependsOn(remapCompileModSources);
        });
        return new OutConfigurations(modCompileClasspath, modRuntimeClasspath);
    }

    protected abstract void addArtifacts(TaskGraphExecution task);

    void linkSources(TaskProvider<TaskGraphExecution> remapJars, TaskProvider<TaskGraphExecution> remapSourceJars) {
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

    Configuration forRunRemapping(Run run, RunType runType) {
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

        var implementation = ConfigurationUtils.dependencyScopeInternal(project, run.getName(), "runImplementation", config -> {
            config.fromDependencyCollector(run.getImplementation());
        });

        var runClasspath = run.classpath;
        var modClasspath = ConfigurationUtils.resolvableInternal(project, run.getName(), "runModClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(runClasspath.getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
        });
        if (runtimeExclude() != null) {
            modClasspath.extendsFrom(runtimeExclude());
        }
        modClasspath.extendsFrom(implementation);
        runClasspath.extendsFrom(implementation);

        var remappedClasspath = ConfigurationUtils.dependencyScopeInternal(project, run.getName(), "remappedRunClasspath", config -> {
            if (runtimeRemapped() != null) {
                config.extendsFrom(runtimeRemapped());
            }
        });

        var excludedClasspath = ConfigurationUtils.resolvableInternal(project, run.getName(), "excludedRunClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(runClasspath.getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
            });
            config.extendsFrom(implementation);
            if (runtimeExclude() != null) {
                config.extendsFrom(runtimeExclude());
            }
        });

        var versioningClasspath = ConfigurationUtils.resolvableInternal(project, run.getName(), "runVersioningClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(runClasspath.getAttributes(), attributes, project.getProviders());
            });
            config.shouldResolveConsistentlyWith(switch (runType) {
                case CLIENT -> minecraftInstallation.nonUpgradableClientRuntimeVersioning;
                case SERVER -> minecraftInstallation.nonUpgradableServerRuntimeVersioning;
                case DATA -> minecraftInstallation.nonUpgradableClientRuntimeVersioning;
            });
        });

        versioningClasspath.extendsFrom(modClasspath);
        versioningClasspath.extendsFrom(runClasspath);
        runClasspath.shouldResolveConsistentlyWith(versioningClasspath);
        modClasspath.shouldResolveConsistentlyWith(versioningClasspath);
        excludedClasspath.shouldResolveConsistentlyWith(versioningClasspath);

        runClasspath.attributes(attributes -> {
            attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_NON_REMAP);
        });

        run.classpath.extendsFrom(remappedClasspath);

        var remappingClasspath = ConfigurationUtils.resolvableInternal(project, run.getName(), "runRemappingClasspath", config -> {
            config.attributes(attributes -> {
                copyAttributes(runClasspath.getAttributes(), attributes, project.getProviders());
                attributes.attribute(CrochetProjectPlugin.CROCHET_REMAP_TYPE_ATTRIBUTE, CrochetProjectPlugin.CROCHET_REMAP_TYPE_REMAP);
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            });
        });
        remappingClasspath.extendsFrom(modClasspath);

        remappingClasspath.extendsFrom(modClasspath);
        remappingClasspath.extendsFrom(intermediaryMinecraft());
        remappingClasspath.shouldResolveConsistentlyWith(versioningClasspath);

        var remappedMods = project.files();
        project.getDependencies().add(remappedClasspath.getName(), remappedMods);

        var remapMods = project.getTasks().register("crochetRemap"+StringUtils.capitalize(run.getName())+"RunClasspath", TaskGraphExecution.class, task -> {
            var configMaker = project.getObjects().newInstance(RemapModsConfigMaker.class);
            configMaker.setup(task, modClasspath, excludedClasspath, workingDirectory().get().dir("runClasspath").dir(run.getName()), remappedMods);
            task.dependsOn(intermediaryToNamedFlat());
            configMaker.getDistribution().set(minecraftInstallation.getDistribution());
            configMaker.getRemappingClasspath().from(remappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed());
            task.getConfigMaker().set(configMaker);

            addArtifacts(task);
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });
        remappedMods.builtBy(remapMods);

        var remapModSources = project.getTasks().register("crochetRemap"+StringUtils.capitalize(run.getName())+"RunClasspathSources", TaskGraphExecution.class, task -> {
            var configMaker = project.getObjects().newInstance(RemapModsSourcesConfigMaker.class);
            configMaker.setup(task, modClasspath, excludedClasspath, workingDirectory().get().dir("runClasspathSources").dir(run.getName()));
            task.dependsOn(intermediaryToNamedFlat());
            configMaker.getRemappingClasspath().from(remappingClasspath);
            configMaker.getMappings().set(intermediaryToNamed());
            task.getConfigMaker().set(configMaker);

            addArtifacts(task);
            task.getClasspath().from(project.getConfigurations().named(CrochetProjectPlugin.TASK_GRAPH_RUNNER_CONFIGURATION_NAME));
        });

        linkSources(remapMods, remapModSources);

        minecraftInstallation.crochetExtension.idePostSync.configure(task -> {
            task.dependsOn(remapMods);
        });

        minecraftInstallation.crochetExtension.generateSources.configure(task -> {
            task.dependsOn(remapModSources);
        });

        return remappingClasspath;
    }
}
