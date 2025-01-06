package dev.lukebemish.crochet.model;

import org.gradle.api.Named;
import org.gradle.api.provider.Property;

public interface GeneralizedMinecraftInstallation extends Named {
    Property<InstallationDistribution> getDistribution();

    default void client() {
        this.getDistribution().set(InstallationDistribution.CLIENT);
    }

    default void common() {
        this.getDistribution().set(InstallationDistribution.COMMON);
    }

    default void joined() {
        this.getDistribution().set(InstallationDistribution.JOINED);
    }

    default void server() {
        this.getDistribution().set(InstallationDistribution.SERVER);
    }
}
