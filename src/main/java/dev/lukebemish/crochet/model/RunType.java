package dev.lukebemish.crochet.model;

import java.io.Serializable;

enum RunType implements Serializable {
    CLIENT("client"),
    SERVER("server"),
    DATA("client"); // TODO: figure this out in newer envs

    private final String attributeName;

    public String attributeName() {
        return attributeName;
    }

    RunType(String attributeName) {
        this.attributeName = attributeName;
    }
}
