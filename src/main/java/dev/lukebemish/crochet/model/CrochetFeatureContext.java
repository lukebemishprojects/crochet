package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.FeatureUtils;
import dev.lukebemish.crochet.internal.FileListStringifier;
import dev.lukebemish.crochet.internal.InheritanceMarker;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.language.jvm.tasks.ProcessResources;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class CrochetFeatureContext {
    private final String name;

    @Inject
    public CrochetFeatureContext(String name) {
        this.name = name;
    }

    @Inject
    protected abstract Project getProject();

    public void configure(Action<CrochetFeatureContext> action) {
        action.execute(this);
    }

    /**
     * Ignored with recompile-based inheritance.
     */
    public void manifestOriginMarker(String marker) {
        var thisSourceSet = getProject().getExtensions().getByType(SourceSetContainer.class).getByName(this.name);
        var thisMarker = InheritanceMarker.getOrCreate(getProject().getObjects(), thisSourceSet);
        thisMarker.getSourceToManifestNameMap().put(this.name, marker);
        getProject().getTasks().named(thisSourceSet.getJarTaskName(), Jar.class, task -> {
            var markngString = thisMarker.getSourceToManifestNameMap().get().get(name);
            if (markngString != null) {
                var stringifier = getProject().getObjects().newInstance(FileListStringifier.class);
                stringifier.getDirectories().from(thisSourceSet.getOutput());
                stringifier.getFiles().from(thisSourceSet.getOutput().getAsFileTree());
                task.manifest(manifest -> {
                    manifest.attributes(Map.of(
                        markngString, stringifier
                    ));
                });
            }
        });
    }

    public void inherit(String name, Action<InheritanceContext> action) {
        var inheritance = getProject().getObjects().newInstance(InheritanceContext.class);
        action.execute(inheritance);

        var thisSourceSet = getProject().getExtensions().getByType(SourceSetContainer.class).getByName(this.name);
        var otherSourceSet = getProject().getExtensions().getByType(SourceSetContainer.class).getByName(name);

        var thisMarker = InheritanceMarker.getOrCreate(getProject().getObjects(), thisSourceSet);
        thisMarker.getInheritsFrom().add(name);
        var otherMarker = InheritanceMarker.getOrCreate(getProject().getObjects(), otherSourceSet);
        thisMarker.getSourceToManifestNameMap().putAll(otherMarker.getSourceToManifestNameMap());
        otherMarker.getInheritedBy().add(this.name);

        if (inheritance.getLinkConfigurations().get()) {
            thisMarker.getShouldTakeConfigurationsFrom().add(name);
            otherMarker.getShouldGiveConfigurationsTo().add(this.name);
        }

        BiConsumer<FeatureUtils.Context, FeatureUtils.Context> shared = (thisContext, otherContext) -> {
            for (var otherCapability : otherContext.getApiElements().getOutgoing().getCapabilities()) {
                thisContext.modifyOutgoing(outgoing -> {
                    outgoing.capability(otherCapability);
                });
            }

            var sourcesName = thisContext.getSourceSet().getSourcesJarTaskName();
            var javadocName = thisContext.getSourceSet().getJavadocTaskName();

            getProject().getTasks().withType(Jar.class, task -> {
                if (sourcesName.equals(task.getName())) {
                    task.from(otherContext.getSourceSet().getAllSource());
                }
            });
            getProject().getTasks().withType(Javadoc.class, task -> {
                if (javadocName.equals(task.getName())) {
                    task.source(otherContext.getSourceSet().getAllSource());
                }
            });

            if (inheritance.getRecompile().get()) {
                for (var language : inheritance.getCompileLanguages().get()) {
                    getProject().getTasks().named(thisContext.getSourceSet().getCompileTaskName(language), AbstractCompile.class, task -> {
                        SourceDirectorySet targetSourceDirectorySet = null;
                        if (language.equals("java")) {
                            targetSourceDirectorySet = otherContext.getSourceSet().getJava();
                        } else {
                            var extension = otherContext.getSourceSet().getExtensions().findByName(language);
                            if (!(extension instanceof SourceDirectorySet sourceDirectorySet)) {
                                throw new IllegalArgumentException("Language " + language + " is not supported by source set " + otherContext.getSourceSet().getName());
                            }
                            targetSourceDirectorySet = sourceDirectorySet;
                        }
                        task.source(targetSourceDirectorySet);
                    });
                    getProject().getTasks().named(thisContext.getSourceSet().getProcessResourcesTaskName(), ProcessResources.class, task -> {
                        task.from(otherContext.getSourceSet().getResources());
                    });
                }
            } else {
                getProject().getTasks().named(thisContext.getSourceSet().getJarTaskName(), Jar.class, task -> {
                    task.from(otherContext.getSourceSet().getOutput());
                    var markngString = thisMarker.getSourceToManifestNameMap().get().get(otherContext.getSourceSet().getName());
                    if (markngString != null) {
                        var stringifier = getProject().getObjects().newInstance(FileListStringifier.class);
                        stringifier.getDirectories().from(otherContext.getSourceSet().getOutput());
                        stringifier.getFiles().from(otherContext.getSourceSet().getOutput().getAsFileTree());
                        task.manifest(manifest -> {
                            manifest.attributes(Map.of(
                                markngString, stringifier
                            ));
                        });
                    }
                });
                var thisClasses = thisContext.getRuntimeElements().getOutgoing().getVariants().findByName("classes");
                var otherClasses = otherContext.getRuntimeElements().getOutgoing().getVariants().findByName("classes");
                if (thisClasses != null && otherClasses != null) {
                    thisClasses.getArtifacts().addAllLater(getProject().provider(otherClasses::getArtifacts));
                }

                var thisResources = thisContext.getRuntimeElements().getOutgoing().getVariants().findByName("resources");
                var otherResources = otherContext.getRuntimeElements().getOutgoing().getVariants().findByName("resources");
                if (thisResources != null && otherResources != null) {
                    thisResources.getArtifacts().addAllLater(getProject().provider(otherResources::getArtifacts));
                }
                var thisClassesAndResources = thisContext.getRuntimeElements().getOutgoing().getVariants().findByName("classesAndResources");
                var otherClassesAndResources = otherContext.getRuntimeElements().getOutgoing().getVariants().findByName("classesAndResources");
                if (thisClassesAndResources != null && otherClassesAndResources != null) {
                    thisClassesAndResources.getArtifacts().addAllLater(getProject().provider(otherClassesAndResources::getArtifacts));
                }
            }
        };

        FeatureUtils.forSourceSetFeatures(getProject(), List.of(this.name, name), contexts -> {
            var thisContext = contexts.get(0);
            var otherContext = contexts.get(1);

            if (thisContext.getApiElements().getOutgoing().getCapabilities().isEmpty()) {
                // Add implicit capability
                thisContext.modifyOutgoing(outgoing -> {
                    outgoing.capability(calculateCapability(""));
                });
            }

            if (inheritance.getLinkConfigurations().get()) {
                for (var linkedConfiguration : LINKED_CONFIGURATIONS) {
                    getProject().getConfigurations().getByName(linkedConfiguration.apply(thisContext.getSourceSet())).extendsFrom(
                        getProject().getConfigurations().getByName(linkedConfiguration.apply(otherContext.getSourceSet()))
                    );
                }
            }

            shared.accept(thisContext, otherContext);
        });

        Consumer<SourceSet> inheritedConsumer = sourceSet -> {
            var marker = InheritanceMarker.getOrCreate(getProject().getObjects(), sourceSet);
            marker.getInheritsFrom().configureEach(otherName -> {
                FeatureUtils.forSourceSetFeatures(getProject(), List.of(this.name, otherName), contexts -> {
                    var thisContext = contexts.get(0);
                    var otherContext = contexts.get(1);
                    shared.accept(thisContext, otherContext);
                });
            });
        };
        inheritedConsumer.accept(otherSourceSet);
    }

    public void inherit(String name) {
        inherit(name, it -> {});
    }

    private static final List<Function<SourceSet, String>> LINKED_CONFIGURATIONS = List.of(
        SourceSet::getCompileOnlyConfigurationName,
        SourceSet::getCompileOnlyApiConfigurationName,
        SourceSet::getRuntimeOnlyConfigurationName,
        SourceSet::getImplementationConfigurationName,
        SourceSet::getApiConfigurationName,
        sourceSet -> sourceSet.getTaskName(null, "localRuntime"),
        sourceSet -> sourceSet.getTaskName(null, "localImplementation"),
        SourceSet::getAnnotationProcessorConfigurationName
    );

    private String calculateCapability(String feature) {
        if (feature.isEmpty()) {
            return getProject().getGroup() + ":" + getProject().getName() + ":" + getProject().getVersion();
        }
        return getProject().getGroup() + ":" + getProject().getName() + "-" + feature + ":" + getProject().getVersion();
    }

    public void capabilities(List<Object> capabilities) {
        FeatureUtils.forSourceSetFeature(getProject(), this.name, context -> {
            context.modifyOutgoing(outgoing -> {
                for (var capability : capabilities) {
                    outgoing.capability(capability);
                }
            });
        });
    }

    public void capability(Object capability) {
        capabilities(List.of(capability));
    }

    public void feature(String feature) {
        features(List.of(feature));
    }

    public void features(List<String> features) {
        FeatureUtils.forSourceSetFeature(getProject(), this.name, context -> {
            context.modifyOutgoing(outgoing -> {
                for (var feature : features) {
                    outgoing.capability(calculateCapability(feature));
                }
            });
        });
    }
}
