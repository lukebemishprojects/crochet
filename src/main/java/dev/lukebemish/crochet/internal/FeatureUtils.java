package dev.lukebemish.crochet.internal;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

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
    }

    public static void forSourceSetFeature(Project project, String sourceSetName, Action<Context> action) {
        var sourceSet = project.getExtensions().getByType(SourceSetContainer.class).getByName(sourceSetName);
        MutableBoolean foundRuntimeElements = new MutableBoolean(false);
        MutableBoolean foundApiElements = new MutableBoolean(false);
        MutableBoolean executed = new MutableBoolean(false);
        Mutable<Configuration> foundFirst = new MutableObject<>();
        Action<Configuration> configAction = configuration -> {
            if (configuration.getName().equals(sourceSet.getRuntimeElementsConfigurationName())) {
                foundRuntimeElements.setTrue();
                if (foundFirst.getValue() == null) {
                    foundFirst.setValue(configuration);
                }
            } else if (configuration.getName().equals(sourceSet.getApiElementsConfigurationName())) {
                foundApiElements.setTrue();
                if (foundFirst.getValue() == null) {
                    foundFirst.setValue(configuration);
                }
            }
            if (foundRuntimeElements.booleanValue() && foundApiElements.booleanValue() && !executed.booleanValue()) {
                executed.setTrue();
                action.execute(project.getObjects().newInstance(Context.class, sourceSet, foundFirst.getValue()));
            }
        };
        project.getConfigurations().all(configAction);
        project.getConfigurations().whenObjectAdded(configAction);
    }
}
