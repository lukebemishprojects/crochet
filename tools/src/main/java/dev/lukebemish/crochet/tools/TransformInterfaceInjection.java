package dev.lukebemish.crochet.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.neoforged.srgutils.IMappingFile;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    private final Gson GSON = new Gson();

    @Override
    public void run() {
        try {
            Map<String, Set<String>> neoInjections = new LinkedHashMap<>();
            for (var neoInput : neoInputs) {
                try (var reader = Files.newBufferedReader(neoInput)) {
                    var json = GSON.fromJson(reader, JsonObject.class);
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
                    var json = GSON.fromJson(reader, JsonObject.class);
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
                var array = GSON.toJsonTree(value).getAsJsonArray();
                json.add(key, array);
            }

            try (var writer = Files.newBufferedWriter(outputFile)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String remapAndConvert(Remapper remapper, String full) {
        var initialBracket = full.indexOf('<');
        if (initialBracket == -1) {
            return remapper.map(full);
        }
        var remappedBinary = remapper.map(full.substring(0, initialBracket));
        var justGenerics = full.substring(initialBracket);
        String remappedSignature = remapper.mapSignature("Ldoes/not/Exist" + justGenerics + ";", true);
        var builder = new StringBuilder();
        var reader = new SignatureReader(remappedSignature);
        reader.acceptType(new ParameterCollectingVisitor(builder));
        return remappedBinary.replace('/', '.').replace('$', '.') + builder.substring("does.not.Exist".length());
    }

    private static class ParameterCollectingVisitor extends SignatureVisitor {
        private final List<String> parameters = new ArrayList<>();
        private final StringBuilder outer;
        private int arrayCount;

        private ParameterCollectingVisitor(StringBuilder outer) {
            super(Opcodes.ASM9);
            this.outer = outer;
        }

        @Override
        public void visitTypeVariable(String name) {
            outer.append(name);
            visitEnd();
        }

        @Override
        public void visitClassType(String name) {
            outer.append(name.replace('/', '.').replace('$', '.'));
        }

        @Override
        public SignatureVisitor visitArrayType() {
            arrayCount++;
            return this;
        }

        @Override
        public void visitInnerClassType(String name) {
            dumpTypeBuffer();
            outer.append(".").append(name);
        }

        @Override
        public void visitTypeArgument() {
            this.parameters.add("?");
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            StringBuilder parameter = new StringBuilder();
            ParameterCollectingVisitor nested = new ParameterCollectingVisitor(parameter) {
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    parameters.add(parameter.toString());
                }
            };
            switch (wildcard) {
                case '+' -> nested.outer.append("? extends ");
                case '-' -> nested.outer.append("? super ");
            }
            return nested;
        }

        @Override
        public void visitBaseType(char descriptor) {
            switch (descriptor) {
                case 'V' -> outer.append("void");
                case 'Z' -> outer.append("boolean");
                case 'C' -> outer.append("char");
                case 'B' -> outer.append("byte");
                case 'S' -> outer.append("short");
                case 'I' -> outer.append("int");
                case 'F' -> outer.append("float");
                case 'J' -> outer.append("long");
                case 'D' -> outer.append("double");
            }
            visitEnd();
        }

        @Override
        public void visitEnd() {
            dumpTypeBuffer();
            outer.append("[]".repeat(arrayCount));
        }

        private void dumpTypeBuffer() {
            if (!parameters.isEmpty()) {
                outer.append('<');
                for (int i = 0; i < parameters.size(); i++) {
                    if (i != 0) {
                        outer.append(", ");
                    }
                    outer.append(parameters.get(i));
                }
                outer.append('>');
            }
            parameters.clear();
        }
    }
}
