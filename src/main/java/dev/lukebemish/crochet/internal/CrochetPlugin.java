package dev.lukebemish.crochet.internal;

import dev.lukebemish.crochet.CrochetProperties;
import dev.lukebemish.crochet.model.CrochetExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

public class CrochetPlugin implements Plugin<Project> {
    public static final String TASK_GRAPH_RUNNER_CONFIGURATION_NAME = "crochetTaskGraphRunnerClasspath";
    public static final String TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME = "crochetTaskGraphRunnerDependencies";
    public static final String DEV_LAUNCH_CONFIGURATION_NAME = "crochetDevLaunchClasspath";
    public static final String TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME = "crochetTerminalConsoleAppender";

    public static final String VERSION = CrochetPlugin.class.getPackage().getImplementationVersion();

    public static final Attribute<String> DISTRIBUTION_ATTRIBUTE = Attribute.of("net.neoforged.distribution", String.class);
    public static final Attribute<String> OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", String.class);
    // This attribute SHOULD NOT be published -- it is for use only in internal pre-remapping-collecting setups
    public static final Attribute<String> CROCHET_REMAP_TYPE_ATTRIBUTE = Attribute.of("dev.lukebemish.crochet.remap", String.class);
    // Dependencies on things that'll need to be remapped when it's all said and done
    public static final String CROCHET_REMAP_TYPE_REMAP = "to-remap";
    // Dependencies on other projects contents', non-remapped components
    public static final String CROCHET_REMAP_TYPE_NON_REMAP = "not-to-remap";

    @Override
    public void apply(@NotNull Project project) {
        if (project.getProviders().gradleProperty(CrochetProperties.ADD_LIKELY_REPOSITORIES).map(Boolean::parseBoolean).orElse(true).get()) {
            if (!project.getGradle().getPlugins().hasPlugin(CrochetRepositoriesPlugin.class)) {
                project.getPlugins().apply(CrochetRepositoriesPlugin.class);
            } else {
                project.getLogger().debug("Skipping application of crochet repositories as it was applied at the settings level; you may still apply the plugin manually");
            }
        }

        project.getPluginManager().apply("java-library");

        var objects = project.getObjects();

        project.getExtensions().create("crochet.internal.mappingsConfigurationContainer", MappingsConfigurationCounter.class);

        // TaskGraphRunner
        project.getConfigurations().register(TASK_GRAPH_RUNNER_CONFIGURATION_NAME, config -> {
            config.attributes(attributes -> {
                // TaskGraphRunner runs on 21 in general
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21);
                // Prefer shadowed jar
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
            });
            config.setCanBeConsumed(false);
        });
        project.getDependencies().add(TASK_GRAPH_RUNNER_CONFIGURATION_NAME, "dev.lukebemish:taskgraphrunner:" + Versions.TASK_GRAPH_RUNNER);

        project.getConfigurations().register(TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME, config -> {
            config.attributes(attributes -> {
                // TaskGraphRunner runs on 21 in general
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21);
            });
            config.setCanBeConsumed(false);
        });
        ((ModuleDependency) project.getDependencies().add(TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME, "dev.lukebemish:taskgraphrunner:" + Versions.TASK_GRAPH_RUNNER)).capabilities(capabilities -> {
            capabilities.requireCapability("dev.lukebemish:taskgraphrunner-external-tools");
        });
        ((ModuleDependency) project.getDependencies().add(TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME, "dev.lukebemish.crochet:tools:" + VERSION)).attributes(attributes -> {
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
        });
        ((ModuleDependency) project.getDependencies().add(TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME, "dev.lukebemish:christen:" + Versions.CHRISTEN)).attributes(attributes -> {
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
        });


        // runs
        project.getConfigurations().register(DEV_LAUNCH_CONFIGURATION_NAME);
        project.getDependencies().add(DEV_LAUNCH_CONFIGURATION_NAME, "net.neoforged:DevLaunch:" + Versions.DEV_LAUNCH);

        project.getConfigurations().register(TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME);
        project.getDependencies().add(TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME, "net.minecrell:terminalconsoleappender:" + Versions.TERMINAL_CONSOLE_APPENDER);

        // configurations
        setupConventionalConfigurations(project);

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

            var localRuntime = project.getConfigurations().maybeCreate(sourceSet.getTaskName(null, "localRuntime"));
            runtimeClasspath.extendsFrom(localRuntime);

            var localImplementation = project.getConfigurations().maybeCreate(sourceSet.getTaskName(null, "localImplementation"));
            compileClasspath.extendsFrom(localImplementation);
            runtimeClasspath.extendsFrom(localImplementation);
        });
    }

    private static void applyDisambiguationRules(Project project) {
        project.getDependencies().attributesSchema(attributesSchema -> {
            attributesSchema.attribute(DISTRIBUTION_ATTRIBUTE).getDisambiguationRules().add(DistributionDisambiguationRule.class);
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
            attributesSchema.attribute(OPERATING_SYSTEM_ATTRIBUTE).getDisambiguationRules().add(
                OperatingSystemDisambiguationRule.class,
                config -> config.params(os)
            );
        });
    }

    public abstract static class DistributionDisambiguationRule implements AttributeDisambiguationRule<String> {
        @Override
        public void execute(MultipleCandidatesDetails<String> details) {
            // Client libraries are a superset of server, so default to that.
            details.closestMatch("client");
        }
    }

    public abstract static class OperatingSystemDisambiguationRule implements AttributeDisambiguationRule<String> {
        private final String currentOs;

        @Inject
        public OperatingSystemDisambiguationRule(String currentOs) {
            this.currentOs = currentOs;
        }

        @Override
        public void execute(MultipleCandidatesDetails<String> details) {
            details.closestMatch(currentOs);
        }
    }
}
