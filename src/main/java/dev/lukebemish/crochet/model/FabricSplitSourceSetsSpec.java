package dev.lukebemish.crochet.model;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.FeatureSpec;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class FabricSplitSourceSetsSpec {
    @Inject public FabricSplitSourceSetsSpec() {
        getPublishCrochetFeatures().convention(false);
        joinedSpec = getObjects().newInstance(FabricFeatureSpec.class);
        commonSpec = getObjects().newInstance(FabricFeatureSpec.class);
        clientSpec = getObjects().newInstance(FabricFeatureSpec.class);
        serverSpec = getObjects().newInstance(FabricFeatureSpec.class);
    }

    @Inject protected abstract ObjectFactory getObjects();

    private String joined;
    private String common;
    private String client;
    private String server;

    private final FabricFeatureSpec joinedSpec;
    private final FabricFeatureSpec commonSpec;
    private final FabricFeatureSpec clientSpec;
    private final FabricFeatureSpec serverSpec;

    public abstract Property<String> getBaseFeature();

    /**
     * Whether to publish the generated client/server specific and common features externally. Off by default as
     * currently publishing such features makes it difficult for loom to consume your project.
     */
    public abstract Property<Boolean> getPublishCrochetFeatures();

    private final List<Action<FeatureSpec>> featureActions = new ArrayList<>();
    private final List<Action<FabricInstallation>> installationActions = new ArrayList<>();

    public void featureSpec(Action<FeatureSpec> action) {
        featureActions.add(action);
    }

    public void installation(Action<FabricInstallation> action) {
        installationActions.add(action);
    }

    public void joined(String name) {
        joined = name;
    }

    public void joined(String name, Action<FabricFeatureSpec> action) {
        joined = name;
        action.execute(joinedSpec);
    }

    public void common(String name) {
        common = name;
    }

    public void common(String name, Action<FabricFeatureSpec> action) {
        common = name;
        action.execute(commonSpec);
    }

    public void client(String name) {
        client = name;
    }

    public void client(String name, Action<FabricFeatureSpec> action) {
        client = name;
        action.execute(clientSpec);
    }

    public void server(String name) {
        server = name;
    }

    public void server(String name, Action<FabricFeatureSpec> action) {
        server = name;
        action.execute(serverSpec);
    }

    SplitSourceSet setup(Project project, CrochetExtension extension, String name) {
        if (client == null && server == null) {
            throw new IllegalArgumentException("Split source sets must contain at least client or server specific code!");
        }
        var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        extension.features(features -> {
            Action<FeatureSpec> featureAction = spec -> {
                for (var action : featureActions) {
                    action.execute(spec);
                }
            };
            Action<FeatureSpec> nonJoinedAction = spec -> {
                featureAction.execute(spec);
                if (!getPublishCrochetFeatures().get()) {
                    spec.disablePublication();
                }
            };
            if (common != null) {
                makeFeature(common, project, features, spec -> {
                    spec.capability(project.getGroup().toString(), calculateFeature(project, getBaseFeature().get()), project.getVersion().toString());
                    nonJoinedAction.execute(spec);
                    for (var action : commonSpec.featureActions) {
                        action.execute(spec);
                    }
                });
                features.modify(common, feature -> {
                    feature.feature(getBaseFeature().get());
                });
            }
            if (client != null) {
                makeFeature(client, project, features, spec -> {
                    spec.capability(project.getGroup().toString(), combine(calculateFeature(project, getBaseFeature().get()), "client"), project.getVersion().toString());
                    nonJoinedAction.execute(spec);
                    for (var action : clientSpec.featureActions) {
                        action.execute(spec);
                    }
                });
                features.modify(client, feature -> {
                    feature.feature(combine(getBaseFeature().get(), "client"));
                    if (common != null) {
                        feature.inherit(common);
                    }
                    feature.manifestOriginMarker("Fabric-Loom-Client-Only-Entries");
                });
            }
            if (server != null) {
                makeFeature(server, project, features, spec -> {
                    spec.capability(project.getGroup().toString(), combine(calculateFeature(project, getBaseFeature().get()), "server"), project.getVersion().toString());
                    nonJoinedAction.execute(spec);
                    for (var action : serverSpec.featureActions) {
                        action.execute(spec);
                    }
                });
                features.modify(server, feature -> {
                    feature.feature(combine(getBaseFeature().get(), "server"));
                    if (common != null) {
                        feature.inherit(common);
                    }
                    feature.manifestOriginMarker("Fabric-Loom-Server-Only-Entries");
                });
            }
            if (joined != null) {
                makeFeature(server, project, features, spec -> {
                    spec.capability(project.getGroup().toString(), combine(calculateFeature(project, getBaseFeature().get()), "joined"), project.getVersion().toString());
                    nonJoinedAction.execute(spec);
                    for (var action : joinedSpec.featureActions) {
                        action.execute(spec);
                    }
                });
                features.modify(joined, feature -> {
                    feature.feature(combine(getBaseFeature().get(), "joined"));
                    if (client != null) {
                        feature.inherit(client);
                    }
                    if (server != null) {
                        feature.inherit(server);
                    }
                    feature.feature(getBaseFeature().get());
                });
            }

            // Then installations -- set up afterwards so that classes/resources linking works properly
            // TODO: make that more resilient?
            if (common != null) {
                extension.fabricInstallation("fabric"+StringUtils.capitalize(common)+StringUtils.capitalize(name), installation -> {
                    for (var action : installationActions) {
                        action.execute(installation);
                    }
                    for (var action : commonSpec.installationActions) {
                        action.execute(installation);
                    }
                    installation.common();
                    installation.forFeature(sourceSets.findByName(common), deps -> {
                        for (var action : commonSpec.dependenciesActions) {
                            action.execute(deps);
                        }
                    });
                });
            }
            if (client != null) {
                extension.fabricInstallation("fabric"+StringUtils.capitalize(client)+StringUtils.capitalize(name), installation -> {
                    for (var action : installationActions) {
                        action.execute(installation);
                    }
                    for (var action : clientSpec.installationActions) {
                        action.execute(installation);
                    }
                    installation.client();
                    installation.forFeature(sourceSets.findByName(client), deps -> {
                        for (var action : clientSpec.dependenciesActions) {
                            action.execute(deps);
                        }
                    });
                });
            }
            if (server != null) {
                extension.fabricInstallation("fabric"+StringUtils.capitalize(server)+StringUtils.capitalize(name), installation -> {
                    for (var action : installationActions) {
                        action.execute(installation);
                    }
                    for (var action : serverSpec.installationActions) {
                        action.execute(installation);
                    }
                    installation.server();
                    installation.forFeature(sourceSets.findByName(server), deps -> {
                        for (var action : serverSpec.dependenciesActions) {
                            action.execute(deps);
                        }
                    });
                });
            }
            if (joined != null) {
                extension.fabricInstallation("fabric"+StringUtils.capitalize(joined)+StringUtils.capitalize(name), installation -> {
                    for (var action : installationActions) {
                        action.execute(installation);
                    }
                    for (var action : joinedSpec.installationActions) {
                        action.execute(installation);
                    }
                    installation.joined();
                    installation.forFeature(sourceSets.findByName(joined), deps -> {
                        for (var action : joinedSpec.dependenciesActions) {
                            action.execute(deps);
                        }
                    });
                });
            }
        });
        MinecraftInstallation joinedInstallation = joined == null ? null : extension.findInstallation(sourceSets.findByName(joined));
        MinecraftInstallation commonInstallation = common == null ? null : extension.findInstallation(sourceSets.findByName(common));
        MinecraftInstallation clientInstallation = client == null ? null : extension.findInstallation(sourceSets.findByName(client));
        MinecraftInstallation serverInstallation = server == null ? null : extension.findInstallation(sourceSets.findByName(server));
        return getObjects().newInstance(SplitSourceSet.class, name, joinedInstallation, commonInstallation, clientInstallation, serverInstallation);
    }

    private String calculateFeature(Project project, String feature) {
        if (feature == null || feature.isEmpty()) {
            return project.getName();
        }
        return project.getName() + "-" + feature;
    }

    private static String combine(String prefix, String suffix) {
        if (prefix == null || prefix.isEmpty()) {
            if (suffix == null || suffix.isEmpty()) {
                return "";
            }
            return suffix;
        } else if (suffix == null || suffix.isEmpty()) {
            return prefix;
        } else {
            return prefix + "-" + suffix;
        }
    }

    private static void makeFeature(String name, Project project, CrochetFeaturesContext context, Action<FeatureSpec> action) {
        if ("main".equals(name)) {
            return;
        }
        var sourceSet = project.getExtensions().getByType(SourceSetContainer.class).maybeCreate(name);
        var apiElements = project.getConfigurations().findByName(sourceSet.getApiElementsConfigurationName());
        if (apiElements == null) {
            context.create(name, action);
        }
    }
}
