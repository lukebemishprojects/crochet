package dev.lukebemish.crochet.internal;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;

public class CrochetPlugin implements Plugin<Object> {
    @Override
    public void apply(Object target) {
        if (target instanceof Project project) {
            project.getPluginManager().apply(CrochetProjectPlugin.class);
        } else if (target instanceof Settings settings) {
            settings.getPluginManager().apply(CrochetSettingsPlugin.class);
        } else {
            throw new IllegalArgumentException("Unsupported target type: " + target.getClass());
        }
    }
}
