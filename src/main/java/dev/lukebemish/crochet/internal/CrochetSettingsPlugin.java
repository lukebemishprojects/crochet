package dev.lukebemish.crochet.internal;

import dev.lukebemish.crochet.model.CrochetSettingsExtension;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

public class CrochetSettingsPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings target) {
        target.getExtensions().create("crochet", CrochetSettingsExtension.class, target);
    }
}
