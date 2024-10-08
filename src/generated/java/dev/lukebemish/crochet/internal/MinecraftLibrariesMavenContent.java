package dev.lukebemish.crochet.internal;

import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;

class MinecraftLibrariesMavenContent {
    static void applyContent(RepositoryContentDescriptor descriptor) {
        descriptor.includeModule("ca.weblite", "java-objc-bridge");
        descriptor.includeModule("com.fasterxml.jackson.core", "jackson-annotations");
        descriptor.includeModule("com.fasterxml.jackson.core", "jackson-core");
        descriptor.includeModule("com.fasterxml.jackson.core", "jackson-databind");
        descriptor.includeModule("com.github.oshi", "oshi-core");
        descriptor.includeModule("com.github.stephenc.jcip", "jcip-annotations");
        descriptor.includeModule("com.google.code.gson", "gson");
        descriptor.includeModule("com.google.guava", "failureaccess");
        descriptor.includeModule("com.google.guava", "guava");
        descriptor.includeModule("com.ibm.icu", "icu4j");
        descriptor.includeModule("com.microsoft.azure", "msal4j");
        descriptor.includeModule("com.mojang", "authlib");
        descriptor.includeModule("com.mojang", "blocklist");
        descriptor.includeModule("com.mojang", "brigadier");
        descriptor.includeModule("com.mojang", "datafixerupper");
        descriptor.includeModule("com.mojang", "jtracy");
        descriptor.includeModule("com.mojang", "logging");
        descriptor.includeModule("com.mojang", "patchy");
        descriptor.includeModule("com.mojang", "text2speech");
        descriptor.includeModule("com.nimbusds", "content-type");
        descriptor.includeModule("com.nimbusds", "lang-tag");
        descriptor.includeModule("com.nimbusds", "nimbus-jose-jwt");
        descriptor.includeModule("com.nimbusds", "oauth2-oidc-sdk");
        descriptor.includeModule("commons-codec", "commons-codec");
        descriptor.includeModule("commons-io", "commons-io");
        descriptor.includeModule("commons-logging", "commons-logging");
        descriptor.includeModule("io.netty", "netty-buffer");
        descriptor.includeModule("io.netty", "netty-codec");
        descriptor.includeModule("io.netty", "netty-common");
        descriptor.includeModule("io.netty", "netty-handler");
        descriptor.includeModule("io.netty", "netty-resolver");
        descriptor.includeModule("io.netty", "netty-transport-classes-epoll");
        descriptor.includeModule("io.netty", "netty-transport-native-epoll");
        descriptor.includeModule("io.netty", "netty-transport-native-unix-common");
        descriptor.includeModule("io.netty", "netty-transport");
        descriptor.includeModule("it.unimi.dsi", "fastutil");
        descriptor.includeModule("net.java.dev.jna", "jna-platform");
        descriptor.includeModule("net.java.dev.jna", "jna");
        descriptor.includeModule("net.minidev", "accessors-smart");
        descriptor.includeModule("net.minidev", "json-smart");
        descriptor.includeModule("net.sf.jopt-simple", "jopt-simple");
        descriptor.includeModule("org.apache.commons", "commons-compress");
        descriptor.includeModule("org.apache.commons", "commons-lang3");
        descriptor.includeModule("org.apache.httpcomponents", "httpclient");
        descriptor.includeModule("org.apache.httpcomponents", "httpcore");
        descriptor.includeModule("org.apache.logging.log4j", "log4j-api");
        descriptor.includeModule("org.apache.logging.log4j", "log4j-core");
        descriptor.includeModule("org.apache.logging.log4j", "log4j-slf4j2-impl");
        descriptor.includeModule("org.jcraft", "jorbis");
        descriptor.includeModule("org.joml", "joml");
        descriptor.includeModule("org.lwjgl", "lwjgl-freetype");
        descriptor.includeModule("org.lwjgl", "lwjgl-glfw");
        descriptor.includeModule("org.lwjgl", "lwjgl-jemalloc");
        descriptor.includeModule("org.lwjgl", "lwjgl-openal");
        descriptor.includeModule("org.lwjgl", "lwjgl-opengl");
        descriptor.includeModule("org.lwjgl", "lwjgl-stb");
        descriptor.includeModule("org.lwjgl", "lwjgl-tinyfd");
        descriptor.includeModule("org.lwjgl", "lwjgl");
        descriptor.includeModule("org.lz4", "lz4-java");
        descriptor.includeModule("org.ow2.asm", "asm");
        descriptor.includeModule("org.slf4j", "slf4j-api");
        descriptor.includeModule("com.mojang", "javabridge");
        descriptor.includeModule("org.apache.logging.log4j", "log4j-slf4j18-impl");
        descriptor.includeModule("io.netty", "netty-all");
        descriptor.includeModule("net.java.jinput", "jinput");
        descriptor.includeModule("net.java.jutils", "jutils");
        descriptor.includeModule("oshi-project", "oshi-core");
        descriptor.includeModule("net.java.dev.jna", "platform");
        descriptor.includeModule("com.ibm.icu", "icu4j-core-mojang");
        descriptor.includeModule("com.mojang", "realms");
        descriptor.includeModule("com.paulscode", "codecjorbis");
        descriptor.includeModule("com.paulscode", "codecwav");
        descriptor.includeModule("com.paulscode", "libraryjavasound");
        descriptor.includeModule("com.paulscode", "soundsystem");
        descriptor.includeModule("com.paulscode", "librarylwjglopenal");
        descriptor.includeModule("org.lwjgl.lwjgl", "lwjgl");
        descriptor.includeModule("org.lwjgl.lwjgl", "lwjgl_util");
        descriptor.includeModule("org.lwjgl.lwjgl", "lwjgl-platform");
        descriptor.includeModule("net.java.jinput", "jinput-platform");
        descriptor.includeModule("com.mojang", "netty");
        descriptor.includeModule("tv.twitch", "twitch");
        descriptor.includeModule("tv.twitch", "twitch-platform");
        descriptor.includeModule("tv.twitch", "twitch-external-platform");
        descriptor.includeModule("java3d", "vecmath");
        descriptor.includeModule("net.sf.trove4j", "trove4j");
        descriptor.includeModule("org.bouncycastle", "bcprov-jdk15on");
        descriptor.includeModule("argo", "argo");
        descriptor.includeModule("net.minecraft", "launchwrapper");
        descriptor.includeModule("org.ow2.asm", "asm-all");
        descriptor.includeGroupAndSubgroups("com.mojang");
    }
}
