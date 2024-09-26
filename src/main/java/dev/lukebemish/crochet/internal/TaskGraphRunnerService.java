package dev.lukebemish.crochet.internal;

import dev.lukebemish.taskgraphrunner.daemon.DaemonExecutor;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.Optional;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class TaskGraphRunnerService implements BuildService<TaskGraphRunnerService.Params>, AutoCloseable {
    public static final String STACKTRACE_PROPERTY = "dev.lukebemish.taskgraphrunner.hidestacktrace";
    public static final String LOG_LEVEL_PROPERTY = "org.slf4j.simpleLogger.defaultLogLevel";

    @Override
    public void close() {
        synchronized (this) {
            if (daemon != null) {
                daemon.close();
                daemon = null;
            }
        }
    }

    private DaemonExecutor daemon;

    public DaemonExecutor start(JavaLauncher javaLauncher, String jarPath, Map<String, String> stringStringMap) {
        synchronized (this) {
            if (daemon == null) {
                daemon = new DaemonExecutor(processBuilder -> {
                    List<String> args = new ArrayList<>();
                    args.add(javaLauncher.getExecutablePath().toString());
                    if (getParameters().getLogLevel().isPresent()) {
                        args.add("-D"+LOG_LEVEL_PROPERTY+"="+getParameters().getLogLevel().get());
                    }
                    args.add("-D"+STACKTRACE_PROPERTY+"="+getParameters().getHideStacktrace().getOrElse(false));
                    for (Map.Entry<String, String> entry : stringStringMap.entrySet()) {
                        args.add("-D"+entry.getKey()+"="+entry.getValue());
                    }
                    args.add("-Dstdout.encoding=UTF-8");
                    args.add("-Dstderr.encoding=UTF-8");
                    args.add("-jar");
                    args.add(jarPath);
                    processBuilder.command(args);
                });
            }
            return daemon;
        }
    }

    public abstract static class Params implements BuildServiceParameters {
        @Optional
        public abstract Property<String> getLogLevel();
        @Optional
        public abstract Property<Boolean> getHideStacktrace();
    }
}
