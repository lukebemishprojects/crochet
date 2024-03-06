package dev.lukebemish.crochet.remappers.tiny;

import net.fabricmc.mappingio.MappingUtil;

import java.nio.file.Path;
import java.util.*;

record Arguments(
    Path mappings,
    Path output,
    Path input,
    List<Path> classpath,
    String sourceNs,
    String targetNs,
    boolean remapLocalVariables
) {
    private enum ArgType {
        MAPPINGS, CLASSPATH, OUTPUT, INPUT, SOURCE, TARGET, REMAP_LOCALS;

        public static final Map<String, ArgType> BY_ARGUMENT;

        static {
            BY_ARGUMENT = new HashMap<>();
            for (var type : values()) {
                BY_ARGUMENT.put("--"+type.name().toLowerCase(Locale.ROOT).replace('_', '-'), type);
            }
        }
    }

    static Arguments of(String[] args) {
        Path mappingsFile = null;
        Path outputFile = null;
        Path inputFile = null;
        List<Path> classpath = new ArrayList<>();
        String sourceNs = null;
        String targetNs = null;
        Boolean remapLocals = null;

        ArgType current = null;
        for (String arg : args) {
            var maybeArg = ArgType.BY_ARGUMENT.get(arg);
            if (maybeArg != null) {
                current = maybeArg;
            } else {
                if (arg.startsWith("--")) {
                    throw new IllegalArgumentException("Unrecognized argument " + arg);
                }
                if (current == null) {
                    throw new IllegalArgumentException("Unrecognized value without argument name " + arg);
                }
                switch (current) {
                    case MAPPINGS:
                        if (mappingsFile != null) {
                            throw new IllegalArgumentException("Duplicate --mappings argument");
                        }
                        mappingsFile = Path.of(arg);
                        current = null;
                        break;
                    case CLASSPATH:
                        classpath.add(Path.of(arg));
                        break;
                    case OUTPUT:
                        if (outputFile != null) {
                            throw new IllegalArgumentException("Duplicate --output argument");
                        }
                        outputFile = Path.of(arg);
                        current = null;
                        break;
                    case INPUT:
                        if (inputFile != null) {
                            throw new IllegalArgumentException("Duplicate --input argument");
                        }
                        inputFile = Path.of(arg);
                        current = null;
                        break;
                    case SOURCE:
                        if (sourceNs != null) {
                            throw new IllegalArgumentException("Duplicate --source argument");
                        }
                        sourceNs = arg;
                        current = null;
                        break;
                    case TARGET:
                        if (targetNs != null) {
                            throw new IllegalArgumentException("Duplicate --target argument");
                        }
                        targetNs = arg;
                        current = null;
                        break;
                    case REMAP_LOCALS:
                        if (remapLocals != null) {
                            throw new IllegalArgumentException("Duplicate --remap-locals argument");
                        }
                        remapLocals = Boolean.parseBoolean(arg);
                        current = null;
                        break;
                }
            }
        }
        if (mappingsFile == null) {
            throw new IllegalArgumentException("No --mappings argument");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("No --output argument");
        }
        if (inputFile == null) {
            throw new IllegalArgumentException("No --input argument");
        }
        if (sourceNs == null) {
            sourceNs = MappingUtil.NS_SOURCE_FALLBACK;
        }
        if (targetNs == null) {
            targetNs = MappingUtil.NS_TARGET_FALLBACK;
        }
        if (remapLocals == null) {
            remapLocals = true;
        }

        classpath.remove(inputFile);

        return new Arguments(mappingsFile, outputFile, inputFile, classpath, sourceNs, targetNs, remapLocals);
    }
}
