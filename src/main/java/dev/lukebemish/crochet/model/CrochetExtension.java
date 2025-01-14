package dev.lukebemish.crochet.model;

import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CrochetExtension implements ExtensionAware {
    final TaskProvider<Task> idePostSync;
    final TaskProvider<Task> generateSources;
    final Project project;
    private final ExtensiblePolymorphicDomainObjectContainer<MinecraftInstallation> installations;
    private final CrochetFeaturesContext features;
    private final CrochetTasksContext tasks;
    private final NamedDomainObjectContainer<SplitSourceSet> splitSourceSets;
    private final NamedDomainObjectContainer<FabricDependencyBundle> fabricDependencyBundles;
    private final NamedDomainObjectContainer<SharedInstallation> sharedInstallations;

    @Inject
    public CrochetExtension(Project project) {
        this.project = project;
        this.generateSources = project.getTasks().register("crochetGenerateSources", t -> t.setGroup("crochet setup"));
        this.idePostSync = project.getTasks().register("crochetIdeSetup", t -> {
            t.setGroup("crochet setup");
            t.dependsOn(generateSources);
        });
        var objects = project.getObjects();
        this.features = objects.newInstance(CrochetFeaturesContext.class);
        this.tasks = objects.newInstance(CrochetTasksContext.class);
        this.installations = objects.polymorphicDomainObjectContainer(
            MinecraftInstallation.class
        );
        this.installations.registerFactory(
            VanillaInstallation.class,
            name -> objects.newInstance(VanillaInstallation.class, name, this)
        );
        this.installations.registerFactory(
            FabricInstallation.class,
            name -> objects.newInstance(FabricInstallation.class, name, this)
        );
        this.installations.registerFactory(
            NeoFormInstallation.class,
            name -> objects.newInstance(NeoFormInstallation.class, name, this)
        );

        // External stuff
        this.installations.registerFactory(
            ExternalVanillaInstallation.class,
            name -> objects.newInstance(ExternalVanillaInstallation.class, name, this)
        );

        // This collection should be non-lazy as it configures other lazy things (namely, tasks)
        this.installations.whenObjectAdded(o -> {});

        if (Boolean.getBoolean("idea.sync.active")) {
            var startParameter = project.getGradle().getStartParameter();
            var taskRequests = new ArrayList<>(startParameter.getTaskRequests());
            // The use of this lets us avoid internal classes
            startParameter.setTaskNames(List.of(idePostSync.getName()));
            taskRequests.addAll(startParameter.getTaskRequests());
            startParameter.setTaskRequests(taskRequests);
        }

        // Runs should also be non-lazy, to trigger task creation
        this.getRuns().whenObjectAdded(o -> {});

        this.splitSourceSets = objects.domainObjectContainer(SplitSourceSet.class, name -> {
            throw new UnsupportedOperationException("Cannot instantiate SplitSourceSet on this container.");
        });
        this.fabricDependencyBundles = objects.domainObjectContainer(FabricDependencyBundle.class, name -> {
            throw new UnsupportedOperationException("Cannot instantiate FabricDependencyBundle on this container.");
        });
        this.sharedInstallations = objects.domainObjectContainer(SharedInstallation.class, name -> {
            throw new UnsupportedOperationException("Cannot instantiate SharedInstallation on this container.");
        });

        this.getExtensions().add("installations", this.installations);
        this.getExtensions().add("splitSourceSets", this.splitSourceSets);
        this.getExtensions().add("fabricDependencyBundles", this.fabricDependencyBundles);
        this.getExtensions().add("sharedInstallations", this.sharedInstallations);
    }

    public CrochetFeaturesContext getFeatures() {
        return features;
    }

    public void features(Action<CrochetFeaturesContext> action) {
        action.execute(getFeatures());
    }

    public CrochetTasksContext getTasks() {
        return tasks;
    }

    public void tasks(Action<CrochetTasksContext> action) {
        action.execute(getTasks());
    }

    public SplitSourceSet fabricSplitSourceSets(String name, Action<FabricSplitSourceSetsSpec> action) {
        var spec = project.getObjects().newInstance(FabricSplitSourceSetsSpec.class);
        action.execute(spec);
        var splitSourceSet = spec.setup(project, this, name);
        splitSourceSets.add(splitSourceSet);
        return splitSourceSet;
    }

    public void installations(Action<ExtensiblePolymorphicDomainObjectContainer<MinecraftInstallation>> action) {
        action.execute(installations);
    }

    public NamedDomainObjectProvider<FabricInstallation> fabricInstallation(String name, Action<? super FabricInstallation> action) {
        return installations.register(name, FabricInstallation.class, action);
    }

    public NamedDomainObjectProvider<NeoFormInstallation> neoFormInstallation(String name, Action<? super NeoFormInstallation> action) {
        return installations.register(name, NeoFormInstallation.class, action);
    }

    public NamedDomainObjectProvider<VanillaInstallation> vanillaInstallation(String name, Action<? super VanillaInstallation> action) {
        return installations.register(name, VanillaInstallation.class, action);
    }

    public NamedDomainObjectProvider<ExternalVanillaInstallation> externalVanillaInstallation(String name, Action<? super ExternalVanillaInstallation> action) {
        return installations.register(name, ExternalVanillaInstallation.class, action);
    }

    public abstract NamedDomainObjectContainer<Run> getRuns();

    public void runs(Action<NamedDomainObjectContainer<Run>> action) {
        action.execute(getRuns());
    }

    private final Map<SourceSet, String> sourceSets = new HashMap<>();

    MinecraftInstallation findInstallation(SourceSet sourceSet) {
        var installation = sourceSets.get(sourceSet);
        if (installation == null) {
            return null;
        }
        return installations.getByName(installation);
    }

    void forSourceSet(String installation, SourceSet sourceSet) {
        var existing = sourceSets.put(sourceSet, installation);
        if (existing != null) {
            throw new IllegalStateException("Source set " + sourceSet.getName() + " is already associated with installation " + existing);
        }
    }

    void addSharedInstallation(String name) {
        sharedInstallations.add(project.getObjects().newInstance(SharedInstallation.class, name));
    }
}
