package dev.lukebemish.crochet.model;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public abstract class CrochetExtension {
    private final TaskProvider<Task> idePostSync;
    final Project project;
    private final ExtensiblePolymorphicDomainObjectContainer<MinecraftInstallation> installations;

    @Inject
    public CrochetExtension(Project project) {
        this.project = project;
        this.idePostSync = project.getTasks().register("crochetIdeSetup");
        var objects = project.getObjects();
        this.installations = objects.polymorphicDomainObjectContainer(
            MinecraftInstallation.class
        );
        this.installations.registerFactory(
            VanillaInstallation.class,
            name -> objects.newInstance(VanillaInstallation.class, name, this)
        );
        // This collection should be non-lazy as it configures other lazy things (namely, tasks)
        this.installations.whenObjectAdded(o -> {});
    }

    public ExtensiblePolymorphicDomainObjectContainer<MinecraftInstallation> getInstallations() {
        return installations;
    }

    private final Map<SourceSet, String> sourceSets = new HashMap<>();

    void forSourceSet(String installation, SourceSet sourceSet) {
        var existing = sourceSets.put(sourceSet, installation);
        if (existing != null) {
            throw new IllegalStateException("Source set " + sourceSet.getName() + " is already associated with installation " + existing);
        }
    }
}
