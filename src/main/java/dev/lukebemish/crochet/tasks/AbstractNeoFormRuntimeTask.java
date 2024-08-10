package dev.lukebemish.crochet.tasks;

import dev.lukebemish.crochet.internal.PropertiesUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNeoFormRuntimeTask extends DefaultTask {

    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    // We want to use gradle-downloaded artifacts everywhere if possible
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArtifactManifest();

    @Nested
    abstract Property<JavaLauncher> getJavaLauncher();

    @Internal
    protected abstract DirectoryProperty getRuntimeCacheDirectory();

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    public AbstractNeoFormRuntimeTask() {
        // We match what ModDevGradle does here so that we can share that cache
        getRuntimeCacheDirectory().set(
            new File(getProject().getGradle().getGradleUserHomeDir(), "caches/neoformruntime")
        );

        // Default to java 21 for NFRT
        getJavaLauncher().convention(getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
    }

    protected void invokeNFRT(List<String> arguments) {
        var fullArguments = new ArrayList<>(arguments);
        fullArguments.add(0, "--home-dir");
        fullArguments.add(1, getRuntimeCacheDirectory().get().getAsFile().getAbsolutePath());
        fullArguments.add(2, "--work-dir");
        fullArguments.add(3, getTemporaryDir().getAbsolutePath());

        fullArguments.add("--artifact-manifest");
        fullArguments.add(getArtifactManifest().get().getAsFile().getAbsolutePath());
        // Ideally we'd like to provide an option to make this error -- not sure NFRT supports that at present
        fullArguments.add("--warn-on-artifact-manifest-miss");

        // TODO:
        // - verbose logging
        // - emojis in IDE output? No clue if this even matters...

        getExecOperations().javaexec(execSpec -> {
            // Network properties should be the same as NFRT may be doing network stuff
            execSpec.systemProperties(PropertiesUtils.networkProperties(getProviderFactory()).get());

            // See https://github.com/gradle/gradle/issues/28959
            execSpec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");

            execSpec.executable(getJavaLauncher().get().getExecutablePath().getAsFile());
            execSpec.classpath(getRuntimeClasspath());
            execSpec.args(fullArguments);
        });
    }
}
