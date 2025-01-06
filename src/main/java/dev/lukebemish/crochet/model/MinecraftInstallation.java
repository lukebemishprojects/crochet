package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.ConfigurationUtils;
import dev.lukebemish.crochet.internal.CrochetPlugin;
import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.IdeaModelHandlerPlugin;
import dev.lukebemish.crochet.internal.InheritanceMarker;
import dev.lukebemish.crochet.internal.tasks.TaskGraphExecution;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class MinecraftInstallation implements Named {
    static final String ACCESS_TRANSFORMER_CATEGORY = "accesstransformer";
    static final String INTERFACE_INJECTION_CATEGORY = "interfaceinjection";

    protected static final String CROSS_PROJECT_SHARING_CAPABILITY_GROUP = "dev.lukebemish.crochet.local.shared-";

    private final String name;
    private final Set<SourceSet> sourceSets = new LinkedHashSet<>();
    final CrochetExtension crochetExtension;
    private final Property<InstallationDistribution> distribution;

    private final Property<String> minecraftVersionProperty;

    final Configuration minecraft;
    final Configuration minecraftResources;
    final Configuration minecraftLineMapped;
    final Configuration minecraftDependencies;

    final DependencyScopeConfiguration nonUpgradableDependencies;
    final Configuration nonUpgradableClientCompileDependencies;
    final Configuration nonUpgradableServerCompileDependencies;
    final Configuration nonUpgradableClientRuntimeDependencies;
    final Configuration nonUpgradableServerRuntimeDependencies;

    final ConfigurableFileCollection assetsPropertiesFiles;

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public MinecraftInstallation(String name, CrochetExtension extension) {
        this.name = name;
        this.crochetExtension = extension;

        var project = this.crochetExtension.project;

        this.assetsPropertiesFiles = project.files();

        this.distribution = project.getObjects().property(InstallationDistribution.class);
        this.distribution.finalizeValueOnRead();
        this.distribution.convention(InstallationDistribution.JOINED);

        this.minecraftDependencies = project.getConfigurations().create("crochet"+ StringUtils.capitalize(name)+"MinecraftDependencies");

        // Create early so getMinecraft provider works right
        this.minecraftVersionProperty = project.getObjects().property(String.class);
        this.minecraftVersionProperty.set(minecraftDependencies.getIncoming().getResolutionResult().getRootComponent().map(ConfigurationUtils::extractMinecraftVersion));

        this.nonUpgradableDependencies = project.getConfigurations().dependencyScope("crochet"+StringUtils.capitalize(name)+"NonUpgradableDependencies", config -> config.extendsFrom(minecraftDependencies)).get();
        Action<AttributeContainer> sharedAttributeAction = attributes -> {
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
        };
        this.nonUpgradableClientCompileDependencies = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NonUpgradableClientCompileDependencies", config -> {
            config.extendsFrom(this.nonUpgradableDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                sharedAttributeAction.execute(attributes);
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, InstallationDistribution.CLIENT.neoAttributeValue());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        this.nonUpgradableServerCompileDependencies = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NonUpgradableServerCompileDependencies", config -> {
            config.extendsFrom(this.nonUpgradableDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                sharedAttributeAction.execute(attributes);
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, InstallationDistribution.SERVER.neoAttributeValue());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
            });
        });
        this.nonUpgradableClientRuntimeDependencies = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NonUpgradableClientRuntimeDependencies", config -> {
            config.extendsFrom(this.nonUpgradableDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                sharedAttributeAction.execute(attributes);
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, InstallationDistribution.CLIENT.neoAttributeValue());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });
        this.nonUpgradableServerRuntimeDependencies = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"NonUpgradableServerRuntimeDependencies", config -> {
            config.extendsFrom(this.nonUpgradableDependencies);
            config.setCanBeConsumed(false);
            config.attributes(attributes -> {
                sharedAttributeAction.execute(attributes);
                attributes.attribute(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, InstallationDistribution.SERVER.neoAttributeValue());
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
        });


        this.minecraftResources = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"MinecraftResources");
        this.minecraft = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"Minecraft", config -> {
            config.setCanBeConsumed(false);
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
            config.attributes(attributes -> attributes.attributeProvider(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::neoAttributeValue)));
        });

        this.minecraftLineMapped = project.getConfigurations().create("crochet"+StringUtils.capitalize(name)+"MinecraftLineMapped", config -> {
            config.setCanBeConsumed(false);
            config.extendsFrom(minecraftDependencies);
            config.extendsFrom(minecraftResources);
            config.attributes(attributes -> attributes.attributeProvider(CrochetPlugin.NEO_DISTRIBUTION_ATTRIBUTE, getDistribution().map(InstallationDistribution::neoAttributeValue)));
        });
    }

    public Property<InstallationDistribution> getDistribution() {
        return this.distribution;
    }

    public void client() {
        this.distribution.set(InstallationDistribution.CLIENT);
    }

    public void common() {
        this.distribution.set(InstallationDistribution.COMMON);
    }

    public void joined() {
        this.distribution.set(InstallationDistribution.JOINED);
    }

    public void server() {
        this.distribution.set(InstallationDistribution.SERVER);
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
                    attributes.attribute(CrochetPlugin.CROCHET_DISTRIBUTION_ATTRIBUTE, dist.name().toLowerCase(Locale.ROOT));
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
                attributes.attribute(CrochetPlugin.CROCHET_DISTRIBUTION_ATTRIBUTE, dist.name().toLowerCase(Locale.ROOT));
            }
        };
        project.getConfigurations().named(sourceSet.getTaskName(null, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME), config -> {
            config.extendsFrom(minecraft);
            config.shouldResolveConsistentlyWith(switch (getDistribution().get()) {
                case CLIENT, JOINED -> nonUpgradableClientCompileDependencies;
                case SERVER, COMMON -> nonUpgradableServerCompileDependencies;
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
            case CLIENT, DATA -> nonUpgradableClientRuntimeDependencies;
            case SERVER -> nonUpgradableServerRuntimeDependencies;
        });
    }
}
