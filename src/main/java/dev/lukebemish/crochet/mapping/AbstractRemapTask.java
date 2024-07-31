package dev.lukebemish.crochet.mapping;

import dev.lukebemish.crochet.mapping.config.RemapParameters;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Nested;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;

public abstract class AbstractRemapTask extends DefaultTask {
    @Nested
    public abstract RemapParameters getRemapParameters();

    @Inject
    protected abstract ExecOperations getExecOperations();

    protected void remap(Path inputPath, Path outputPath) {
        var tmpDir = this.getTemporaryDir().toPath();

        getRemapParameters().execute(
            getExecOperations(),
            outputPath,
            inputPath,
            tmpDir,
            List.of()
        );
    }
}
