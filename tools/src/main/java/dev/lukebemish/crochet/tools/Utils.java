package dev.lukebemish.crochet.tools;

import com.google.gson.Gson;
import net.neoforged.srgutils.IMappingFile;
import org.objectweb.asm.commons.Remapper;

final class Utils {
    private Utils() {}

    static final Gson GSON = new Gson();

    static Remapper remapperForFile(IMappingFile mappings) {
        return new Remapper() {
            @Override
            public String mapDesc(String descriptor) {
                return mappings.remapDescriptor(descriptor);
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                var iClass = mappings.getClass(owner);
                if (iClass == null) {
                    return name;
                }
                var iField = iClass.getField(name);
                if (iField == null) {
                    return name;
                }
                return iField.getMapped();
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                var iClass = mappings.getClass(owner);
                if (iClass == null) {
                    return name;
                }
                var iMethod = iClass.getMethod(name, descriptor);
                if (iMethod == null) {
                    return name;
                }
                return iMethod.getMapped();
            }

            @Override
            public String map(String internalName) {
                return mappings.remapClass(internalName);
            }
        };
    }
}
