package dev.lukebemish.crochet.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

public abstract class ExtractAccessWideners extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getModJars();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractAccessWideners.class);

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Inject
    public ExtractAccessWideners() {}

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
        for (var file : getModJars()) {
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
                        }
                    } catch (Exception e) {
                        // Invalid FMJ -- we should ignore it but log it
                        LOGGER.warn("Invalid fabric.mod.json in " + file + ": " + e);
                    }
                }
            }
        }
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
    }
}