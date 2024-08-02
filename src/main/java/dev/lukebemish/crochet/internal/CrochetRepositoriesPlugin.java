package dev.lukebemish.crochet.internal;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
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
        } else if (target instanceof Settings settings) {
            repositories(settings.getDependencyResolutionManagement().getRepositories());
            settings.getGradle().getPlugins().apply(getClass());
        } else if (!(target instanceof Gradle)) {
            throw new GradleException("This plugin does not support being applied to " + target);
        }
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
