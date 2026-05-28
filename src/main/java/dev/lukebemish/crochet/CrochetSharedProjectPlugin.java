package dev.lukebemish.crochet;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public abstract class CrochetSharedProjectPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getExtensions().create("crochet", CrochetSharedProjectExtension.class);
    }
}
