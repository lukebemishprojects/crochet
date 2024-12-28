package dev.lukebemish.crochet.model;

import java.io.Serializable;

public enum InstallationDistribution implements Serializable {
    CLIENT("client"),
    SERVER("server"),
    JOINED("client"),
    COMMON("server");

    private final String attributeValue;

    InstallationDistribution(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    public String neoAttributeValue() {
        return attributeValue;
    }
}
