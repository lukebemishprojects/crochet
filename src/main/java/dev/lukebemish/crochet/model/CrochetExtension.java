package dev.lukebemish.crochet.model;

import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CrochetExtension {
    final TaskProvider<Task> idePostSync;
    final TaskProvider<Task> generateSources;
    final Project project;
    private final ExtensiblePolymorphicDomainObjectContainer<MinecraftInstallation> installations;

    @Inject
    public CrochetExtension(Project project) {
        this.project = project;
        this.generateSources = project.getTasks().register("crochetGenerateSources", t -> t.setGroup("crochet setup"));
        this.idePostSync = project.getTasks().register("crochetIdeSetup", t -> {
            t.setGroup("crochet setup");
            t.dependsOn(generateSources);
        });
        var objects = project.getObjects();
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
    }

    public ExtensiblePolymorphicDomainObjectContainer<MinecraftInstallation> getInstallations() {
        return installations;
    }

    public void installations(Action<ExtensiblePolymorphicDomainObjectContainer<MinecraftInstallation>> action) {
        action.execute(installations);
    }

    public NamedDomainObjectProvider<FabricInstallation> fabric(String name, Action<FabricInstallation> action) {
        return installations.register(name, FabricInstallation.class, action);
    }

    public NamedDomainObjectProvider<VanillaInstallation> vanilla(String name, Action<VanillaInstallation> action) {
        return installations.register(name, VanillaInstallation.class, action);
    }

    public abstract NamedDomainObjectContainer<Run> getRuns();

    public void runs(Action<NamedDomainObjectContainer<Run>> action) {
        action.execute(getRuns());
    }

    private final Map<SourceSet, String> sourceSets = new HashMap<>();

    void forSourceSet(String installation, SourceSet sourceSet) {
        var existing = sourceSets.put(sourceSet, installation);
        if (existing != null) {
            throw new IllegalStateException("Source set " + sourceSet.getName() + " is already associated with installation " + existing);
        }
    }
}
