package dev.lukebemish.crochet.features;

import dev.lukebemish.crochet.CrochetSharedProjectExtension;
import dev.lukebemish.crochet.CrochetSharedProjectPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JvmEcosystemPlugin;

public abstract class FeatureRoutingPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(CrochetSharedProjectPlugin.class);
        project.getPluginManager().apply(JvmEcosystemPlugin.class);
        var crochetExtension = project.getExtensions().getByType(CrochetSharedProjectExtension.class);
            crochetExtension.getExtensions().create("features", FeatureRoutingExtension.class);
    }
}
