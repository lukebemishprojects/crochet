package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetPlugin;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import javax.inject.Inject;

public abstract class VanillaInstallation extends AbstractVanillaInstallation {
    final Provider<Configuration> clientRuntimeClasspath;
    final Provider<Configuration> serverRuntimeClasspath;

    @Inject
    public VanillaInstallation(String name, CrochetExtension extension) {
        super(name, extension);
        this.clientRuntimeClasspath = this.project.getConfigurations().register(name+"ClientRuntimeClasspath", configuration -> {
            configuration.extendsFrom(this.minecraft);
        });
        serverRuntimeClasspath = this.project.getConfigurations().register(name+"ServerRuntimeClasspath", configuration -> {
            configuration.extendsFrom(this.minecraft);
        });
    }

    public void dependencies(Action<InstallationDependencies> action) {
        action.execute(getDependencies());
    }

    @Override
    protected void forRun(Run run, RunType runType) {
        Configuration runtimeClasspath = project.getConfigurations().maybeCreate("crochet" + StringUtils.capitalize(this.getName()) + StringUtils.capitalize(run.getName()) + "Classpath");
        runtimeClasspath.extendsFrom(minecraft);
        runtimeClasspath.attributes(attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            // We just default to 21 here if nothing is specified -- we'll want to be smarter about this in the future
            // and try and pull it from the source compile tasks I guess?
            attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, run.getToolchain().getLanguageVersion().map(JavaLanguageVersion::asInt).orElse(21));
        });
        run.getClasspath().from(runtimeClasspath);
        switch (runType) {
            case CLIENT -> {
                runtimeClasspath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "client"));
                // TODO: pull this from neoform if it's there?
                run.getMainClass().convention("net.minecraft.client.main.Main");
            }
            case SERVER -> {
                runtimeClasspath.attributes(attributes -> attributes.attribute(CrochetPlugin.DISTRIBUTION_ATTRIBUTE, "server"));
                // TODO: pull this from neoform if it's there?
                run.getMainClass().convention("net.minecraft.server.Main");
            }
        }
    }
}
