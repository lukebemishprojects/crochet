package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import dev.lukebemish.crochet.internal.ExtensionHolder;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.InheritanceMarker;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class MinecraftInstallation extends ExtensionHolder implements GeneralizedMinecraftInstallation {
    static final String ACCESS_TRANSFORMER_CATEGORY = "accesstransformer";
    static final String INTERFACE_INJECTION_CATEGORY = "interfaceinjection";

    protected static final String CROSS_PROJECT_SHARING_CAPABILITY_GROUP = "dev.lukebemish.crochet.local.shared-";
    protected static final String CROSS_PROJECT_BUNDLE_CAPABILITY_GROUP = "dev.lukebemish.crochet.local.bundle-";

    private final String name;
    private final Set<SourceSet> sourceSets = new LinkedHashSet<>();
    final CrochetExtension crochetExtension;
    private final Property<InstallationDistribution> distribution;

    private final Property<String> minecraftVersionProperty;

    final DependencyScopeConfiguration minecraft;
    final DependencyScopeConfiguration minecraftResources;
    final DependencyScopeConfiguration minecraftLineMapped;

    final DependencyScopeConfiguration minecraftDependencies;

    final DependencyScopeConfiguration nonUpgradableDependencies;
    final ResolvableConfiguration nonUpgradableClientCompileVersioning;
    final ResolvableConfiguration nonUpgradableServerCompileVersioning;
    final ResolvableConfiguration nonUpgradableClientRuntimeVersioning;
    final ResolvableConfiguration nonUpgradableServerRuntimeVersioning;

    final ConfigurableFileCollection assetsPropertiesFiles;

    @Inject
    public MinecraftInstallation(String name, CrochetExtension extension) {
        super(extension);

        this.name = name;
        this.crochetExtension = extension;

        var project = this.crochetExtension.project;

        this.assetsPropertiesFiles = project.files();

        this.distribution = project.getObjects().property(InstallationDistribution.class);
        this.distribution.finalizeValueOnRead();
        this.distribution.convention(InstallationDistribution.JOINED);

        this.minecraftDependencies = ConfigurationUtils.dependencyScope(this, name, null, "minecraftDependencies", config -> {});
        Configuration minecraftDependenciesVersioning = ConfigurationUtils.resolvableInternal(this, name, "minecraftDependenciesVersioning", config -> {
            config.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, InstallationDistribution.CLIENT.neoAttributeValue());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
            config.extendsFrom(minecraftDependencies);
        });

        // Create early so getMinecraft provider works right
        this.minecraftVersionProperty = project.getObjects().property(String.class);
        this.minecraftVersionProperty.set(minecraftDependenciesVersioning.getIncoming().getResolutionResult().getRootComponent().map(ConfigurationUtils::extractMinecraftVersion));

        this.nonUpgradableDependencies = ConfigurationUtils.dependencyScopeInternal(this, name, "nonUpgradableDependencies", config -> {
            config.extendsFrom(minecraftDependencies);
        });
        Action<AttributeContainer> sharedAttributeAction = attributes -> {
            /*attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));*/
            // TODO: is this necessary? It _shouldn't_ be...
        };
        this.nonUpgradableClientCompileVersioning = ConfigurationUtils.resolvableInternal(this, name, "nonUpgradableClientCompileVersioning", config -> {
            config.extendsFrom(nonUpgradableDependencies);
            config.attributes(sharedAttributeAction);
            config.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, InstallationDistribution.CLIENT.neoAttributeValue());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        this.nonUpgradableServerCompileVersioning = ConfigurationUtils.resolvableInternal(this, name, "nonUpgradableServerCompileVersioning", config -> {
            config.extendsFrom(nonUpgradableDependencies);
            config.attributes(sharedAttributeAction);
            config.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, InstallationDistribution.SERVER.neoAttributeValue());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        this.nonUpgradableClientRuntimeVersioning = ConfigurationUtils.resolvableInternal(this, name, "nonUpgradableClientRuntimeVersioning", config -> {
            config.extendsFrom(nonUpgradableDependencies);
            config.attributes(sharedAttributeAction);
            config.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, InstallationDistribution.CLIENT.neoAttributeValue());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });
        this.nonUpgradableServerRuntimeVersioning = ConfigurationUtils.resolvableInternal(this, name, "nonUpgradableServerRuntimeVersioning", config -> {
            config.extendsFrom(nonUpgradableDependencies);
            config.attributes(sharedAttributeAction);
            config.attributes(attributes -> {
                attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, InstallationDistribution.SERVER.neoAttributeValue());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });

        this.minecraftResources = ConfigurationUtils.dependencyScopeInternal(this, name, "minecraftResources", config -> {});
        this.minecraft = ConfigurationUtils.dependencyScopeInternal(this, name, "minecraft", config -> {
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
        });
        this.minecraftLineMapped = ConfigurationUtils.dependencyScopeInternal(this, name, "minecraftLineMapped", config -> {
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
        });
    }

    @Override
    public Property<InstallationDistribution> getDistribution() {
        return this.distribution;
    }

    @Override
    public String getName() {
        return name;
    }

    public Provider<String> getMinecraft() {
        return this.minecraftVersionProperty;
    }

    public void forFeature(SourceSet sourceSet) {
        if (sourceSets.add(sourceSet)) {
            this.crochetExtension.forSourceSet(this.getName(), sourceSet);
        }
        FeatureUtils.forSourceSetFeature(crochetExtension.project, sourceSet.getName(), context -> {
            forFeatureShared(context);

            Action<AttributeContainer> attributesAction = attributes -> {
                var dist = getDistribution().get();
                if (dist != InstallationDistribution.JOINED) {
                    attributes.attribute(CrochetProjectPlugin.CROCHET_DISTRIBUTION_ATTRIBUTE, dist.name().toLowerCase(Locale.ROOT));
                }
            };
            context.getRuntimeElements().attributes(attributesAction);
            context.getApiElements().attributes(attributesAction);
            crochetExtension.project.getConfigurations().getByName(context.getSourceSet().getCompileClasspathConfigurationName()).attributes(attributesAction);
            crochetExtension.project.getConfigurations().getByName(context.getSourceSet().getRuntimeClasspathConfigurationName()).attributes(attributesAction);
        });
    }

    protected boolean canPublishInjectedInterfaces() {
        return true;
    }

    public void forLocalFeature(SourceSet sourceSet) {
        if (sourceSets.add(sourceSet)) {
            this.crochetExtension.forSourceSet(this.getName(), sourceSet);
        }
        FeatureUtils.forSourceSetFeature(crochetExtension.project, sourceSet.getName(), context -> {
            forFeatureShared(context);
        });
    }

    protected List<String> getInstallationConfigurationNames() {
        return List.of();
    }

    private void forFeatureShared(FeatureUtils.Context context) {
        var project = crochetExtension.project;
        var sourceSet = context.getSourceSet();

        Action<AttributeContainer> attributesAction = attributes -> {
            var dist = getDistribution().get();
            if (dist != InstallationDistribution.JOINED) {
                attributes.attribute(CrochetProjectPlugin.CROCHET_DISTRIBUTION_ATTRIBUTE, dist.name().toLowerCase(Locale.ROOT));
            }
        };
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(minecraft);
            config.shouldResolveConsistentlyWith(switch (getDistribution().get()) {
                case CLIENT, JOINED -> nonUpgradableClientCompileVersioning;
                case SERVER, COMMON -> nonUpgradableServerCompileVersioning;
            });
            config.attributes(attributesAction);
        });

        // Link up inheritance via CrochetFeatureContexts for the injected configurations
        var marker = InheritanceMarker.getOrCreate(project.getObjects(), sourceSet);
        marker.getShouldTakeConfigurationsFrom().configureEach(name -> {
            var otherSourceSet = project.getExtensions().getByType(SourceSetContainer.class).findByName(name);
            var otherInstallation = crochetExtension.findInstallation(otherSourceSet);
            if (otherInstallation != this && otherInstallation != null) {
                var configs = new ArrayList<>(getInstallationConfigurationNames());
                var otherConfigs = new HashSet<>(otherInstallation.getInstallationConfigurationNames());
                configs.retainAll(otherConfigs);
                for (var confName : configs) {
                    var thisConf = project.getConfigurations().getByName(this.name + confName);
                    var otherConf = project.getConfigurations().getByName(otherInstallation.name + confName);
                    thisConf.extendsFrom(otherConf);
                }
            }
        });
        marker.getShouldGiveConfigurationsTo().configureEach(name -> {
            var otherSourceSet = project.getExtensions().getByType(SourceSetContainer.class).findByName(name);
            var otherInstallation = crochetExtension.findInstallation(otherSourceSet);
            if (otherInstallation != this && otherInstallation != null) {
                var configs = new ArrayList<>(getInstallationConfigurationNames());
                var otherConfigs = new HashSet<>(otherInstallation.getInstallationConfigurationNames());
                configs.retainAll(otherConfigs);
                for (var confName : configs) {
                    var thisConf = project.getConfigurations().getByName(this.name + confName);
                    var otherConf = project.getConfigurations().getByName(otherInstallation.name + confName);
                    otherConf.extendsFrom(thisConf);
                }
            }
        });
    }

    void forRun(Run run, RunType runType) {
        if (!runType.allowsDistribution(getDistribution().get())) {
            throw new IllegalArgumentException("Run type "+runType+" is not allowed for distribution "+getDistribution().get());
        }

        run.classpath.shouldResolveConsistentlyWith(switch (runType) {
            case CLIENT, DATA -> nonUpgradableClientRuntimeVersioning;
            case SERVER -> nonUpgradableServerRuntimeVersioning;
        });
    }
}
