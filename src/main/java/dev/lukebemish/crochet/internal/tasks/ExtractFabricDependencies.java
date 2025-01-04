package dev.lukebemish.crochet.internal.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

@DisableCachingByDefault(because = "Not worth caching")
public abstract class ExtractFabricDependencies extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getCompileModJars();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getRuntimeModJars();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getFloatingCompileAccessWideners();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getFloatingRuntimeAccessWideners();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getFloatingCompileNeoInterfaceInjections();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getFloatingRuntimeNeoInterfaceInjections();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractFabricDependencies.class);

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Inject
    public ExtractFabricDependencies() {}

    private sealed interface Widener {
        record Field(String owner, String name, String descriptor, AccessWidenerReader.AccessType access) implements Widener {}
        record Method(String owner, String name, String descriptor, AccessWidenerReader.AccessType access) implements Widener {}
        record Class(String name, AccessWidenerReader.AccessType access) implements Widener {}
    }

    @TaskAction
    public void execute() throws IOException {
        getFileSystemOperations().delete(spec -> {
            spec.delete(getOutputDirectory());
        });
        var intermediaryWideners = new HashSet<Widener>();
        var namedWideners = new HashSet<Widener>();
        var interfaceInjections = new HashMap<String, Set<String>>();
        for (var file : getCompileModJars()) {
            forModJar(file, intermediaryWideners, namedWideners, interfaceInjections);
        }
        for (var file : getFloatingCompileAccessWideners()) {
            try (var stream = Files.newInputStream(file.toPath())) {
                readAccessWidener(intermediaryWideners, namedWideners, stream);
            }
        }

        var runtimeIntermediaryWideners = new HashSet<Widener>();
        var runtimeNamedWideners = new HashSet<Widener>();
        var runtimeInterfaceInjections = new HashMap<String, Set<String>>();
        for (var file : getRuntimeModJars()) {
            forModJar(file, runtimeIntermediaryWideners, runtimeNamedWideners, runtimeInterfaceInjections);
        }
        for (var file : getFloatingRuntimeAccessWideners()) {
            try (var stream = Files.newInputStream(file.toPath())) {
                readAccessWidener(runtimeIntermediaryWideners, runtimeNamedWideners, stream);
            }
        }

        intermediaryWideners.retainAll(runtimeIntermediaryWideners);
        namedWideners.retainAll(runtimeNamedWideners);
        interfaceInjections.forEach((key, value) -> value.retainAll(runtimeInterfaceInjections.getOrDefault(key, Set.of())));
        interfaceInjections.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        var directory = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(directory);
        if (!intermediaryWideners.isEmpty()) {
            var writer = new AccessWidenerWriter();
            writer.visitHeader("intermediary");
            intermediaryWideners.stream().sorted(Comparator.comparing(Widener::toString)).forEach(widener ->{
                switch (widener) {
                    case Widener.Class clazz -> {
                        writer.visitClass(clazz.name(), clazz.access(), false);
                    }
                    case Widener.Field field -> {
                        writer.visitField(field.owner(), field.name(), field.descriptor(), field.access(), false);
                    }
                    case Widener.Method method -> {
                        writer.visitMethod(method.owner(), method.name(), method.descriptor(), method.access(), false);
                    }
                }
            });

            var outPath = getOutputDirectory().get().getAsFile().toPath().resolve("intermediary.accesswidener");
            try (var out = Files.newOutputStream(outPath)) {
                out.write(writer.write());
            }
        }
        if (!namedWideners.isEmpty()) {
            var writer = new AccessWidenerWriter();
            writer.visitHeader("named");
            namedWideners.stream().sorted(Comparator.comparing(Widener::toString)).forEach(widener ->{
                switch (widener) {
                    case Widener.Class clazz -> {
                        writer.visitClass(clazz.name(), clazz.access(), false);
                    }
                    case Widener.Field field -> {
                        writer.visitField(field.owner(), field.name(), field.descriptor(), field.access(), false);
                    }
                    case Widener.Method method -> {
                        writer.visitMethod(method.owner(), method.name(), method.descriptor(), method.access(), false);
                    }
                }
            });

            var outPath = getOutputDirectory().get().getAsFile().toPath().resolve("named.accesswidener");
            try (var out = Files.newOutputStream(outPath)) {
                out.write(writer.write());
            }
        }
        if (!interfaceInjections.isEmpty()) {
            var outPath = getOutputDirectory().get().getAsFile().toPath().resolve("interface_injections.json");
            var json = new JsonObject();
            interfaceInjections.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                var key = entry.getKey();
                var value = entry.getValue();
                var array = new JsonArray();
                value.stream().sorted().forEach(array::add);
                json.add(key, array);
            });
            try (var out = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
                GSON.toJson(json, out);
            }
        }

        var neoInterfaceInjections = new HashMap<String, Set<String>>();
        for (var file : getFloatingCompileNeoInterfaceInjections()) {
            try (var stream = Files.newInputStream(file.toPath())) {
                var json = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                for (var entry : json.entrySet()) {
                    var key = entry.getKey();
                    var value = entry.getValue().getAsJsonArray();
                    var set = neoInterfaceInjections.computeIfAbsent(key, k -> new HashSet<>());
                    for (var element : value) {
                        set.add(element.getAsString());
                    }
                }
            }
        }
        var neoRuntimeInterfaceInjections = new HashMap<String, Set<String>>();
        for (var file : getFloatingRuntimeNeoInterfaceInjections()) {
            try (var stream = Files.newInputStream(file.toPath())) {
                var json = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                for (var entry : json.entrySet()) {
                    var key = entry.getKey();
                    var value = entry.getValue().getAsJsonArray();
                    var set = neoRuntimeInterfaceInjections.computeIfAbsent(key, k -> new HashSet<>());
                    for (var element : value) {
                        set.add(element.getAsString());
                    }
                }
            }
        }

        neoInterfaceInjections.forEach((key, value) -> value.retainAll(neoRuntimeInterfaceInjections.getOrDefault(key, Set.of())));
        neoInterfaceInjections.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (!neoInterfaceInjections.isEmpty()) {
            var outPath = getOutputDirectory().get().getAsFile().toPath().resolve("neo_interface_injections.json");
            var json = new JsonObject();
            neoInterfaceInjections.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                var key = entry.getKey();
                var value = entry.getValue();
                var array = new JsonArray();
                value.stream().sorted().forEach(array::add);
                json.add(key, array);
            });
            try (var out = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
                GSON.toJson(json, out);
            }
        }
    }

    private static void forModJar(File file, HashSet<Widener> intermediaryWideners, HashSet<Widener> namedWideners, HashMap<String, Set<String>> interfaceInjections) throws IOException {
        try (var zip = new ZipFile(file)) {
            var fmj = zip.getEntry("fabric.mod.json");
            if (fmj != null) {
                try {
                    var json = GSON.fromJson(new InputStreamReader(zip.getInputStream(fmj), StandardCharsets.UTF_8), JsonObject.class);
                    var aw = json.get("accessWidener");
                    if (aw != null) {
                        var awEntry = zip.getEntry(aw.getAsString());
                        if (awEntry != null) {
                            try (var stream = zip.getInputStream(awEntry)) {
                                readAccessWidener(intermediaryWideners, namedWideners, stream);
                            }
                        }
                    }
                    var custom = json.get("custom");
                    if (custom != null) {
                        var ii = custom.getAsJsonObject().get("loom:injected_interfaces");
                        if (ii != null) {
                            for (var entry : ii.getAsJsonObject().entrySet()) {
                                var key = entry.getKey();
                                var value = entry.getValue().getAsJsonArray();
                                var set = interfaceInjections.computeIfAbsent(key, k -> new HashSet<>());
                                for (var element : value) {
                                    set.add(element.getAsString());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Invalid FMJ -- we should ignore it but log it
                    LOGGER.warn("Invalid fabric.mod.json in " + file + ": " + e);
                }
            }
        }
    }

    private static void readAccessWidener(HashSet<Widener> intermediaryWideners, HashSet<Widener> namedWideners, InputStream stream) throws IOException {
        var bytes = stream.readAllBytes();
        @SuppressWarnings("unchecked") HashSet<Widener>[] wideners = new HashSet[1];
        var reader = new AccessWidenerReader(new AccessWidenerVisitor() {
            @Override
            public void visitHeader(String namespace) {
                if ("intermediary".equals(namespace)) {
                    wideners[0] = intermediaryWideners;
                } else {
                    wideners[0] = namedWideners;
                }
            }

            @Override
            public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {
                if (transitive) {
                    wideners[0].add(new Widener.Class(name, access));
                }
            }

            @Override
            public void visitMethod(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
                if (transitive) {
                    wideners[0].add(new Widener.Method(owner, name, descriptor, access));
                }
            }

            @Override
            public void visitField(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
                if (transitive) {
                    wideners[0].add(new Widener.Field(owner, name, descriptor, access));
                }
            }
        });
        reader.read(bytes);
    }
}
