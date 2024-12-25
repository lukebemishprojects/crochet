package dev.lukebemish.crochet.internal;

import org.gradle.api.Plugin;

public class CrochetRepositoriesMarker implements Plugin<Object> {
    @Override
    public void apply(Object target) {
        // Application does nothing, merely serving as a marker the plugin can check as to whether the repositories
        // plugin has been applied at the settings level.
    }
}
