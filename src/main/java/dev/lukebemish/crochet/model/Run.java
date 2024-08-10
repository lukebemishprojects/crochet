package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.tasks.DownloadAssetsTask;
import dev.lukebemish.crochet.tasks.GenerateArgFiles;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.GradleTask;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;

import javax.inject.Inject;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public abstract class Run implements Named, Dependencies {
    private static final String DEV_LAUNCH_MAIN_CLASS = "net.neoforged.devlaunch.Main";

    private final String name;
    private final TaskProvider<JavaExec> runTask;
    final TaskProvider<GenerateArgFiles> argFilesTask;
    private MinecraftInstallation installation;

    final Configuration classpath;

    @Inject
    protected abstract JavaToolchainService getToolchainService();

    @Inject
    public Run(String name) {
        this.name = name;
        this.classpath = getProject().getConfigurations().maybeCreate("crochetRun"+StringUtils.capitalize(name)+"Classpath");

        this.getJvmArgs().add("-Dlog4j2.formatMsgNoLookups=true");

        this.getRunDirectory().convention(getProject().getLayout().getBuildDirectory().dir("runs/"+name));

        var rootToolchain = getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain();
        this.getToolchain().getLanguageVersion().convention(rootToolchain.getLanguageVersion());
        this.getToolchain().getVendor().convention(rootToolchain.getVendor());
        this.getToolchain().getImplementation().convention(rootToolchain.getImplementation());

        this.argFilesTask = getProject().getTasks().register("generate"+ StringUtils.capitalize(name)+"ArgFiles", GenerateArgFiles.class, task -> {
            task.setGroup("crochet setup");
            task.getArgFile().convention(getProject().getLayout().getBuildDirectory().file("crochet/runs/"+name+"/args.txt"));
            task.getJvmArgFile().convention(getProject().getLayout().getBuildDirectory().file("crochet/runs/"+name+"/jvmargs.txt"));
            task.getJvmArgs().convention(getJvmArgs());
            task.getArgs().convention(getArgs());
            task.getMainClass().convention(getMainClass());
            task.getRunDirectory().convention(getRunDirectory().map(f -> f.getAsFile().getAbsolutePath()));
        });

        getProject().getExtensions().getByType(CrochetExtension.class).idePostSync.configure(task -> {
            task.dependsOn(argFilesTask);
        });

        this.runTask = this.getProject().getTasks().register("run"+ StringUtils.capitalize(name), JavaExec.class, task -> {
            task.setGroup("crochet");
            task.setDescription("Run the "+name+" configuration");
            task.getMainClass().set(DEV_LAUNCH_MAIN_CLASS);
            task.classpath(getProject().getConfigurations().getByName(CrochetPlugin.DEV_LAUNCH_CONFIGURATION_NAME));
            task.classpath(classpath);
            task.dependsOn(argFilesTask);
            task.workingDir(getRunDirectory());
            task.getJavaLauncher().set(getToolchainService().launcherFor(getToolchain().asAction()));
            task.args("@"+argFilesTask.get().getArgFile().get().getAsFile().getAbsolutePath().replace("\\", "\\\\"));
            task.jvmArgs("@"+argFilesTask.get().getJvmArgFile().get().getAsFile().getAbsolutePath().replace("\\", "\\\\"));
        });

        if (Boolean.getBoolean("idea.active")) {
            SourceSetContainer sourceSets = getProject().getExtensions().getByType(SourceSetContainer.class);
            var dummySourceSet = sourceSets.register("crochet_wrapper_"+name, sourceSet -> {
                getProject().getConfigurations().getByName(sourceSet.getImplementationConfigurationName()).extendsFrom(getProject().getConfigurations().getByName(CrochetPlugin.DEV_LAUNCH_CONFIGURATION_NAME));
                getProject().getConfigurations().getByName(sourceSet.getImplementationConfigurationName()).extendsFrom(classpath);
                sourceSet.getResources().setSrcDirs(List.of());
                sourceSet.getJava().setSrcDirs(List.of());
            });

            getProject().afterEvaluate(p -> {
                dummySourceSet.configure(sourceSet -> {
                    sourceSet.getExtensions().getExtensionsSchema().forEach(schema -> {
                        if (TypeOf.typeOf(SourceDirectorySet.class).isAssignableFrom(schema.getPublicType())) {
                            ((SourceDirectorySet) sourceSet.getExtensions().getByName(schema.getName())).setSrcDirs(List.of());
                        }
                    });
                });

                if (Boolean.getBoolean("idea.sync.active")) {
                    var rootProject = getProject().getRootProject();
                    var idea = rootProject.getExtensions().getByType(IdeaModel.class);
                    var settings = ((ExtensionAware) idea.getProject()).getExtensions().getByType(ProjectSettings.class);
                    var runConfigurations = ((ExtensionAware) settings).getExtensions().getByType(RunConfigurationContainer.class);
                    var runName = getProject().getParent() == null ? getName() : getName() + " (" + getProject().getPath() + ")";
                    runConfigurations.register(runName, Application.class, runConfig -> {
                        runConfig.setMainClass(DEV_LAUNCH_MAIN_CLASS);
                        runConfig.setJvmArgs("@" + argFilesTask.get().getJvmArgFile().get().getAsFile().getAbsolutePath().replace("\\", "\\\\"));
                        runConfig.setProgramParameters("@" + argFilesTask.get().getArgFile().get().getAsFile().getAbsolutePath().replace("\\", "\\\\"));
                        runConfig.moduleRef(getProject(), dummySourceSet.get());
                        runConfig.setWorkingDirectory(getRunDirectory().get().getAsFile().getAbsolutePath());
                        runConfig.beforeRun(beforeRun -> {
                            beforeRun.create(argFilesTask.getName(), GradleTask.class, task -> {
                                task.setTask(argFilesTask.get());
                            });
                        });
                    });
                }
            });
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    protected abstract SetProperty<Mod> getRunMods();

    public void mod(Mod mod) {
        getRunMods().add(mod);
        classpath.extendsFrom(mod.classpath);
    }

    public void mod(Provider<Mod> mod) {
        getRunMods().add(mod);
        classpath.extendsFrom(mod.get().classpath);
    }

    public void mods(Iterable<?> mods) {
        for (var part : mods) {
            if (part instanceof Mod mod) {
                mod(mod);
            } else if (part instanceof Provider<?> provider && provider.get() instanceof Mod mod) {
                mod(mod);
            } else {
                throw new IllegalArgumentException("Unsupported mod type: "+part.getClass());
            }
        }
    }

    public void mods(Object... mods) {
        mods(List.of(mods));
    }

    public abstract DirectoryProperty getRunDirectory();

    public abstract ListProperty<String> getJvmArgs();

    public abstract ListProperty<String> getArgs();

    public abstract Property<String> getMainClass();

    @Nested
    public abstract ToolchainSpec getToolchain();

    public void toolchain(Action<ToolchainSpec> action) {
        action.execute(getToolchain());
    }

    public static abstract class ToolchainSpec {
        public abstract Property<JavaLanguageVersion> getLanguageVersion();
        public abstract Property<JvmVendorSpec> getVendor();
        public abstract Property<JvmImplementation> getImplementation();
        public Action<JavaToolchainSpec> asAction() {
            return spec -> {
                spec.getLanguageVersion().set(getLanguageVersion());
                spec.getVendor().set(getVendor());
                spec.getImplementation().set(getImplementation());
            };
        }
    }

    public abstract DependencyCollector getClasspath();

    public void client(MinecraftInstallation installation) {
        installation(installation, RunType.CLIENT);
    }

    public void server(MinecraftInstallation installation) {
        installation(installation, RunType.SERVER);
    }

    private void installation(MinecraftInstallation installation, RunType runType) {
        if (this.installation != null) {
            throw new IllegalStateException("Installation already set for run "+this.name);
        }
        this.installation = installation;
        this.argFilesTask.configure(task -> {
            task.getAssetsProperties().set(installation.downloadAssetsTask.flatMap(DownloadAssetsTask::getAssetsProperties));
            task.dependsOn(installation.downloadAssetsTask);
        });
        installation.forRun(this, runType);
    }
}
