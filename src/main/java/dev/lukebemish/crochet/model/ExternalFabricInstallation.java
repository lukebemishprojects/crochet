package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.tasks.TaskGraphExecution;
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

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        // TODO: implement
    }
}
