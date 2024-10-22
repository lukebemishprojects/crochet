package dev.lukebemish.crochet.internal;

import dev.lukebemish.crochet.internal.pistonmeta.PistonMetaMetadataRule;
import dev.lukebemish.crochet.internal.pistonmeta.PistonMetaVersionLister;
import dev.lukebemish.crochet.internal.pistonmeta.VersionManifest;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;

import java.net.URI;

public class CrochetRepositoriesPlugin implements Plugin<Object> {
    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void apply(Object target) {
        if (target instanceof Project project) {
            // We allow applying this plugin at the project level if someone wants to use it settings-level but override repositories for a project.
            repositories(project.getRepositories());
            components(project.getDependencies().getComponents());
        } else if (target instanceof Settings settings) {
            repositories(settings.getDependencyResolutionManagement().getRepositories());
            components(settings.getDependencyResolutionManagement().getComponents());
            settings.getGradle().getPlugins().apply(CrochetRepositoriesPlugin.class);
        } else if (!(target instanceof Gradle)) {
            throw new GradleException("This plugin does not support being applied to " + target);
        }
    }

    private static void components(ComponentMetadataHandler components) {
        components.withModule("net.fabricmc:fabric-loader", FabricInstallerRule.class);
        components.withModule("org.quiltmc:quilt-loader", FabricInstallerRule.class);

        components.withModule("dev.lukebemish.crochet:"+PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES, PistonMetaMetadataRule.class);
        components.withModule("dev.lukebemish.crochet:"+PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES_NATIVES, PistonMetaMetadataRule.class);
    }

    private static void repositories(RepositoryHandler repositoryHandler) {
        var minecraftLibraries = repositoryHandler.maven(repo -> {
            repo.setName("Minecraft Libraries");
            repo.setUrl(URI.create("https://libraries.minecraft.net/"));
            repo.metadataSources(MavenArtifactRepository.MetadataSources::mavenPom);
            repo.content(MinecraftLibrariesMavenContent::applyContent);
        });
        repositoryHandler.remove(minecraftLibraries);
        repositoryHandler.addFirst(minecraftLibraries);

        repositoryHandler.ivy(repo -> {
            repo.setName("Piston Meta Minecraft Dependencies");
            repo.setUrl(VersionManifest.PISTON_META_URL);
            repo.patternLayout(layout ->
                layout.artifact("mc/game/version_manifest_v2.json")
            );
            repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
            repo.setComponentVersionsLister(PistonMetaVersionLister.class);
            repo.content(content -> {
                content.includeModule("dev.lukebemish.crochet", PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES);
                content.includeModule("dev.lukebemish.crochet", PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES_NATIVES);
            });
        });

        // Tempted to make this an exclusive repository but decided it wasn't worth it
        var neoMinecraftDependencies = repositoryHandler.maven(repo -> {
            repo.setName("Neoforge Minecraft Dependencies");
            repo.setUrl("https://maven.neoforged.net/mojang-meta/");
            repo.metadataSources(MavenArtifactRepository.MetadataSources::gradleMetadata);
            repo.content(content ->
                content.includeModule("net.neoforged", "minecraft-dependencies")
            );
        });
        repositoryHandler.remove(neoMinecraftDependencies);
        repositoryHandler.addFirst(neoMinecraftDependencies);

        repositoryHandler.maven(repo -> {
            repo.setName("NeoForged Releases");
            repo.setUrl("https://maven.neoforged.net/releases/");
        });

        repositoryHandler.maven(repo -> {
            repo.setName("FabricMC");
            repo.setUrl("https://maven.fabricmc.net/");
        });

        repositoryHandler.mavenCentral();
    }
}
