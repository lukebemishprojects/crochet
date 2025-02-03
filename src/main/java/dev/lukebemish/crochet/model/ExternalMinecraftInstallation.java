package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class ExternalMinecraftInstallation extends MinecraftInstallation {
    final DependencyScopeConfiguration assetsProperties;
    final ResolvableConfiguration assetsPropertiesPath;

    @Inject
    public ExternalMinecraftInstallation(String name, CrochetExtension extension) {
        super(name, extension);

        assetsProperties = ConfigurationUtils.dependencyScopeInternal(this, name, "assetsProperties", c -> {});
        assetsPropertiesPath = ConfigurationUtils.resolvableInternal(this, name, "assetsPropertiesPath", c -> {
            c.extendsFrom(assetsProperties);
        });
        this.assetsPropertiesFiles.from(assetsPropertiesPath);
    }

    boolean linked = false;

    protected abstract String sharingInstallationTypeTag();

    protected Map<String, Configuration> getConfigurationsToLink() {
        return Map.of(
            "assets-properties", assetsProperties,
            "minecraft", minecraft,
            "minecraft-dependencies", minecraftDependencies,
            "minecraft-resources", minecraftResources,
            "minecraft-line-mapped", minecraftLineMapped,
            "non-upgradable", nonUpgradableDependencies
        );
    }

    @SuppressWarnings("UnstableApiUsage")
    public void consume(SharedInstallation sharedInstallation) {
        consume(crochetExtension.project.getIsolated().getRootProject().getPath(), sharedInstallation.getName()+"Shared");
    }

    public void consume(NamedDomainObjectProvider<SharedInstallation> sharedInstallation) {
        consume(sharedInstallation.get());
    }

    public void consume(String project, String name) {
        if (linked) {
            throw new IllegalStateException("External Minecraft installation already linked");
        }
        linked = true;
        var dependencies = this.crochetExtension.project.getDependencies();
        Function<String, Action<ModuleDependency>> capabilitiesFunction = tag -> dependency -> {
            dependency.capabilities(capabilities -> {
                var group = CROSS_PROJECT_SHARING_CAPABILITY_GROUP + sharingInstallationTypeTag();
                var module = tag + "-" + name;
                capabilities.requireCapability(group + ":" + module);
            });
            dependency.attributes(attribute -> {
                attribute.attributeProvider(CrochetProjectPlugin.LOCAL_DISTRIBUTION_ATTRIBUTE, getDistribution().map(it -> it.name().toLowerCase(Locale.ROOT)));
            });
        };
        Supplier<Provider<Dependency>> projectDependencyProvider = () -> crochetExtension.project.provider(() -> dependencies.project(Map.of("path", project)));
        for (var entry : getConfigurationsToLink().entrySet()) {
            var configuration = entry.getValue();
            configuration.getAttributes().attributeProvider(CrochetProjectPlugin.LOCAL_DISTRIBUTION_ATTRIBUTE, getDistribution().map(it -> it.name().toLowerCase(Locale.ROOT)));
            var tag = entry.getKey();
            dependencies.addProvider(
                configuration.getName(),
                projectDependencyProvider.get(),
                capabilitiesFunction.apply(tag)
            );
        }
    }
}
