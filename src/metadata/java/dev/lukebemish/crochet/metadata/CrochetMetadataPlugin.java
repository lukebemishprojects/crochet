package dev.lukebemish.crochet.metadata;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.net.URI;

public abstract class CrochetMetadataPlugin implements Plugin<Object> {
    @Override
    public void apply(Object target) {
        if (target instanceof Project project) {
            repositories(project.getRepositories());
            components(project.getDependencies().getComponents());
            setupAttributesSchema(project);
        } else if (target instanceof Settings settings) {
            repositories(settings.getDependencyResolutionManagement().getRepositories());
            components(settings.getDependencyResolutionManagement().getComponents());
            settings.getGradle().getLifecycle().beforeProject(project -> {
                project.getPluginManager().apply(CrochetMetadataMarker.class);
                setupAttributesSchema(project);
            });
        } else {
            throw new GradleException("This plugin does not support being applied to " + target);
        }
    }

    @Inject
    public CrochetMetadataPlugin() {}

    @Inject
    protected abstract ProviderFactory getProviders();

    private void setupAttributesSchema(Project project) {
    }

    private void components(ComponentMetadataHandler components) {

    }

    private void repositories(RepositoryHandler repositoryHandler) {
        var minecraftLibraries = repositoryHandler.maven(repo -> {
            repo.setName("Minecraft Libraries");
            repo.setUrl(URI.create("https://libraries.minecraft.net/"));
            repo.metadataSources(MavenArtifactRepository.MetadataSources::mavenPom);
            repo.content(MinecraftLibrariesMavenContent::applyContent);
            // TODO: figure out approach to deprecated artifactUrls -- may need specific replacements?
            //repo.artifactUrls(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL);
        });
        repositoryHandler.remove(minecraftLibraries);
        repositoryHandler.addFirst(minecraftLibraries);
    }
}
