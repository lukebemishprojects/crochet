package dev.lukebemish.crochet.wrappers.tinyremapper;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.neoforged.srgutils.IMappingFile;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@CommandLine.Command(name = "tiny-remapper", mixinStandardHelpOptions = true)
public class Main implements Callable<Integer>  {
    private static final Pattern INVALID_LV_PATTERN = Pattern.compile("\\$\\$\\d+");

    static class Target {
        @CommandLine.Parameters(index = "0", description = "File to remap.") Path source;
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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws IOException {
        IMappingFile mappings = IMappingFile.load(this.mappingsFile.toFile());
        IMappingProvider mappingProvider = mappingProvider(mappings);

        var builder = TinyRemapper.newRemapper()
            .withMappings(mappingProvider)
            .renameInvalidLocals(true)
            .rebuildSourceFilenames(true)
            .invalidLvNamePattern(INVALID_LV_PATTERN)
            .inferNameFromSameLvIndex(true);

        var tinyRemapper = builder.build();
        tinyRemapper.readClassPathAsync(remappingClasspath.toArray(Path[]::new));

        InputTag[] tags = new InputTag[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            tags[i] = tinyRemapper.createInputTag();
            tinyRemapper.readInputsAsync(tags[i], target.source);
        }

        List<OutputConsumerPath> paths = new ArrayList<>();
        IOException exception = null;

        try {
            for (int i = 0; i < targets.size(); i++) {
                var target = targets.get(i);
                var path = new OutputConsumerPath.Builder(target.target).assumeArchive(true).build();
                paths.add(path);
                tinyRemapper.apply(path, tags[i]);
                path.addNonClassFiles(target.source, NonClassCopyMode.FIX_META_INF, tinyRemapper);
            }
        } finally {
            List<IOException> exceptions = new ArrayList<>();
            for (OutputConsumerPath path : paths) {
                try {
                    path.close();
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
            if (!exceptions.isEmpty()) {
                IOException e = new IOException("Failed to close output consumers");
                for (IOException ex : exceptions) {
                    e.addSuppressed(ex);
                }
                exception = e;
            }
        }
        tinyRemapper.finish();
        if (exception != null) {
            throw exception;
        }

        return 0;
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
