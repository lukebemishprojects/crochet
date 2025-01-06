package dev.lukebemish.crochet.model;

import org.gradle.api.artifacts.Configuration;

interface InstallationData {
    Configuration nonUpgradableClientCompileDependencies();
    Configuration nonUpgradableServerCompileDependencies();
    Configuration nonUpgradableClientRuntimeDependencies();
    Configuration nonUpgradableServerRuntimeDependencies();

    Configuration minecraft();
    Configuration minecraftResources();
    Configuration minecraftLineMapped();
    Configuration minecraftDependencies();
}
