package dev.lukebemish.crochet.internal;

import dev.lukebemish.crochet.CrochetProperties;
import dev.lukebemish.crochet.model.CrochetExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;

public class CrochetProjectPlugin implements Plugin<Project> {
    public static final String TASK_GRAPH_RUNNER_CONFIGURATION_NAME = "_crochetTaskGraphRunnerClasspath";
    public static final String TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME = "_crochetTaskGraphRunnerToolsClasspath";
    public static final String DEV_LAUNCH_CONFIGURATION_NAME = "_crochetDevLaunch";
    public static final String TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME = "_crochetTerminalConsoleAppender";

    public static final String VERSION = CrochetProjectPlugin.class.getPackage().getImplementationVersion();

    public static final Attribute<String> NEO_DISTRIBUTION_ATTRIBUTE = Attribute.of("net.neoforged.distribution", String.class);
    public static final Attribute<String> NEO_OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", String.class);
    public static final Attribute<String> CROCHET_DISTRIBUTION_ATTRIBUTE = Attribute.of("dev.lukebemish.crochet.distribution", String.class);
    public static final Attribute<String> LOCAL_DISTRIBUTION_ATTRIBUTE = Attribute.of("dev.lukebemish.crochet.local.distribution", String.class);
    // This attribute SHOULD NOT be published -- it is for use only in internal pre-remapping-collecting setups
    public static final Attribute<String> CROCHET_REMAP_TYPE_ATTRIBUTE = Attribute.of("dev.lukebemish.crochet.remap", String.class);
    // Dependencies on things that'll need to be remapped when it's all said and done
    public static final String CROCHET_REMAP_TYPE_REMAP = "to-remap";
    // Dependencies on other projects non-remapped components
    public static final String CROCHET_REMAP_TYPE_NON_REMAP = "not-to-remap";

    @Override
    public void apply(Project project) {
        if (project.getProviders().gradleProperty(CrochetProperties.ADD_LIKELY_REPOSITORIES).map(Boolean::parseBoolean).orElse(true).get()) {
            if (!project.getPlugins().hasPlugin(CrochetRepositoriesMarker.class)) {
                project.getPluginManager().apply(CrochetRepositoriesPlugin.class);
            } else {
                project.getLogger().debug("Skipping application of crochet repositories as it was applied at the settings level; you may still apply the plugin manually");
            }
        }

        project.getPluginManager().apply("java-library");

        var objects = project.getObjects();

        project.getExtensions().create("crochet.internal.mappingsConfigurationContainer", MappingsConfigurationCounter.class);

        // TaskGraphRunner
        var taskGraphRunnerDependencies = ConfigurationUtils.dependencyScopeInternal(project, "", "TaskGraphRunner", config -> {});
        var taskGraphRunnerClasspath = ConfigurationUtils.resolvableInternal(project, "", "TaskGraphRunnerClasspath", config -> {
            config.attributes(attributes -> {
                // TaskGraphRunner runs on 21 in general
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21);
                // Prefer shadowed jar
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
            });
            config.extendsFrom(taskGraphRunnerDependencies);
        });
        if (!TASK_GRAPH_RUNNER_CONFIGURATION_NAME.equals(taskGraphRunnerClasspath.getName())) {
            throw new IllegalStateException("TaskGraphRunnerClasspath configuration name is not as expected: " + taskGraphRunnerClasspath.getName() + ", expected "+TASK_GRAPH_RUNNER_CONFIGURATION_NAME);
        }
        project.getDependencies().add(taskGraphRunnerDependencies.getName(), "dev.lukebemish:taskgraphrunner:" + Versions.TASK_GRAPH_RUNNER);

