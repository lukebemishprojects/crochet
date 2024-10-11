package dev.lukebemish.crochet.tools;

import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@CommandLine.Command(
        name = "combine-sources",
        description = "Combine sources from multiple jars into a single source jar"
)
public class CombineSources implements Runnable {
    @CommandLine.Option(
        names = {"-i", "--input"},
        description = "Input source zips",
        arity = "0..*"
    )
    List<Path> inputs = List.of();

    @CommandLine.Option(
        names = {"-o", "--output"},
        description = "Output zip",
        required = true
    )
    Path outputFile;

    @CommandLine.Option(
        names = "--duplicate",
        description = "File to save duplicate entries to",
        required = true
    )
    Path duplicatesFile;

    @Override
    public void run() {
        Set<String> sourceFiles = new HashSet<>();
        try (var os = Files.newOutputStream(outputFile);
             var zos = new ZipOutputStream(os);
             var duplicates = Files.newBufferedWriter(duplicatesFile, StandardCharsets.UTF_8)) {
            for (var input : inputs) {
                try (var is = Files.newInputStream(input);
                     var zis = new ZipInputStream(is)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.isDirectory()) {
                            continue;
                        }
                        if (!entry.getName().endsWith(".java")) {
                            continue;
                        }
                        if (sourceFiles.add(entry.getName())) {
                            zos.putNextEntry(new ZipEntry(entry.getName()));
                            zis.transferTo(zos);
                            zos.closeEntry();
                        } else {
                            duplicates.write(entry.getName());
                            duplicates.newLine();
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
