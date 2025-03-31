package dev.lukebemish.crochet.model;

import java.io.Serializable;

enum RunType implements Serializable {
    CLIENT(InstallationDistribution.CLIENT),
    SERVER(InstallationDistribution.SERVER),
    DATA(InstallationDistribution.CLIENT); // TODO: figure this out in newer envs

    private final InstallationDistribution distribution;

    InstallationDistribution distribution() {
        return distribution;
    }

    RunType(InstallationDistribution distribution) {
        this.distribution = distribution;
    }

    boolean allowsDistribution(InstallationDistribution installationDistribution) {
        return switch (installationDistribution) {
            case CLIENT -> this != RunType.SERVER;
            case SERVER -> this != RunType.CLIENT;
            case JOINED -> true;
            case COMMON -> false;
        };
    }
}
