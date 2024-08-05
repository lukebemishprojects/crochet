package dev.lukebemish.crochet.internal;

import dev.lukebemish.crochet.model.CrochetExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

public class CrochetPlugin implements Plugin<Project> {
    // TODO: re-implement this stuff
    public static final String LOCAL_RUNTIME_CONFIGURATION_NAME = "localRuntime";
    public static final String NEOFORM_RUNTIME_CONFIGURATION_NAME = "crochetNeoformRuntimeClasspath";
    public static final String DEV_LAUNCH_CONFIGURATION_NAME = "crochetDevLaunchClasspath";
    public static final String VERSION = CrochetPlugin.class.getPackage().getImplementationVersion();

    public static final Attribute<String> DISTRIBUTION_ATTRIBUTE = Attribute.of("net.neoforged.distribution", String.class);
    public static final Attribute<String> OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", String.class);

    private static final String NFRT_VERSION = "1.0.1";
    private static final String DEV_LAUNCH_VERSION = "1.0.1";

    @Override
    public void apply(@NotNull Project project) {
        if (!project.getGradle().getPlugins().hasPlugin(CrochetRepositoriesPlugin.class)) {
            project.getPlugins().apply(CrochetRepositoriesPlugin.class);
        } else {
            project.getLogger().debug("Skipping application of crochet repositories as it was applied at the settings level; you may still apply the plugin manually");
        }

        project.getPluginManager().apply("java-library");

        var objects = project.getObjects();

        project.getConfigurations().register(NEOFORM_RUNTIME_CONFIGURATION_NAME, config -> config.attributes(attributes -> {
            // NFRT runs on 21 in general
            attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21);
            // Prefer shadowed jar
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.SHADOWED));
        }));
        project.getDependencies().add(NEOFORM_RUNTIME_CONFIGURATION_NAME, "net.neoforged:neoform-runtime:" + NFRT_VERSION);

        project.getConfigurations().register(DEV_LAUNCH_CONFIGURATION_NAME);
        project.getDependencies().add(DEV_LAUNCH_CONFIGURATION_NAME, "net.neoforged:DevLaunch:" + DEV_LAUNCH_VERSION);

        var extension = project.getExtensions().create("crochet", CrochetExtension.class, project);

        applyDisambiguationRules(project);
        applyComponentRules(project);

        // Plan for runs:
        // - start with everything based on a javaexec task
        // - ...pain?
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
