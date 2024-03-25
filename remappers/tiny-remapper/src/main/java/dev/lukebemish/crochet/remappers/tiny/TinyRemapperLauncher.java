package dev.lukebemish.crochet.remappers.tiny;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.regex.Pattern;

public class TinyRemapperLauncher {
    private static final Pattern INVALID_LV_PATTERN = Pattern.compile("\\$\\$\\d+");

    public static void main(String[] argFile) {
        String[] argStrings;
        try {
            argStrings = Files.readString(Path.of(argFile[0])).trim().split("\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var args = Arguments.of(argStrings);

        VisitableMappingTree mappings = new MemoryMappingTree();

        Path mappingsFile = args.mappings();
        if (!Files.exists(mappingsFile)) {
            throw new IllegalArgumentException("Mappings file does not exist: " + mappingsFile);
        } else if (mappingsFile.toString().endsWith(".jar")) {
            var output = args.tmpDir().resolve("mappings.tiny");
            try (FileSystem fs = FileSystems.newFileSystem(mappingsFile, Map.of("create", "true"))) {
                Path nf = fs.getPath("mappings","mappings.tiny");
                if (!Files.exists(nf)) {
                    throw new RuntimeException("No tiny mappings found in mappings jar");
                }
                Files.copy(nf, output, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mappingsFile = output;
        }

        try {
            MappingReader.read(mappingsFile, mappings);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        IMappingProvider mappingProvider = mappingProvider(mappings, args);

        var builder = TinyRemapper.newRemapper()
            .withMappings(mappingProvider)
            .renameInvalidLocals(true)
            .rebuildSourceFilenames(true)
            .invalidLvNamePattern(INVALID_LV_PATTERN)
            .inferNameFromSameLvIndex(true);

        var tinyRemapper = builder.build();
        tinyRemapper.readClassPathAsync(args.classpath().toArray(Path[]::new));
        tinyRemapper.readInputsAsync(args.input());

        try (var outPath = new OutputConsumerPath.Builder(args.output())
            .assumeArchive(true)
            .build()
        ) {
            tinyRemapper.apply(outPath);
            outPath.addNonClassFiles(args.input(), NonClassCopyMode.FIX_META_INF, tinyRemapper);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            tinyRemapper.finish();
        }
    }

    private static IMappingProvider mappingProvider(VisitableMappingTree mappings, Arguments args) {
        return acceptor -> {
            int from = mappings.getNamespaceId(args.sourceNs());
            int to = mappings.getNamespaceId(args.targetNs());

            for (var classDef : mappings.getClasses()) {
                var originalName = classDef.getName(from);
                var newName = classDef.getName(to);

                if (newName == null) {
                    newName = originalName;
                }

                acceptor.acceptClass(originalName, newName);

                for (var fieldDef : classDef.getFields()) {
                    acceptor.acceptField(new IMappingProvider.Member(
                        originalName, fieldDef.getName(from), fieldDef.getDesc(from)), fieldDef.getName(to)
                    );
                }

                for (var methodDef : classDef.getMethods()) {
                    var member = new IMappingProvider.Member(
                        originalName, methodDef.getName(from), methodDef.getDesc(from)
                    );
                    acceptor.acceptMethod(member, methodDef.getName(to));

                    if (args.remapLocalVariables()) {
                        for (var parameterDef : methodDef.getArgs()) {
                            var name = parameterDef.getName(to);
                            if (name != null) {
                                acceptor.acceptMethodArg(member, parameterDef.getLvIndex(), name);
                            }
                        }

                        for (var localVariableDef : methodDef.getVars()) {
                            acceptor.acceptMethodVar(member, localVariableDef.getLvIndex(),
                                localVariableDef.getStartOpIdx(), localVariableDef.getLvtRowIndex(),
                                localVariableDef.getName(to));
                        }
                    }
                }
            }
        };
    }
}
