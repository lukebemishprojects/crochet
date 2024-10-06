package dev.lukebemish.crochet.tools;

import com.google.gson.JsonObject;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.MetaInfFixer;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import net.neoforged.srgutils.IMappingFile;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

@CommandLine.Command(name = "remap-mods", mixinStandardHelpOptions = true, description = "Remap mods.")
class RemapMods implements Runnable {
    static class Target {
        @CommandLine.Parameters(index = "0", description = "File to remap.")
        Path source;
        @CommandLine.Parameters(index = "1", description = "Location to place remapped file.") Path target;
    }

    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1..*")
    List<Target> targets;

    @CommandLine.Option(names = "--mappings", description = "Mappings file, in a format SRGUtils can read.")
    Path mappingsFile;

    @CommandLine.Option(names = "--classpath", description = "Remapping classpath.", converter = ClasspathConverter.class)
    List<Path> remappingClasspath = new ArrayList<>();

    static final class ClasspathConverter implements CommandLine.ITypeConverter<List<Path>> {
        @Override
        public List<Path> convert(String value) {
            return Arrays.stream(value.split(File.pathSeparator)).map(Paths::get).toList();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(RemapMods.class.getName());

    static Map<String, AWData> readAccessWideners(Path jar) throws IOException {
        var out = new HashMap<String, AWData>();
        try (var zip = new ZipFile(jar.toFile())) {
            var fmj = zip.getEntry("fabric.mod.json");
            if (fmj != null) {
                try {
                    var json = Utils.GSON.fromJson(new InputStreamReader(zip.getInputStream(fmj), StandardCharsets.UTF_8), JsonObject.class);
                    var aw = json.get("accessWidener");
                    if (aw != null) {
                        var awEntry = zip.getEntry(aw.getAsString());
                        if (awEntry != null) {
                            try (var stream = zip.getInputStream(awEntry)) {
                                var content = stream.readAllBytes();
                                var header = AccessWidenerReader.readHeader(content);
                                out.put(aw.getAsString(), new AWData(header, content));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Invalid FMJ -- we should ignore it but log it
                    LOGGER.warning("Invalid fabric.mod.json in " + jar + ": " + e);
                }
            }
        }
        return out;
    }

    record AWData(AccessWidenerReader.Header header, byte[] content) {}

    static byte[] remapAccessWidener(byte[] input, IMappingFile mappings) {
        var header = AccessWidenerReader.readHeader(input);
        if (!header.getNamespace().equals("intermediary")) {
            return input;
        }

        int version = AccessWidenerReader.readVersion(input);

        AccessWidenerWriter writer = new AccessWidenerWriter(version);
        AccessWidenerRemapper awRemapper = new AccessWidenerRemapper(
            writer,
            Utils.remapperForFile(mappings),
            "intermediary",
            "named"
        );
        AccessWidenerReader reader = new AccessWidenerReader(awRemapper);
        reader.read(input);
        return writer.write();
    }

    @Override
    public void run() {
        try {
            IMappingFile mappings = IMappingFile.load(this.mappingsFile.toFile());
            IMappingProvider mappingProvider = mappingProvider(mappings);

            List<ModData> modData = new ArrayList<>();
            List<Target> remapTargets = new ArrayList<>();
            for (var target : targets) {
                var data = new ModData(target.source);
                if (data.shouldRemap) {
                    modData.add(data);
                    remapTargets.add(target);
                } else {
                    Files.createDirectories(target.target.getParent());
                    Files.copy(target.source, target.target, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            Set<String> knownIndyBsm = new HashSet<>();
            for (var data : modData) {
                knownIndyBsm.addAll(data.knownIndyBsms);
            }

            var builder = TinyRemapper.newRemapper()
                .withMappings(mappingProvider)
                .renameInvalidLocals(false)
                .withKnownIndyBsm(knownIndyBsm);

            final Set<InputTag> remapMixins = new HashSet<>();
            var needsStaticMixinRemapping = modData.stream().anyMatch(it -> it.mixinRemapType == ModData.MixinRemapType.STATIC);
            if (needsStaticMixinRemapping) {
                builder = builder.extension(new MixinExtension(remapMixins::contains));
            }

            var tinyRemapper = builder.build();
            tinyRemapper.readClassPathAsync(remappingClasspath.toArray(Path[]::new));

            InputTag[] tags = new InputTag[remapTargets.size()];
            for (int i = 0; i < remapTargets.size(); i++) {
                var target = remapTargets.get(i);
                tags[i] = tinyRemapper.createInputTag();
                tinyRemapper.readInputsAsync(tags[i], target.source);
                if (modData.get(i).mixinRemapType == ModData.MixinRemapType.STATIC) {
                    remapMixins.add(tags[i]);
                } else {
                    // TODO: old fashioned mixin remapping
                }
            }

            List<OutputConsumerPath> paths = new ArrayList<>();
            List<IOException> exceptions = new ArrayList<>();

            try {
                for (int i = 0; i < remapTargets.size(); i++) {
                    var target = remapTargets.get(i);

                    var transforms = ZipTransforms.create();
                    transforms.withJson(Map.of("fabric.mod.json", json -> {
                        json.remove("jars");
                        return json;
                    }));
                    var accessWideners = readAccessWideners(target.source);
                    var awTransforms = new HashMap<String, ZipTransforms.IoUnaryOperator<byte[]>>();
                    for (var entry : accessWideners.entrySet()) {
                        var data = entry.getValue();
                        var remapped = remapAccessWidener(data.content, mappings);
                        if (remapped != data.content) {
                            awTransforms.put(entry.getKey(), bytes -> remapped);
                        }
                    }
                    transforms.with(awTransforms);
                    transforms.with(Map.of("META-INF/MANIFEST.MF", bytes -> {
                        var manifest = new Manifest(new ByteArrayInputStream(bytes));

                        Attributes mainAttrs = manifest.getMainAttributes();

                        mainAttrs.putValue("Fabric-Mapping-Namespace", "named");

                        // Fix all the stuff tiny-remapper would normally do
                        mainAttrs.remove(Attributes.Name.SIGNATURE_VERSION);
                        manifest.getEntries().values().forEach(it -> {
                            it.entrySet().removeIf(e -> {
                                var name = e.getKey().toString();
                                return name.endsWith("-Digest") || name.contains("-Digest-") || name.equals("Magic");
                            });
                        });
                        manifest.getEntries().values().removeIf(Attributes::isEmpty);

                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        manifest.write(out);
                        return out.toByteArray();
                    }));

                    var path = new OutputConsumerPath.Builder(target.target).assumeArchive(true).build();
                    paths.add(path);
                    path.addNonClassFiles(target.source, tinyRemapper, List.of(transforms, MetaInfFixer.INSTANCE));
                    tinyRemapper.apply(path, tags[i]);
                }
            } catch (IOException e) {
                exceptions.add(e);
            } finally {
                tinyRemapper.finish();
                for (OutputConsumerPath path : paths) {
                    try {
                        path.close();
                    } catch (IOException e) {
                        exceptions.add(e);
                    }
                }
            }

            if (!exceptions.isEmpty()) {
                var e = new IOException("Failed to remap mods");
                for (IOException ex : exceptions) {
                    e.addSuppressed(ex);
                }
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private IMappingProvider mappingProvider(IMappingFile mappings) {
        return acceptor -> {
            mappings.getClasses().forEach(iClass -> {
                acceptor.acceptClass(iClass.getOriginal(), iClass.getMapped());
                iClass.getFields().forEach(iField -> {
                    acceptor.acceptField(new IMappingProvider.Member(
                        iClass.getOriginal(),
                        iField.getOriginal(),
                        iField.getDescriptor()
                    ), iField.getMapped());
                });
                iClass.getMethods().forEach(iMethod -> {
                    acceptor.acceptMethod(new IMappingProvider.Member(
                        iClass.getOriginal(),
                        iMethod.getOriginal(),
                        iMethod.getDescriptor()
                    ), iMethod.getMapped());
                });
            });
        };
    }
}
