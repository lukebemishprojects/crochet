package dev.lukebemish.crochet.buildlogic;

import groovy.json.JsonSlurper;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class ConventionPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings settings) {
        settings.getDependencyResolutionManagement().repositories(repositories -> {
            repositories.exclusiveContent(it -> {
                it.forRepositories(repositories.ivy(ivy -> {
                    ivy.setName("Version Metadata");
                    ivy.setUrl("https://piston-meta.mojang.com/");
                    ivy.patternLayout(layout -> layout.artifact("v1/packages/[module]/[revision].json"));
                    ivy.metadataSources(sources -> {
                        sources.artifact();
                    });
                }));
                it.filter(filter -> filter.includeGroup("dev.lukebemish.crochet.mojang-stubs.version-metadata"));
            });
        });
        settings.getGradle().getLifecycle().beforeProject(project -> {
            if (project.getPath().equals(":")) {
                var versionMetadata = project.getConfigurations().dependencyScope("versionMetadata");
                project.getConfigurations().resolvable("versionMetadataResolved", config -> {
                    config.extendsFrom(versionMetadata);
                });
                try (var resource = ConventionPlugin.class.getResourceAsStream("/dev/lukebemish/crochet/buildlogic/version-manifest-v2.json")) {
                    var json = new JsonSlurper();
                    var manifest = (Map<?, ?>) json.parse(resource);
                    ((List<?>) manifest.get("versions")).forEach(versionData -> {
                        var url = (String) ((Map<?, ?>) versionData).get("url");
                        if (!url.startsWith("https://piston-meta.mojang.com/v1/packages/")) {
                            throw new IllegalStateException(String.format("Unexpected URL '%s'", url));
                        }
                        var rest = url.substring("https://piston-meta.mojang.com/v1/packages/".length());
                        var parts = rest.split("/");
                        if (parts.length != 2) {
                            throw new IllegalStateException(String.format("Unexpected URL '%s'", url));
                        }
                        var dep = project.getDependencyFactory().create(
                            "dev.lukebemish.crochet.mojang-stubs.version-metadata",
                            URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(parts[1].substring(0, parts[1].lastIndexOf('.')), StandardCharsets.UTF_8)
                        );
                        dep.artifact(artifact -> artifact.setExtension("json"));
                        project.getDependencies().add(versionMetadata.getName(), dep);
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
