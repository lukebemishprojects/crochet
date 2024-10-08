package dev.lukebemish.crochet.internal;

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
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

public class CrochetPlugin implements Plugin<Project> {
    // TODO: re-implement this stuff
    public static final String LOCAL_RUNTIME_CONFIGURATION_NAME = "localRuntime";
    public static final String TASK_GRAPH_RUNNER_CONFIGURATION_NAME = "crochetTaskGraphRunnerClasspath";
    public static final String TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME = "crochetTaskGraphRunnerDependencies";
    public static final String DEV_LAUNCH_CONFIGURATION_NAME = "crochetDevLaunchClasspath";
    public static final String TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME = "crochetTerminalConsoleAppender";
    public static final String TINY_REMAPPER_CONFIGURATION_NAME = "crochetTinyRemapper";
    public static final String CHRISTEN_CONFIGURATION_NAME = "crochetChristen";

    public static final String VERSION = CrochetPlugin.class.getPackage().getImplementationVersion();

    public static final Attribute<String> DISTRIBUTION_ATTRIBUTE = Attribute.of("net.neoforged.distribution", String.class);
    public static final Attribute<String> OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", String.class);
    // This attribute SHOULD NOT be published -- it is for use only in internal pre-remapping-collecting setups
    public static final Attribute<String> CROCHET_REMAP_TYPE_ATTRIBUTE = Attribute.of("dev.lukebemish.crochet.remap", String.class);
    public static final String CROCHET_REMAP_TYPE_REMAP = "remap";
    public static final String CROCHET_REMAP_TYPE_NON_REMAP = "non-remap";
    public static final String CROCHET_REMAP_TYPE_IGNORE = "non-remap";
    public static final String CROCHET_REMAP_TYPE_REMAP_CLASSPATH = "remap-classpath";

    private static final String TASK_GRAPH_RUNNER_VERSION = "0.1.0";
    private static final String DEV_LAUNCH_VERSION = "1.0.1";
    private static final String TERMINAL_CONSOLE_APPENDER_VERSION = "1.3.0";
    private static final String CHRISTEN_VERSION = "0.1.5";

    private static final String PROPERTY_TASKGRAPHRUNNER_LOG_LEVEL = "dev.lukebemish.crochet.taskgraphrunner.logging.level";
    private static final String PROPERTY_TASKGRAPHRUNNER_REMOVE_ASSET_DURATION = "dev.lukebemish.crochet.taskgraphrunner.cache.remove-assets-after";
    private static final String PROPERTY_TASKGRAPHRUNNER_REMOVE_OUTPUT_DURATION = "dev.lukebemish.crochet.taskgraphrunner.cache.remove-outputs-after";
    private static final String PROPERTY_TASKGRAPHRUNNER_REMOVE_LOCK_DURATION = "dev.lukebemish.crochet.taskgraphrunner.cache.remove-locks-after";

    @Override
    public void apply(@NotNull Project project) {
        if (!project.getGradle().getPlugins().hasPlugin(CrochetRepositoriesPlugin.class)) {
            project.getPlugins().apply(CrochetRepositoriesPlugin.class);
        } else {
            project.getLogger().debug("Skipping application of crochet repositories as it was applied at the settings level; you may still apply the plugin manually");
        }

        project.getPluginManager().apply("java-library");

        var objects = project.getObjects();

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
        project.getDependencies().add(TASK_GRAPH_RUNNER_CONFIGURATION_NAME, "dev.lukebemish:taskgraphrunner:" + TASK_GRAPH_RUNNER_VERSION);

        project.getConfigurations().register(TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME, config -> {
            config.attributes(attributes -> {
                // TaskGraphRunner runs on 21 in general
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21);
            });
            config.setCanBeConsumed(false);
        });
        ((ModuleDependency) project.getDependencies().add(TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME, "dev.lukebemish:taskgraphrunner:" + TASK_GRAPH_RUNNER_VERSION)).capabilities(capabilities -> {
            capabilities.requireCapability("dev.lukebemish:taskgraphrunner-external-tools");
        });
        ((ModuleDependency) project.getDependencies().add(TASK_GRAPH_RUNNER_TOOLS_CONFIGURATION_NAME, "dev.lukebemish.crochet:tools:" + VERSION)).attributes(attributes -> {
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
        });

        // tiny-remapper
        project.getConfigurations().register(TINY_REMAPPER_CONFIGURATION_NAME);
        project.getDependencies().add(TINY_REMAPPER_CONFIGURATION_NAME, "dev.lukebemish.crochet.wrappers:tiny-remapper:" + VERSION);

        // christen
        project.getConfigurations().register(CHRISTEN_CONFIGURATION_NAME, config -> {
            config.attributes(attributes -> {
                // TaskGraphRunner runs on 21 in general
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21);
                // Prefer shadowed jar
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
            });
            config.setCanBeConsumed(false);
        });
        project.getDependencies().add(CHRISTEN_CONFIGURATION_NAME, "dev.lukebemish:christen:" + CHRISTEN_VERSION);

        // runs
        project.getConfigurations().register(DEV_LAUNCH_CONFIGURATION_NAME);
        project.getDependencies().add(DEV_LAUNCH_CONFIGURATION_NAME, "net.neoforged:DevLaunch:" + DEV_LAUNCH_VERSION);

        project.getConfigurations().register(TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME);
        project.getDependencies().add(TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME, "net.minecrell:terminalconsoleappender:" + TERMINAL_CONSOLE_APPENDER_VERSION);

        var extension = project.getExtensions().create("crochet", CrochetExtension.class, project);

        project.getGradle().getSharedServices().registerIfAbsent("taskGraphRunnerDaemon", TaskGraphRunnerService.class, spec -> {
            spec.getParameters().getHideStacktrace().convention(project.getGradle().getStartParameter().getShowStacktrace() == ShowStacktrace.INTERNAL_EXCEPTIONS);
            spec.getParameters().getLogLevel().convention(project.getProviders().gradleProperty(PROPERTY_TASKGRAPHRUNNER_LOG_LEVEL).orElse("INFO"));
            spec.getParameters().getRemoveUnusedAssetsAfterDays().convention(project.getProviders().gradleProperty(PROPERTY_TASKGRAPHRUNNER_REMOVE_ASSET_DURATION).map(Integer::parseInt).orElse(30));
            spec.getParameters().getRemoveUnusedOutputsAfterDays().convention(project.getProviders().gradleProperty(PROPERTY_TASKGRAPHRUNNER_REMOVE_OUTPUT_DURATION).map(Integer::parseInt).orElse(30));
            spec.getParameters().getRemoveUnusedLocksAfterDays().convention(project.getProviders().gradleProperty(PROPERTY_TASKGRAPHRUNNER_REMOVE_LOCK_DURATION).map(Integer::parseInt).orElse(1));
        });

        applyDisambiguationRules(project);
        applyComponentRules(project);
    }

    private static void applyComponentRules(Project project) {
        project.getDependencies().getComponents().withModule("net.fabricmc:fabric-loader", FabricInstallerRule.class);
        project.getDependencies().getComponents().withModule("org.quiltmc:quilt-loader", FabricInstallerRule.class);
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
