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
import org.gradle.api.plugins.UnknownPluginException;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

public class CrochetPlugin implements Plugin<Project> {
    // TODO: re-implement this stuff
    public static final String LOCAL_RUNTIME_CONFIGURATION_NAME = "localRuntime";
    public static final String NEOFORM_RUNTIME_CONFIGURATION_NAME = "crochetNeoformRuntimeClasspath";
    public static final String INTERMEDIARY_NEOFORM_DEPENDENCIES_CONFIGURATION_NAME = "crochetIntermediaryNeoformDependencies";
    public static final String NFRT_DEPENDENCIES_CONFIGURATION_NAME = "crochetNeoFormRuntimeDependencies";
    public static final String DEV_LAUNCH_CONFIGURATION_NAME = "crochetDevLaunchClasspath";
    public static final String TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME = "crochetTerminalConsoleAppender";
    public static final String TINY_REMAPPER_CONFIGURATION_NAME = "crochetTinyRemapper";

    public static final String VERSION = CrochetPlugin.class.getPackage().getImplementationVersion();

    public static final Attribute<String> DISTRIBUTION_ATTRIBUTE = Attribute.of("net.neoforged.distribution", String.class);
    public static final Attribute<String> OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", String.class);

    private static final String NFRT_VERSION = "1.0.4";
    private static final String DEV_LAUNCH_VERSION = "1.0.1";
    private static final String TERMINAL_CONSOLE_APPENDER_VERSION = "1.3.0";

    public static final class IntermediaryNeoFormDependencies {
        private IntermediaryNeoFormDependencies() {}

        public static final String MERGETOOL = "net.neoforged:mergetool:2.0.3:fatjar";
        public static final String ART = "net.neoforged:AutoRenamingTool:2.0.3:all";
        public static final String INSTALLERTOOLS = "net.neoforged.installertools:installertools:2.1.2:fatjar";
    }

    @Override
    public void apply(@NotNull Project project) {
        if (!project.getGradle().getPlugins().hasPlugin(CrochetRepositoriesPlugin.class)) {
            project.getPlugins().apply(CrochetRepositoriesPlugin.class);
        } else {
            project.getLogger().debug("Skipping application of crochet repositories as it was applied at the settings level; you may still apply the plugin manually");
        }

        project.getPluginManager().apply("java-library");

        var objects = project.getObjects();

        // intermediary neoform configs
        project.getConfigurations().register(INTERMEDIARY_NEOFORM_DEPENDENCIES_CONFIGURATION_NAME, config -> {
            config.setTransitive(false);
        });
        project.getDependencies().add(INTERMEDIARY_NEOFORM_DEPENDENCIES_CONFIGURATION_NAME, IntermediaryNeoFormDependencies.MERGETOOL);
        project.getDependencies().add(INTERMEDIARY_NEOFORM_DEPENDENCIES_CONFIGURATION_NAME, IntermediaryNeoFormDependencies.ART);
        project.getDependencies().add(INTERMEDIARY_NEOFORM_DEPENDENCIES_CONFIGURATION_NAME, IntermediaryNeoFormDependencies.INSTALLERTOOLS);

        // NFRT
        project.getConfigurations().register(NEOFORM_RUNTIME_CONFIGURATION_NAME, config -> config.attributes(attributes -> {
            // NFRT runs on 21 in general
            attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21);
            // Prefer shadowed jar
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
        }));
        project.getDependencies().add(NEOFORM_RUNTIME_CONFIGURATION_NAME, "net.neoforged:neoform-runtime:" + NFRT_VERSION);

        project.getConfigurations().register(NFRT_DEPENDENCIES_CONFIGURATION_NAME, config -> config.attributes(attributes -> {
            // NFRT runs on 21 in general
            attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21);
        }));
        var nfrtDeps = (ModuleDependency) project.getDependencies().add(NFRT_DEPENDENCIES_CONFIGURATION_NAME, "net.neoforged:neoform-runtime:" + NFRT_VERSION);
        nfrtDeps.capabilities(capabilities -> {
            capabilities.requireCapability("net.neoforged:neoform-runtime-external-tools");
        });

        // tiny-remapper
        project.getConfigurations().register(TINY_REMAPPER_CONFIGURATION_NAME);
        project.getDependencies().add(TINY_REMAPPER_CONFIGURATION_NAME, "dev.lukebemish.crochet.wrappers:tiny-remapper:" + VERSION);

        // runs
        project.getConfigurations().register(DEV_LAUNCH_CONFIGURATION_NAME);
        project.getDependencies().add(DEV_LAUNCH_CONFIGURATION_NAME, "net.neoforged:DevLaunch:" + DEV_LAUNCH_VERSION);

        project.getConfigurations().register(TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME);
        project.getDependencies().add(TERMINAL_CONSOLE_APPENDER_CONFIGURATION_NAME, "net.minecrell:terminalconsoleappender:" + TERMINAL_CONSOLE_APPENDER_VERSION);

        var extension = project.getExtensions().create("crochet", CrochetExtension.class, project);

        applyDisambiguationRules(project);
        applyComponentRules(project);

        setupIntelliJ(project);

        // Plan for runs:
        // - start with everything based on a javaexec task
        // - ...pain?
    }

    private static void setupIntelliJ(Project project) {
        if (Boolean.getBoolean("idea.sync.active")) {
            // We break project isolation here -- not that we have a choice, the whole setup is rather terrible
            var rootProject = project.getRootProject();
            try {
                rootProject.getPluginManager().apply("org.jetbrains.gradle.plugin.idea-ext");
            } catch (UnknownPluginException e) {
                // Ensures that classpath errors due to multiple subprojects trying to add this are impossible
                throw new IllegalStateException("Crochet requires the 'org.jetbrains.gradle.plugin.idea-ext' plugin to be available to the root project plugin classpath.", e);
            }
        }
    }

    private static void applyComponentRules(Project project) {
        project.getDependencies().getComponents().withModule("net.fabricmc:fabric-loader", FabricInstallerRule.class);
        project.getDependencies().getComponents().withModule("org.quiltmc:quilt-loader", FabricInstallerRule.class);
    }

    private static void applyDisambiguationRules(Project project) {
        project.getDependencies().attributesSchema(attributesSchema -> {
            attributesSchema.attribute(DISTRIBUTION_ATTRIBUTE).getDisambiguationRules().add(DistributionDisambiguationRule.class);

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
