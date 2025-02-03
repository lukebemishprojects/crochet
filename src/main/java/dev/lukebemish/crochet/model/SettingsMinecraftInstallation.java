package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.CrochetProperties;
import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.CrochetRepositoriesPlugin;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.initialization.Settings;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class SettingsMinecraftInstallation<R extends LocalMinecraftInstallation, D extends AbstractLocalInstallationDependencies<D>> implements GeneralizedMinecraftInstallation {
    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public SettingsMinecraftInstallation(String name, Settings settings) {
        settings.getGradle().getLifecycle().beforeProject(project -> {
            if (project.getPath().equals(project.getIsolated().getRootProject().getPath())) {
                project.getPluginManager().apply(CrochetPlugin.class);
                var crochet = project.getExtensions().getByType(CrochetExtension.class);
                makeInstallation(crochet, this::configureInstallation);
            }
            project.getPluginManager().withPlugin("dev.lukebemish.crochet", plugin -> {
                var crochet = project.getExtensions().getByType(CrochetExtension.class);
                crochet.addSharedInstallation(name);
                generalSetup(crochet);
            });
        });
        getDistribution().convention(InstallationDistribution.JOINED);
    }

    protected abstract void makeInstallation(CrochetExtension extension, Action<? super R> action);

    protected void generalSetup(CrochetExtension crochet) {}

    protected void configureInstallation(R installation) {
        installation.getDistribution().set(getDistribution());
        installation.share(getInstallationName());
    }

    protected final List<Action<? super D>> dependencyActions = new ArrayList<>();

    protected String getInstallationName() {
        return getName()+"Shared";
    }

    public void dependencies(Action<? super D> action) {
        dependencyActions.add(action);
    }

    public abstract static class AbstractVanilla<T extends SettingsMinecraftInstallation<R, D>, R extends AbstractVanillaInstallation, D extends AbstractVanillaInstallationDependencies<D>> extends SettingsMinecraftInstallation<R, D> {
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

        public void setMinecraft(VersionConstraint version) {
            dependencies(dependencies -> {
                setMinecraftOnDependencies(dependencies.installation.crochetExtension.project.provider(() -> version), dependencies);
            });
        }

        /**
         * Sets the Minecraft version to use for this installation; the version can be a string or a {@link VersionConstraint}.
         */
        public void setMinecraft(Provider<?> string) {
            dependencies(dependencies -> {
                setMinecraftOnDependencies(string, dependencies);
            });
        }

        @SuppressWarnings("UnstableApiUsage")
        private void setMinecraftOnDependencies(Provider<?> provider, D dependencies) {
            var installation = (AbstractVanillaInstallation) dependencies.installation;
            dependencies.getMinecraftDependencies().add(
                dependencies.installation.crochetExtension.project.provider(() ->
                    dependencies.module((installation.getUseStubBackedMinecraftDependencies().get() ? CrochetRepositoriesPlugin.MOJANG_STUBS_GROUP + ":minecraft-dependencies" : "net.neoforged:minecraft-dependencies"))
                ), dep -> {
                    var value = provider.get();
                    if (value instanceof VersionConstraint version) {
                        dep.version(v -> {
                            if (!version.getPreferredVersion().isEmpty()) v.prefer(version.getPreferredVersion());
                            if (!version.getRejectedVersions().isEmpty()) v.reject(version.getRejectedVersions().toArray(String[]::new));
                            if (version.getBranch() != null) v.setBranch(version.getBranch());
                            if (!version.getStrictVersion().isEmpty()) v.strictly(version.getStrictVersion());
                            if (!version.getRequiredVersion().isEmpty()) v.require(version.getRequiredVersion());
                        });
                    } else if (value instanceof String string) {
                        dep.version(v -> v.require(string));
                    } else {
                        throw new IllegalArgumentException("Unsupported type for minecraft version: " + value.getClass());
                    }
                }
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
        protected void makeInstallation(CrochetExtension extension, Action<? super VanillaInstallation> action) {
            extension.vanillaInstallation(this.getInstallationName(), action);
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

        private final List<Action<FabricRemapDependencies>> bundleAction = new ArrayList<>();

        @Override
        protected void generalSetup(CrochetExtension crochet) {
            super.generalSetup(crochet);
            var bundle = makeDependencyBundle(this.getInstallationName(), crochet.project);
            crochet.addBundle(bundle);
        }

        @Override
        protected void makeInstallation(CrochetExtension extension, Action<? super FabricInstallation> action) {
            extension.fabricInstallation(this.getInstallationName(), action);
        }

        private FabricDependencyBundle makeDependencyBundle(String name, Project project) {
            return project.getObjects().newInstance(FabricDependencyBundle.class, name, (Action<FabricRemapDependencies>) dependencies -> {
                for (var action : bundleAction) {
                    action.execute(dependencies);
                }
            });
        }

        @Override
        protected void configureInstallation(FabricInstallation installation) {
            super.configureInstallation(installation);
            for (var action : this.dependencyActions) {
                action.execute(installation.getDependencies());
            }
            var bundle = makeDependencyBundle(installation.getName(), installation.project);
            installation.makeBundle(bundle);
        }

        public void bundle(Action<FabricRemapDependencies> action) {
            bundleAction.add(action);
        }
    }

    public abstract static class NeoForm extends SettingsMinecraftInstallation<NeoFormInstallation, NeoFormInstallationDependencies> {
        @Inject
        public NeoForm(String name, Settings settings) {
            super(name, settings);
        }

        @Override
        protected void makeInstallation(CrochetExtension extension, Action<? super NeoFormInstallation> action) {
            extension.neoFormInstallation(this.getInstallationName(), action);
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
