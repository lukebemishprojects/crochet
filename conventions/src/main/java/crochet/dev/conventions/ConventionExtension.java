package crochet.dev.conventions;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.Settings;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public abstract class ConventionExtension {
    private final Settings settings;

    @Inject
    public ConventionExtension(Settings settings) {
        this.settings = settings;
    }

    public void repositories() {
        repositories("./.local.properties");
    }

    @SuppressWarnings("UnstableApiUsage")
    public void repositories(String path) {
        boolean useLocal;
        if (path != null && Files.exists(Paths.get(path))) {
            Properties props = new Properties();
            try (var inputStream = new FileInputStream(path)) {
                props.load(inputStream);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load properties from " + path, e);
            }
            useLocal = Boolean.parseBoolean(props.getOrDefault("useLocalMavenForTesting", "false").toString());
        } else {
            useLocal = false;
        }

        addRepositories(settings.getPluginManagement().getRepositories(), useLocal);
        addRepositories(settings.getDependencyResolutionManagement().getRepositories(), useLocal);
        settings.getGradle().getLifecycle().beforeProject(project -> {
            addRepositories(project.getRepositories(), useLocal);
        });
    }

    private static void addRepositories(RepositoryHandler repositoryHandler, boolean useLocal) {
        if (useLocal) {
            var repo = repositoryHandler.mavenLocal();
            repositoryHandler.remove(repo);
            repositoryHandler.addFirst(repo);
        }
    }
}
