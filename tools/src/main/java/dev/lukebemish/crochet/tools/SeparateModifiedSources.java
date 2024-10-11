package dev.lukebemish.crochet.tools;

import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@CommandLine.Command(
        name = "separate-modified-sources",
        description = "Separate modified sources from a source jar matching with original source jars"
)
public class SeparateModifiedSources implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(SeparateModifiedSources.class.getName());

    static class Target {
        @CommandLine.Parameters(index = "0", description = "File to modify.")
        Path source;
        @CommandLine.Parameters(index = "1", description = "Location to place modified file.") Path target;
    }

    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1..*")
    List<RemapMods.Target> targets;

    @CommandLine.Option(
        names = "--modified",
        description = "Modified sources jar",
        required = true
    )
    Path modifiedFile;

    @CommandLine.Option(
        names = "--duplicate",
        description = "File to save duplicate entries to",
        required = true
    )
    Path duplicatesFile;

    @Override
    public void run() {
        try {
            var duplicates = Files.readAllLines(duplicatesFile, StandardCharsets.UTF_8).stream().filter(it -> !it.isBlank()).collect(Collectors.toSet());

            try (var modifiedZipFile = new ZipFile(modifiedFile.toFile())) {
                for (var target : targets) {
                    try (var is = Files.newInputStream(target.source);
                         var zis = new ZipInputStream(is);
                         var os = Files.newOutputStream(target.target);
                            var zos = new ZipOutputStream(os)) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            if (entry.isDirectory()) {
                                continue;
                            }
                            var modifiedEntry = modifiedZipFile.getEntry(entry.getName());
                            if (modifiedEntry != null) {
                                if (duplicates.contains(entry.getName())) {
                                    // If it's a duplicate, don't write it
                                    LOGGER.warning("Duplicate source entry found: " + entry.getName());
                                    continue;
                                }
                                zos.putNextEntry(modifiedEntry);
                                modifiedZipFile.getInputStream(modifiedEntry).transferTo(zos);
                                zos.closeEntry();
                            } else {
                                zos.putNextEntry(entry);
                                zis.transferTo(zos);
                                zos.closeEntry();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
