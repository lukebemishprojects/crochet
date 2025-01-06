package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.CrochetProperties;
import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.CrochetRepositoriesPlugin;
import org.gradle.api.Action;
import org.gradle.api.initialization.Settings;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class SettingsMinecraftInstallation<T extends SettingsMinecraftInstallation<T, R, D>, R extends LocalMinecraftInstallation, D extends AbstractLocalInstallationDependencies<D>> implements GeneralizedMinecraftInstallation {
    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public SettingsMinecraftInstallation(String name, Settings settings) {
        settings.getGradle().getLifecycle().beforeProject(project -> {
            if (project.getPath().equals(project.getIsolated().getRootProject().getPath())) {
                project.getPluginManager().apply(CrochetPlugin.class);
                var crochet = project.getExtensions().getByType(CrochetExtension.class);
                makeInstallation(name, crochet, this::configureInstallation);
            }
            project.getPluginManager().withPlugin("dev.lukebemish.crochet", plugin -> {
                var crochet = project.getExtensions().getByType(CrochetExtension.class);
                crochet.addSharedInstallation(name);
            });
        });
        getDistribution().convention(InstallationDistribution.JOINED);
    }

    protected abstract void makeInstallation(String name, CrochetExtension extension, Action<? super R> action);

    protected void configureInstallation(R installation) {
        installation.getDistribution().set(getDistribution());
        installation.share(getName());
    }

    protected final List<Action<? super D>> dependencyActions = new ArrayList<>();

    public void dependencies(Action<? super D> action) {
        dependencyActions.add(action);
    }

    public abstract static class AbstractVanilla<T extends SettingsMinecraftInstallation<T, R, D>, R extends AbstractVanillaInstallation, D extends AbstractVanillaInstallationDependencies<D>> extends SettingsMinecraftInstallation<T, R, D> {
        @Inject
        public AbstractVanilla(String name, Settings settings) {
            super(name, settings);
            getUseStubBackedMinecraftDependencies().convention(
                settings.getProviders().gradleProperty(CrochetProperties.USE_STUB_GENERATED_MINECRAFT_DEPENDENCIES).map(Boolean::parseBoolean).orElse(false).get()
            );
        }

        @ApiStatus.Experimental
        public abstract Property<Boolean> getUseStubBackedMinecraftDependencies();

        public void setMinecraft(String string) {
            dependencies(dependencies -> {
                setMinecraftOnDependencies(dependencies.installation.crochetExtension.project.provider(() -> string), dependencies);
            });
        }

        public void setMinecraft(Provider<String> string) {
            dependencies(dependencies -> {
                setMinecraftOnDependencies(string, dependencies);
            });
        }

        @SuppressWarnings("UnstableApiUsage")
        private void setMinecraftOnDependencies(Provider<String> string, D dependencies) {
            var installation = (AbstractVanillaInstallation) dependencies.installation;
            dependencies.getMinecraftDependencies().add(
                dependencies.installation.crochetExtension.project.provider(() ->
                    dependencies.module((installation.getUseStubBackedMinecraftDependencies().get() ? CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP + ":minecraft-dependencies" : "net.neoforged:minecraft-dependencies")+":"+ string.get())
                )
            );
        }

        @Override
        protected void configureInstallation(R installation) {
            super.configureInstallation(installation);
            installation.getUseStubBackedMinecraftDependencies().set(getUseStubBackedMinecraftDependencies());
        }
    }

    public abstract static class Vanilla extends AbstractVanilla<Vanilla, VanillaInstallation, VanillaInstallationDependencies> {
        @Inject
        public Vanilla(String name, Settings settings) {
            super(name, settings);
        }

        @Override
        protected void makeInstallation(String name, CrochetExtension extension, Action<? super VanillaInstallation> action) {
            extension.vanillaInstallation(name, action);
        }

        @Override
        protected void configureInstallation(VanillaInstallation installation) {
            super.configureInstallation(installation);
            for (var action : this.dependencyActions) {
                action.execute(installation.getDependencies());
            }
        }
    }

    public abstract static class Fabric extends AbstractVanilla<Fabric, FabricInstallation, FabricInstallationDependencies> {
        @Inject
        public Fabric(String name, Settings settings) {
            super(name, settings);
        }

        @Override
        protected void makeInstallation(String name, CrochetExtension extension, Action<? super FabricInstallation> action) {
            extension.fabricInstallation(name, action);
        }

        @Override
        protected void configureInstallation(FabricInstallation installation) {
            super.configureInstallation(installation);
            for (var action : this.dependencyActions) {
                action.execute(installation.getDependencies());
            }
        }
    }

    public abstract static class NeoForm extends SettingsMinecraftInstallation<NeoForm, NeoFormInstallation, NeoFormInstallationDependencies> {
        @Inject
        public NeoForm(String name, Settings settings) {
            super(name, settings);
        }

        @Override
        protected void makeInstallation(String name, CrochetExtension extension, Action<? super NeoFormInstallation> action) {
            extension.neoFormInstallation(name, action);
        }

        @Override
        protected void configureInstallation(NeoFormInstallation installation) {
            super.configureInstallation(installation);
            for (var action : this.dependencyActions) {
                action.execute(installation.getDependencies());
            }
        }
    }
}
