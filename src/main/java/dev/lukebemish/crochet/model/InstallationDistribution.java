package dev.lukebemish.crochet.model;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public enum InstallationDistribution implements Serializable {
    CLIENT("client"),
    SERVER("server"),
    JOINED("client");

    private final String attributeValue;

    InstallationDistribution(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    public String attributeValue() {
        return attributeValue;
    }
}
