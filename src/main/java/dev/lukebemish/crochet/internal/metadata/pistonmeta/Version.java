package dev.lukebemish.crochet.internal.metadata.pistonmeta;

import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Version(JavaVersion javaVersion, List<Library> libraries, Map<String, Download> downloads) {
    public record Download(String url) {}

    public record LibraryDownloads(@Nullable Artifact artifact) {}

    public record Artifact(String path) {}

    public record JavaVersion(String component, int majorVersion) {}

    public record Library(String name, List<Rule> rules, @Nullable Map<String, String> natives, LibraryDownloads downloads) {}

    public record Rule(Action action, Map<String, Boolean> features, Rule.@Nullable OsDetails os) {
        public Rule {
            Objects.requireNonNull(action);
            if (features == null) {
                features = Map.of();
            }
        }

        public enum Action {
            @SerializedName("allow") ALLOW,
            @SerializedName("disallow") DISALLOW
        }

        public record OsDetails(@Nullable String name, @Nullable String version, @Nullable String arch) {}
    }
}