        var taskGraphRunnerToolsDependencies = ConfigurationUtils.dependencyScopeInternal(project, "", "TaskGraphRunnerTools", config -> {});
        var taskGraphRunnerToolsClasspath = ConfigurationUtils.resolvableInternal(project, "", "TaskGraphRunnerToolsClasspath", config -> {
            config.attributes(attributes -> {
                // TaskGraphRunner runs on 21 in general
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21);
            });
            config.extendsFrom(taskGraphRunnerToolsDependencies);
        });
        if (!TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME.equals(taskGraphRunnerToolsClasspath.getName())) {
            throw new IllegalStateException("TaskGraphRunnerToolsClasspath configuration name is not as expected: " + taskGraphRunnerToolsClasspath.getName() + ", expected "+TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME);
        }

        ((ModuleDependency) project.getDependencies().add(taskGraphRunnerToolsDependencies.getName(), "dev.lukebemish:taskgraphrunner:" + Versions.TASK_GRAPH_RUNNER)).capabilities(capabilities -> {
            capabilities.requireCapability("dev.lukebemish:taskgraphrunner-external-tools");
        });
        ((ModuleDependency) project.getDependencies().add(taskGraphRunnerToolsDependencies.getName(), "dev.lukebemish.crochet:tools:" + VERSION)).attributes(attributes -> {
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
        });
        ((ModuleDependency) project.getDependencies().add(taskGraphRunnerToolsDependencies.getName(), "dev.lukebemish:christen:" + Versions.CHRISTEN)).attributes(attributes -> {
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
        });


        // runs
        var devLaunchConfiguration = ConfigurationUtils.dependencyScopeInternal(project, "", "DevLaunch", config -> {});
        if (!DEV_LAUNCH_CONFIGURATION_NAME.equals(devLaunchConfiguration.getName())) {
            throw new IllegalStateException("DevLaunch configuration name is not as expected: " + devLaunchConfiguration.getName() + ", expected "+DEV_LAUNCH_CONFIGURATION_NAME);
        }
        project.getDependencies().add(devLaunchConfiguration.getName(), "net.neoforged:DevLaunch:" + Versions.DEV_LAUNCH);

        var terminalConsoleAppenderConfiguration = ConfigurationUtils.dependencyScopeInternal(project, "", "TerminalConsoleAppender", config -> {});
        if (!TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME.equals(terminalConsoleAppenderConfiguration.getName())) {
            throw new IllegalStateException("TerminalConsoleAppender configuration name is not as expected: " + terminalConsoleAppenderConfiguration.getName() + ", expected "+TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME);
        }
        project.getDependencies().add(terminalConsoleAppenderConfiguration.getName(), "net.minecrell:terminalconsoleappender:" + Versions.TERMINAL_CONSOLE_APPENDER);

        // configurations
        setupConventionalConfigurations(project);

        // Add more conventional stuff -- classesAndResources variants
        project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
            FeatureUtils.forSourceSetFeature(project, sourceSet.getName(), context -> {
                var classes = context.getRuntimeElements().getOutgoing().getVariants().findByName("classes");
                var resources = context.getRuntimeElements().getOutgoing().getVariants().findByName("resources");
                if (classes != null && resources != null) {
                    context.getRuntimeElements().getOutgoing().getVariants().register("classesAndResources", variant -> {
                        ConfigurationUtils.copyAttributes(classes.getAttributes(), variant.getAttributes(), project.getProviders());
                        ConfigurationUtils.copyAttributes(resources.getAttributes(), variant.getAttributes(), project.getProviders());
                        variant.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.CLASSES_AND_RESOURCES));
                        variant.getArtifacts().addAllLater(project.provider(classes::getArtifacts));
                        variant.getArtifacts().addAllLater(project.provider(resources::getArtifacts));
                    });
                }
            });
        });

        var extension = project.getExtensions().create("crochet", CrochetExtension.class, project);

        project.getGradle().getSharedServices().registerIfAbsent("taskGraphRunnerDaemon", TaskGraphRunnerService.class, spec -> {
            spec.getParameters().getHideStacktrace().convention(project.getGradle().getStartParameter().getShowStacktrace() == ShowStacktrace.INTERNAL_EXCEPTIONS);
            spec.getParameters().getLogLevel().convention(project.getProviders().gradleProperty(CrochetProperties.TASKGRAPHRUNNER_LOG_LEVEL).orElse("INFO"));
            spec.getParameters().getRemoveUnusedAssetsAfterDays().convention(project.getProviders().gradleProperty(CrochetProperties.TASKGRAPHRUNNER_REMOVE_ASSET_DURATION).map(Integer::parseInt).orElse(30));
            spec.getParameters().getRemoveUnusedOutputsAfterDays().convention(project.getProviders().gradleProperty(CrochetProperties.TASKGRAPHRUNNER_REMOVE_OUTPUT_DURATION).map(Integer::parseInt).orElse(30));
            spec.getParameters().getRemoveUnusedLocksAfterDays().convention(project.getProviders().gradleProperty(CrochetProperties.TASKGRAPHRUNNER_REMOVE_LOCK_DURATION).map(Integer::parseInt).orElse(1));
        });

        applyDisambiguationRules(project);
    }

    private static void setupConventionalConfigurations(Project project) {
        var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.configureEach(sourceSet -> {
            var compileClasspath = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
            var runtimeClasspath = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());

            var localRuntime = ConfigurationUtils.dependencyScope(project, sourceSet.getName(), null, "localRuntime", c -> {});
            runtimeClasspath.extendsFrom(localRuntime);

            // TODO: make sure that local run classpaths actually pull in the source set's runtime classpath.

            var localImplementation = ConfigurationUtils.dependencyScope(project, sourceSet.getName(), null, "localImplementation", c -> {});
            compileClasspath.extendsFrom(localImplementation);
            runtimeClasspath.extendsFrom(localImplementation);
        });
    }

    private static void applyDisambiguationRules(Project project) {
        project.getDependencies().attributesSchema(attributesSchema -> {
            attributesSchema.attribute(NEO_DISTRIBUTION_ATTRIBUTE).getDisambiguationRules().add(NeoDistributionDisambiguationRule.class);
            attributesSchema.attribute(CROCHET_REMAP_TYPE_ATTRIBUTE);


            String osName = System.getProperty("os.name").toLowerCase();
            String os;
            if (osName.startsWith("windows")) {
                os = "windows";
            } else if (osName.startsWith("linux")) {
                os = "linux";
            } else if (osName.startsWith("mac")) {
                os = "mac";
            } else {
                throw new IllegalStateException("Unsupported operating system for opensesame native lookup provider: " + osName);
            }
            attributesSchema.attribute(NEO_OPERATING_SYSTEM_ATTRIBUTE).getDisambiguationRules().add(
                NeoOperatingSystemDisambiguationRule.class,
                config -> config.params(os)
            );
        });
    }

    public abstract static class CrochetDistributionDisambiguationRule implements AttributeDisambiguationRule<String> {
        @Override
        public void execute(MultipleCandidatesDetails<String> details) {
            details.closestMatch("joined");
        }
    }

    public abstract static class CrochetDistributionCompatiblityRule implements AttributeCompatibilityRule<String> {
        @Override
        public void execute(CompatibilityCheckDetails<String> details) {
            if ("joined".equals(details.getConsumerValue())) {
                details.compatible();
            } else if ("common".equals(details.getProducerValue())) {
                details.compatible();
            }
        }
    }

    public abstract static class NeoDistributionDisambiguationRule implements AttributeDisambiguationRule<String> {
        @Override
        public void execute(MultipleCandidatesDetails<String> details) {
            // Client libraries are a superset of server, so default to that.
            details.closestMatch("client");
        }
    }

    public abstract static class NeoOperatingSystemDisambiguationRule implements AttributeDisambiguationRule<String> {
        private final String currentOs;

        @Inject
        public NeoOperatingSystemDisambiguationRule(String currentOs) {
            this.currentOs = currentOs;
        }

        @Override
        public void execute(MultipleCandidatesDetails<String> details) {
            details.closestMatch(currentOs);
        }
    }
}
