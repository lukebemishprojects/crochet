package dev.lukebemish.crochet.fabric;

import dev.lukebemish.crochet.CrochetExtension;
import dev.lukebemish.crochet.CrochetPlugin;
import dev.lukebemish.crochet.mapping.Mappings;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

public class FabricConventionPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(CrochetPlugin.class);

        var extension = project.getExtensions().getByType(CrochetExtension.class);

        extension.useTinyRemapper();

        Mappings intermediaryToNamed = extension.mappings("intermediary", "named");
        Mappings namedToIntermediary = extension.mappings("named", "intermediary");

        JavaPluginExtension javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);
        javaPlugin.getSourceSets().configureEach(sourceSet -> {
            Configuration runtimeConfiguration = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
            Configuration compileConfiguration = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
            extension.remap(runtimeConfiguration, intermediaryToNamed);
            extension.remap(compileConfiguration, intermediaryToNamed);
        });

        SourceSet mainSourceSet = javaPlugin.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        extension.remapOutgoing(mainSourceSet, namedToIntermediary);
    }
}
