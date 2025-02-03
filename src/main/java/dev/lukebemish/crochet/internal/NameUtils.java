package dev.lukebemish.crochet.internal;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public final class NameUtils {
    private NameUtils() {}

    public static String name(String parent, @Nullable String prefix, @Nullable String suffix) {
        return StringUtils.uncapitalize(
            (prefix != null ? StringUtils.capitalize(prefix) : "") +
                (!"main".equals(parent) ? StringUtils.capitalize(parent) : "") +
                (suffix != null ? StringUtils.capitalize(suffix) : "")
        );
    }

    public static String internal(String parent, @Nullable String suffix) {
        return "_crochet"+ StringUtils.capitalize(parent) + (suffix != null ? StringUtils.capitalize(suffix) : "");
    }
}
