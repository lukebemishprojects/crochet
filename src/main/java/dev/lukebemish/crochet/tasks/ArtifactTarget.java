package dev.lukebemish.crochet.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public abstract class ArtifactTarget {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSource();

    @OutputFile
    public abstract RegularFileProperty getTarget();

    @Internal
    public abstract ListProperty<String> getCapabilities();
}
