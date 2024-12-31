package dev.lukebemish.crochet.model;

import org.gradle.api.Named;

import javax.inject.Inject;

public class SplitSourceSet implements Named {
    private final String name;
    private final MinecraftInstallation joined;
    private final MinecraftInstallation common;
    private final MinecraftInstallation client;
    private final MinecraftInstallation server;

    @Inject public SplitSourceSet(String name, MinecraftInstallation joined, MinecraftInstallation common, MinecraftInstallation client, MinecraftInstallation server) {
        this.name = name;
        this.joined = joined;
        this.common = common;
        this.client = client;
        this.server = server;
    }

    @Override
    public String getName() {
        return name;
    }

    MinecraftInstallation getJoined() {
        if (joined == null) {
            throw new IllegalStateException("Joined source set not present in "+getName());
        }
        return joined;
    }

    MinecraftInstallation getCommon() {
        if (common == null) {
            throw new IllegalStateException("Common source set not present in "+getName());
        }
        return common;
    }

    MinecraftInstallation getClient() {
        if (client == null) {
            throw new IllegalStateException("Client source set not present in "+getName());
        }
        return client;
    }

    MinecraftInstallation getServer() {
        if (server == null) {
            throw new IllegalStateException("Server source set not present in "+getName());
        }
        return server;
    }
}
