package dev.lukebemish.crochet.internal;

import dev.lukebemish.taskgraphrunner.daemon.DaemonExecutor;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.Optional;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TaskGraphRunnerService implements BuildService<TaskGraphRunnerService.Params>, AutoCloseable {
    public static final String STACKTRACE_PROPERTY = "dev.lukebemish.taskgraphrunner.hidestacktrace";
    public static final String LOG_LEVEL_PROPERTY = "org.slf4j.simpleLogger.defaultLogLevel";

    @Override
    public void close() {
        synchronized (this) {
            if (daemon != null) {
                List<Throwable> suppressed = new ArrayList<>();
                try {
                    for (var taskRecord : taskRecordJsons.entrySet()) {
                        var args = new ArrayList<String>();
                        args.add("--cache-dir=" + taskRecord.getKey().toAbsolutePath());
                        args.add("mark");
                        for (var record : taskRecord.getValue()) {
                            if (Files.exists(record)) {
                                args.add(record.toAbsolutePath().toString());
                            }
                        }
                        daemon.execute(args.toArray(String[]::new));
                    }
                    for (var cacheDir : cacheDirs) {
                        var args = new ArrayList<String>();
                        args.add("--cache-dir=" + cacheDir.toAbsolutePath());
                        args.add("clean");
                        args.add("--asset-duration=" + getParameters().getRemoveUnusedAssetsAfterDays().get());
                        args.add("--output-duration=" + getParameters().getRemoveUnusedOutputsAfterDays().get());
                        args.add("--lock-duration=" + getParameters().getRemoveUnusedLocksAfterDays().get());
                        daemon.execute(args.toArray(String[]::new));
                    }
                } catch (Throwable t) {
                    suppressed.add(t);
                }
                try {
                    daemon.close();
                    daemon = null;
                } catch (Throwable t) {
                    suppressed.add(t);
                }
                if (!suppressed.isEmpty()) {
                    var e = new RuntimeException("Failed to close daemon", suppressed.getFirst());
                    if (suppressed.size() > 1) {
                        for (var t : suppressed.subList(1, suppressed.size())) {
                            e.addSuppressed(t);
                        }
                    }
                    throw e;
                }
            }
        }
    }

    private DaemonExecutor daemon;

    private final Map<Path, List<Path>> taskRecordJsons = new ConcurrentHashMap<>();

    private final Set<Path> cacheDirs = ConcurrentHashMap.newKeySet();

    public void addTaskRecordJson(Path cacheDir, Path taskRecordJson) {
        taskRecordJsons.computeIfAbsent(cacheDir.toAbsolutePath(), k -> new ArrayList<>()).add(taskRecordJson.toAbsolutePath());
        addCacheDir(cacheDir);
    }

    public void addCacheDir(Path cacheDir) {
        cacheDirs.add(cacheDir.toAbsolutePath());
    }

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
        @Optional
        public abstract Property<Integer> getRemoveUnusedAssetsAfterDays();
        @Optional
        public abstract Property<Integer> getRemoveUnusedOutputsAfterDays();
        @Optional
        public abstract Property<Integer> getRemoveUnusedLocksAfterDays();
    }
}
