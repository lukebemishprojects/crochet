package dev.lukebemish.crochet.internal;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class FeatureUtils {
    private FeatureUtils() {}

    public static abstract class Context {
        private final Configuration runtimeElements;
        private final Configuration apiElements;
        private final SourceSet sourceSet;
        private final AdhocComponentWithVariants component;
        private final Configuration foundFirst;

        @Inject
        public abstract Project getProject();

        @Inject
        public Context(SourceSet sourceSet, Configuration foundFirst) {
            this.sourceSet = sourceSet;
            this.runtimeElements = getProject().getConfigurations().getByName(sourceSet.getRuntimeElementsConfigurationName());
            this.apiElements = getProject().getConfigurations().getByName(sourceSet.getApiElementsConfigurationName());
            this.foundFirst = foundFirst;
            this.component = (AdhocComponentWithVariants) getProject().getComponents().getByName("java");
        }

        public void withCapabilities(Configuration variant) {
            foundFirst.getOutgoing().getCapabilities().forEach(capability ->
                variant.getOutgoing().capability(capability)
            );
        }

        public void publishWithVariants(Configuration variant) {
            withCapabilities(variant);
            component.addVariantsFromConfiguration(variant, ConfigurationVariantDetails::mapToOptional);
        }

        public AdhocComponentWithVariants getComponent() {
            return component;
        }

        public SourceSet getSourceSet() {
            return sourceSet;
        }

        public Configuration getRuntimeElements() {
            return runtimeElements;
        }

        public Configuration getApiElements() {
            return apiElements;
        }

        public void modifyOutgoing(Action<ConfigurationPublications> action) {
            action.execute(apiElements.getOutgoing());
            action.execute(runtimeElements.getOutgoing());
            var sourcesName = sourceSet.getSourcesElementsConfigurationName();
            var javadocName = sourceSet.getJavadocElementsConfigurationName();
            getProject().getConfigurations().configureEach(configuration -> {
                if (configuration.getName().equals(sourcesName) || configuration.getName().equals(javadocName)) {
                    action.execute(configuration.getOutgoing());
                }
            });
        }
    }

    public static void forSourceSetFeatures(Project project, List<String> sourceSetNames, Action<List<Context>> action) {
        AtomicInteger counter = new AtomicInteger(sourceSetNames.size());
        Context[] contexts = new Context[sourceSetNames.size()];
        for (int i = 0; i < sourceSetNames.size(); i++) {
            String sourceSetName = sourceSetNames.get(i);
            int finalI = i;
            forSourceSetFeature(project, sourceSetName, context -> {
                contexts[finalI] = context;
                if (counter.decrementAndGet() == 0) {
                    action.execute(List.of(contexts));
                }
            });
        }
    }

    public static void forSourceSetFeature(Project project, String sourceSetName, Action<Context> action) {
        var sourceSet = project.getExtensions().getByType(SourceSetContainer.class).getByName(sourceSetName);
        MutableBoolean foundRuntimeElements = new MutableBoolean(false);
        MutableBoolean foundApiElements = new MutableBoolean(false);
        MutableBoolean executed = new MutableBoolean(false);
        MutableBoolean foundApiClasses = new MutableBoolean(false);
        MutableBoolean foundRuntimeClasses = new MutableBoolean(false);
        MutableBoolean foundRuntimeResources = new MutableBoolean(false);
        Mutable<Configuration> foundFirst = new MutableObject<>();
        Runnable checkedAction = () -> {
            if (foundRuntimeElements.booleanValue() && foundRuntimeClasses.booleanValue() && foundRuntimeResources.booleanValue() &&
                foundApiElements.booleanValue() && foundApiClasses.booleanValue() &&
                !executed.booleanValue()) {
                executed.setTrue();
                action.execute(project.getObjects().newInstance(Context.class, sourceSet, foundFirst.getValue()));
            }
        };
        Action<Configuration> configAction = configuration -> {
            if (configuration.getName().equals(sourceSet.getRuntimeElementsConfigurationName())) {
                foundRuntimeElements.setTrue();
                if (foundFirst.getValue() == null) {
                    foundFirst.setValue(configuration);
                }
                Action<ConfigurationVariant> variantsAction = variant -> {
                    var name = variant.getName();
                    if (name.equals("classes")) {
                        foundRuntimeClasses.setTrue();
                        checkedAction.run();
                    } else if (name.equals("resources")) {
                        foundRuntimeResources.setTrue();
                        checkedAction.run();
                    }
                };
                configuration.getOutgoing().getVariants().all(variantsAction);
                configuration.getOutgoing().getVariants().whenObjectAdded(variantsAction);
                checkedAction.run();
            } else if (configuration.getName().equals(sourceSet.getApiElementsConfigurationName())) {
                foundApiElements.setTrue();
                if (foundFirst.getValue() == null) {
                    foundFirst.setValue(configuration);
                }
                Action<ConfigurationVariant> variantsAction = variant -> {
                    var name = variant.getName();
                    if (name.equals("classes")) {
                        foundApiClasses.setTrue();
                        checkedAction.run();
                    }
                };
                configuration.getOutgoing().getVariants().all(variantsAction);
                configuration.getOutgoing().getVariants().whenObjectAdded(variantsAction);
                checkedAction.run();
            }
        };
        // Ideally would be lazy -- we'll change it when we can
        project.getConfigurations().all(configAction);
        project.getConfigurations().whenObjectAdded(configAction);
    }
}
