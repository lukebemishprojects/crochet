package dev.lukebemish.crochet.fabric;

import dev.lukebemish.crochet.CrochetExtension;
import dev.lukebemish.crochet.CrochetPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaLibraryPlugin;

public class FabricConventionPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(CrochetPlugin.class);

        var extension = project.getExtensions().getByType(CrochetExtension.class);

        extension.useTinyRemapper();
    }
}
