package dev.lukebemish.crochet.internal.pistonmeta;

import com.google.gson.Gson;

import java.util.List;

public record VersionManifest(Latest latest, List<Version> versions) {
    public static final Gson GSON = new Gson();
    public static final String VERSION_MANIFEST = "mc/game/version_manifest_v2.json";
    public static final String PISTON_META_URL = "https://piston-meta.mojang.com/";

    public record Latest(String release, String snapshot) {}
    public record Version(String id, String type, String url, String sha1) {}
}
