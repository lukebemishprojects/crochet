package dev.lukebemish.crochet.internal;

import dev.lukebemish.crochet.CrochetProperties;
import dev.lukebemish.crochet.internal.metadata.FabricInstallerRule;
import dev.lukebemish.crochet.internal.metadata.NeoMinecraftDependenciesRule;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.PistonMetaMetadataRule;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.PistonMetaVersionLister;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.ServerDependenciesMetadataRule;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.VersionAsArtifactRule;
import dev.lukebemish.crochet.internal.metadata.pistonmeta.VersionManifest;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.net.URI;

public abstract class CrochetRepositoriesPlugin implements Plugin<Object> {
    public static final String MOJANG_STUBS_GROUP = "dev.lukebemish.crochet.mojang-stubs";

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
            settings.getGradle().getLifecycle().beforeProject(project -> {
                project.getPluginManager().apply(CrochetRepositoriesMarker.class);
            });
        } else {
            throw new GradleException("This plugin does not support being applied to " + target);
        }
    }

    @Inject
    public CrochetRepositoriesPlugin() {}

    @Inject
    protected abstract ProviderFactory getProviders();

    private void components(ComponentMetadataHandler components) {
        components.withModule("net.fabricmc:fabric-loader", FabricInstallerRule.class);
        components.withModule("org.quiltmc:quilt-loader", FabricInstallerRule.class);

        components.withModule(MOJANG_STUBS_GROUP+":"+PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES, PistonMetaMetadataRule.class);
        components.withModule(MOJANG_STUBS_GROUP+":"+PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES_NATIVES, PistonMetaMetadataRule.class);
        components.withModule(MOJANG_STUBS_GROUP+":"+PistonMetaMetadataRule.MINECRAFT, PistonMetaMetadataRule.class);
        components.withModule(MOJANG_STUBS_GROUP+":"+ServerDependenciesMetadataRule.MINECRAFT_SERVER_DEPENDENCIES, ServerDependenciesMetadataRule.class);

        components.withModule(MOJANG_STUBS_GROUP+":"+PistonMetaMetadataRule.MINECRAFT_META_ARTIFACT, VersionAsArtifactRule.class);
        components.withModule(MOJANG_STUBS_GROUP+":"+PistonMetaMetadataRule.MINECRAFT_DATA_ARTIFACT, VersionAsArtifactRule.class);

        components.withModule("net.neoforged:minecraft-dependencies", NeoMinecraftDependenciesRule.class);
    }

    private void repositories(RepositoryHandler repositoryHandler) {
        var minecraftLibraries = repositoryHandler.maven(repo -> {
            repo.setName("Minecraft Libraries");
            repo.setUrl(URI.create("https://libraries.minecraft.net/"));
            repo.metadataSources(MavenArtifactRepository.MetadataSources::mavenPom);
            repo.content(MinecraftLibrariesMavenContent::applyContent);
            repo.artifactUrls(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL);
        });
        repositoryHandler.remove(minecraftLibraries);
        repositoryHandler.addFirst(minecraftLibraries);

        repositoryHandler.ivy(repo -> {
            repo.setName("Piston Meta Minecraft Dependencies");
            repo.setUrl(getProviders().gradleProperty(CrochetProperties.PISTON_META_URL).getOrElse(VersionManifest.PISTON_META_URL));
            var dependencyStubUrl = getProviders().gradleProperty(CrochetProperties.DEPENDENCY_STUB_URL).getOrElse(
                "https://repo1.maven.org/maven2/dev/lukebemish/crochet/metadata-stub/1.0.0/ivy-1.0.0.xml"
            );
            repo.artifactPattern(dependencyStubUrl);
            repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
            repo.setComponentVersionsLister(PistonMetaVersionLister.class);
            repo.content(content -> {
                content.includeModule(MOJANG_STUBS_GROUP, PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES);
                content.includeModule(MOJANG_STUBS_GROUP, PistonMetaMetadataRule.MINECRAFT_DEPENDENCIES_NATIVES);
                content.includeModule(MOJANG_STUBS_GROUP, PistonMetaMetadataRule.MINECRAFT);
            });
        });

        repositoryHandler.ivy(repo -> {
            repo.setName("Piston Meta Minecraft Artifacts");
            repo.setUrl(getProviders().gradleProperty(CrochetProperties.PISTON_META_URL).getOrElse(VersionManifest.PISTON_META_URL));
            repo.patternLayout(layout ->
                layout.artifact("[revision]")
            );
            repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
            repo.content(content -> {
                content.includeModule(MOJANG_STUBS_GROUP, PistonMetaMetadataRule.MINECRAFT_META_ARTIFACT);
            });
        });

        repositoryHandler.ivy(repo -> {
            repo.setName("Piston Data Minecraft Artifacts");
            repo.setUrl(getProviders().gradleProperty(CrochetProperties.PISTON_DATA_URL).getOrElse(VersionManifest.PISTON_DATA_URL));
            repo.patternLayout(layout ->
                layout.artifact("[revision]")
            );
            repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
            repo.content(content -> {
                content.includeModule(MOJANG_STUBS_GROUP, ServerDependenciesMetadataRule.MINECRAFT_SERVER_DEPENDENCIES);
                content.includeModule(MOJANG_STUBS_GROUP, PistonMetaMetadataRule.MINECRAFT_DATA_ARTIFACT);
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
