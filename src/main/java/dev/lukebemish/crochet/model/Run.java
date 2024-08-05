package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.tasks.GenerateArgFiles;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.inject.Inject;

public abstract class Run implements Named {
    private static final String DEV_LAUNCH_MAIN_CLASS = "net.neoforged.devlaunch.Main";

    private final String name;
    final TaskProvider<JavaExec> runTask;
    final TaskProvider<GenerateArgFiles> argFilesTask;
    private MinecraftInstallation installation;

    @Inject
    protected abstract Project getProject();

    @Inject
    protected abstract JavaToolchainService getToolchainService();

    @Inject
    public Run(String name) {
        this.name = name;

        this.getRunDirectory().convention(getProject().getLayout().getBuildDirectory().dir("runs/"+name));

        var rootToolchain = getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain();
        this.getToolchain().getLanguageVersion().convention(rootToolchain.getLanguageVersion());
        this.getToolchain().getVendor().convention(rootToolchain.getVendor());
        this.getToolchain().getImplementation().convention(rootToolchain.getImplementation());

        this.argFilesTask = getProject().getTasks().register("generate"+ StringUtils.capitalize(name)+"ArgFiles", GenerateArgFiles.class, task -> {
            task.getArgFile().convention(getRunDirectory().file("crochet/runs/"+name+"/args.txt"));
            task.getJvmArgFile().convention(getRunDirectory().file("crochet/runs/"+name+"/jvmargs.txt"));
            task.getJvmArgs().convention(getJvmArgs());
            task.getArgs().convention(getArgs());
            task.getMainClass().convention(getMainClass());
        });

        this.runTask = this.getProject().getTasks().register("run"+ StringUtils.capitalize(name), JavaExec.class, task -> {
            task.setGroup("crochet");
            task.setDescription("Run the "+name+" configuration");
            task.getJvmArguments().addAll(getJvmArgs());
            task.getMainClass().set(DEV_LAUNCH_MAIN_CLASS);
            task.classpath(getProject().getConfigurations().getByName(CrochetPlugin.DEV_LAUNCH_CONFIGURATION_NAME));
            task.classpath(getClasspath());
            task.dependsOn(argFilesTask);
            task.getJavaLauncher().set(getToolchainService().launcherFor(getToolchain().asAction()));
            task.args("@"+argFilesTask.get().getArgFile().get().getAsFile().getAbsolutePath());
        });
    }

    @Override
    public String getName() {
        return this.name;
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

    public abstract ConfigurableFileCollection getClasspath();

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
        installation.forRun(this, runType);
    }
}
