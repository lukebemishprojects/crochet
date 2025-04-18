import dev.lukebemish.crochet.kotlin.extension.minecraftVersion

plugins {
    id("java-library")
    id("maven-publish")
    id("dev.lukebemish.crochet")
}

repositories {
    mavenCentral()
    maven {
        name = "ParchmentMC"
        url = uri("https://maven.parchmentmc.org/")
    }
    maven {
        name = "Staging"
        url = uri("https://maven.lukebemish.dev/staging/")
    }
}

crochet {
    externalVanillaInstallation("vanilla") {
        consume(sharedInstallations.vanilla)
    }

    fabricInstallation("fabric") {
        minecraftVersion = "1.21.4"

        dependencies {
            loader("net.fabricmc:fabric-loader:0.16.9")
            mappings(chained {
                add(intermediary())
                add(artifact("net.fabricmc:yarn:1.21.4+build.4:v2"))
            })
        }
    }
}
