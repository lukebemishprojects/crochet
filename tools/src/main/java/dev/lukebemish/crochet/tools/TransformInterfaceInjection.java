package dev.lukebemish.crochet.tools;

import com.google.gson.JsonObject;
import dev.lukebemish.taskgraphrunner.signatures.TypeSignature;
import net.neoforged.srgutils.IMappingFile;
import org.objectweb.asm.commons.Remapper;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CommandLine.Command(
    name = "transform-interface-injection",
    description = "Remap and merge fabric interface injection entries"
)
class TransformInterfaceInjection implements Runnable {
    @CommandLine.Option(
        names = {"-i", "--input"},
        description = "Input fabric interface injection collection file",
        arity = "0..*"
    )
    List<Path> inputs = List.of();

    @CommandLine.Option(
        names = "--neo-input",
        description = "Input neo interface injection file",
        arity = "0..*"
    )
    List<Path> neoInputs = List.of();

    @CommandLine.Option(
        names = {"-o", "--output"},
        description = "Output neo interface injection file",
        required = true
    )
    Path outputFile;

    @CommandLine.Option(
        names = {"-m", "--mappings"},
        description = "Mappings file",
        required = true
    )
    Path mappingsFile;

    @Override
    public void run() {
        try {
            Map<String, Set<String>> neoInjections = new LinkedHashMap<>();
            for (var neoInput : neoInputs) {
                try (var reader = Files.newBufferedReader(neoInput)) {
                    var json = Utils.GSON.fromJson(reader, JsonObject.class);
                    for (var entry : json.entrySet()) {
                        var key = entry.getKey();
                        var value = entry.getValue().getAsJsonArray();
                        var set = neoInjections.computeIfAbsent(key, k -> new LinkedHashSet<>());
                        for (var element : value) {
                            set.add(element.getAsString());
                        }
                    }
                }
            }
            IMappingFile mappings;
            try (var mappingsStream = Files.newInputStream(mappingsFile)) {
                mappings = IMappingFile.load(mappingsStream);
            }
            var remapper = Utils.remapperForFile(mappings);
            for (var fabricInput : inputs) {
                try (var reader = Files.newBufferedReader(fabricInput)) {
                    var json = Utils.GSON.fromJson(reader, JsonObject.class);
                    for (var entry : json.entrySet()) {
                        var key = remapper.map(entry.getKey());
                        var value = entry.getValue().getAsJsonArray();
                        var set = neoInjections.computeIfAbsent(key, k -> new LinkedHashSet<>());
                        for (var element : value) {
                            set.add(remapAndConvert(remapper, element.getAsString()));
                        }
                    }
                }
            }

            JsonObject json = new JsonObject();
            for (var entry : neoInjections.entrySet()) {
                var key = entry.getKey();
                var value = entry.getValue();
                var array = Utils.GSON.toJsonTree(value).getAsJsonArray();
                json.add(key, array);
            }

            try (var writer = Files.newBufferedWriter(outputFile)) {
                Utils.GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String remapAndConvert(Remapper remapper, String full) {
        var remappedSignature = remapper.mapSignature("L" + full + ";", true);
        var signature = TypeSignature.fromBinary(remappedSignature);
        return signature.neo();
    }
}
