package dev.lukebemish.crochet.model;

import dev.lukebemish.crochet.internal.CrochetProjectPlugin;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.provider.Provider;

import java.io.Serializable;

public enum InstallationDistribution implements Serializable {
    COMMON("common", "server"),
    SERVER("server", "server"),
    CLIENT("client", "client"),
    JOINED("joined", "client");

    private final String attributeValue;
    private final String neoAttributeValue;

    InstallationDistribution(String attributeValue, String neoAttributeValue) {
        this.attributeValue = attributeValue;
        this.neoAttributeValue = neoAttributeValue;
    }

    String neoAttributeValue() {
        return neoAttributeValue;
    }

    String attributeValue() {
        return attributeValue;
    }

    public void apply(AttributeContainer attributes) {
        attributes.attribute(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, neoAttributeValue);
        attributes.attribute(CrochetProjectPlugin.CROCHET_DISTRIBUTION_ATTRIBUTE, attributeValue);
    }

    public static void applyLazy(Provider<InstallationDistribution> provider, AttributeContainer attributes) {
        attributes.attributeProvider(CrochetProjectPlugin.NEO_DISTRIBUTION_ATTRIBUTE, provider.map(it -> it.neoAttributeValue));
        attributes.attributeProvider(CrochetProjectPlugin.CROCHET_DISTRIBUTION_ATTRIBUTE, provider.map(it -> it.attributeValue));
    }
}
