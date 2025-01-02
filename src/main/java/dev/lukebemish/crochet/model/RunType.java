package dev.lukebemish.crochet.model;

import java.io.Serializable;

enum RunType implements Serializable {
    CLIENT("client"),
    SERVER("server"),
    DATA("client"); // TODO: figure this out in newer envs

    private final String attributeName;

    String attributeName() {
        return attributeName;
    }

    RunType(String attributeName) {
        this.attributeName = attributeName;
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
