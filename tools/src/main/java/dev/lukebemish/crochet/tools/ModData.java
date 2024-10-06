package dev.lukebemish.crochet.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;

class ModData {
    static final String KNOWN_IDY_BSMS = "Fabric-Loom-Known-Indy-BSMS";
    static final String SHOULD_REMAP = "Fabric-Loom-Remap";

    boolean shouldRemap = true;
    MixinRemapType mixinRemapType = MixinRemapType.MIXIN;
    List<String> knownIndyBsms = new ArrayList<>();

    enum MixinRemapType {
        MIXIN,
        STATIC;

        static final String KEY = "Fabric-Loom-Mixin-Remap-Type";
    }

    ModData(Path jarPath) throws IOException {
        try (var jarFile = new JarFile(jarPath.toFile())) {
            var manifest = jarFile.getManifest();
            var mainAttributes = manifest.getMainAttributes();
            var remapValue = mainAttributes.getValue(SHOULD_REMAP);
            var mixinRemapType = mainAttributes.getValue(MixinRemapType.KEY);
            var knownIndyBsmsValue = mainAttributes.getValue(KNOWN_IDY_BSMS);

            if (remapValue != null) {
                shouldRemap = Boolean.parseBoolean(remapValue);
            }

            if (mixinRemapType != null) {
                this.mixinRemapType = MixinRemapType.valueOf(mixinRemapType.toUpperCase(Locale.ROOT));
            }

            if (knownIndyBsmsValue != null) {
                Collections.addAll(knownIndyBsms, knownIndyBsmsValue.split(","));
            }
        }
    }
}
