package crochet.dev.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;

public class ConventionPlugin implements Plugin<Object> {
    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void apply(Object target) {
        switch (target) {
            case Project project -> {
                // Does nothing at present
            }
            case Settings settings -> {
                settings.getGradle().getLifecycle().beforeProject(project -> {
                    project.getPluginManager().apply(ConventionPlugin.class);
                });
                settings.getExtensions().create("sharedConventions", ConventionExtension.class, settings);
            }
            default -> throw new IllegalStateException("Unexpected value: " + target);
        }
    }
}
