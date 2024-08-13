package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.tasks.CreateArtifactManifest;
import dev.lukebemish.crochet.tasks.ExtractConfigTask;
import dev.lukebemish.crochet.tasks.VanillaArtifactsTask;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;

public abstract class AbstractVanillaInstallation extends MinecraftInstallation {
    final Project project;
    final Provider<Configuration> clientMinecraft;
    final Provider<Configuration> serverMinecraft;
    final TaskProvider<ExtractConfigTask> extractConfig;
    final TaskProvider<VanillaArtifactsTask> artifactsTask;
    final TaskProvider<CreateArtifactManifest> createArtifactManifestTask;
    final Provider<Directory> workingDirectory;

    @Inject
    public AbstractVanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        this.project = extension.project;

        var workingDirectory = project.getLayout().getBuildDirectory().dir("crochet/installations/" + name);
        this.workingDirectory = workingDirectory;

        this.createArtifactManifestTask = project.getTasks().register(name + "CrochetCreateArtifactManifest", CreateArtifactManifest.class, task -> {
            task.getOutputFile().set(workingDirectory.get().file("artifacts.properties"));
        });

        this.downloadAssetsTask.configure(task -> {
            task.getArguments().add("--neoform");
            task.getArguments().add(this.getNeoFormModule().map(m -> m + "@zip"));
            task.getArtifactManifest().set(createArtifactManifestTask.flatMap(CreateArtifactManifest::getOutputFile));
            task.dependsOn(createArtifactManifestTask);
        });

        this.artifactsTask = project.getTasks().register(name + "CrochetMinecraftArtifacts", VanillaArtifactsTask.class, task -> {
            task.setGroup("crochet setup");
            task.getAccessTransformers().from(this.accessTransformersPath);
            task.getNeoFormModule().set(this.getNeoFormModule().map(m -> m + "@zip"));
            task.getClientResources().set(workingDirectory.get().file("client-extra.jar"));
            task.getCompiled().set(workingDirectory.get().file("compiled.jar"));
            task.getSources().set(workingDirectory.get().file("sources.jar"));
            task.getSourcesAndCompiled().set(workingDirectory.get().file("sources-and-compiled.jar"));
            task.getRuntimeClasspath().from(project.getConfigurations().named(CrochetPlugin.NEOFORM_RUNTIME_CONFIGURATION_NAME));
            task.getArtifactManifest().set(createArtifactManifestTask.flatMap(CreateArtifactManifest::getOutputFile));
            task.dependsOn(createArtifactManifestTask);
        });

        var minecraftDependencies = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"MinecraftDependencies");
        var minecraft = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"Minecraft", config ->
            config.extendsFrom(minecraftDependencies)
        );

        this.clientMinecraft = this.project.getConfigurations().register("crochet"+StringUtils.capitalize(name)+"ClientRuntimeClasspath", configuration -> {
            configuration.extendsFrom(minecraft);
            configuration.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client"));
        });
        this.serverMinecraft = this.project.getConfigurations().register("crochet"+StringUtils.capitalize(name)+"ServerRuntimeClasspath", configuration -> {
            configuration.extendsFrom(minecraft);
            configuration.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server"));
        });

        var neoformCompileClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoformCompileClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        var neoformRuntimeClasspath = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoformRuntimeClasspath", config -> {
            config.extendsFrom(minecraftDependencies);
            config.attributes(attributes -> {
                attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });

        this.project.getDependencies().addProvider(
            minecraftDependencies.getName(),
            this.getNeoFormModule(),
            dependency ->
                dependency.capabilities(capabilities ->
                    capabilities.requireCapability("net.neoforged:neoform-dependencies")
                )
        );

        var neoform = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NeoForm");
        this.project.getDependencies().addProvider(
            neoform.getName(),
            this.getNeoFormModule()
        );

        this.createArtifactManifestTask.configure(task -> {
            task.configuration(neoformCompileClasspath);
            task.configuration(neoformRuntimeClasspath);
            task.configuration(project.getConfigurations().getByName(CrochetPlugin.NFRT_DEPENDENCIES_CONFIGURATION_NAME));
            task.configuration(neoform);
        });

        if (Boolean.getBoolean("idea.active")) {
            this.project.getDependencies().add(
                minecraft.getName(),
                project.files(artifactsTask.flatMap(VanillaArtifactsTask::getSourcesAndCompiled)).builtBy(artifactsTask)
            );
        } else {
            this.project.getDependencies().add(
                minecraft.getName(),
                project.files(artifactsTask.flatMap(VanillaArtifactsTask::getCompiled)).builtBy(artifactsTask)
            );
        }
        this.project.getDependencies().add(
            clientMinecraft.get().getName(),
            project.files(artifactsTask.flatMap(VanillaArtifactsTask::getClientResources)).builtBy(artifactsTask)
        );

        var neoFormOnlyConfiguration = project.getConfigurations().maybeCreate("crochet"+StringUtils.capitalize(name)+"NeoFormOnly");
        this.project.getDependencies().addProvider(
            neoFormOnlyConfiguration.getName(),
            this.getNeoFormModule(),
            dependency -> dependency.setTransitive(false)
        );

        this.extractConfig = project.getTasks().register(name + "CrochetExtractNeoFormConfig", ExtractConfigTask.class, task -> {
            task.getNeoForm().from(neoFormOnlyConfiguration);
            task.getNeoFormConfig().set(workingDirectory.get().file("config.json"));
        });

        extension.idePostSync.configure(t -> t.dependsOn(artifactsTask));
    }

    public abstract Property<String> getNeoFormModule();

    public void setNeoFormVersion(String version) {
        getNeoFormModule().set("net.neoforged:neoform:" + version);
    }

    @Override
    public void forSourceSet(SourceSet sourceSet) {
        super.forSourceSet(sourceSet);
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(clientMinecraft.get());
        });
    }
}
