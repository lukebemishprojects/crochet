package dev.lukebemish.crochet.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class FileListStringifier {
    @InputFiles
    public abstract ConfigurableFileCollection getFiles();

    @InputFiles
    public abstract ConfigurableFileCollection getDirectories();

    @Override
    public String toString() {
        var strings = new ArrayList<String>();
        var roots = getDirectories().getFiles().stream().map(File::toPath).toList();
        for (var file :getFiles()) {
            var path = file.toPath();
            roots.stream().filter(path::startsWith).findFirst().ifPresent(root -> strings.add(root.relativize(path).toString().replace(File.separatorChar, '/')));
        }
        strings.sort(Comparator.naturalOrder());
        return String.join(";", strings);
    }
}
