package dev.lukebemish.crochet.kotlin.extension

import dev.lukebemish.crochet.model.AbstractVanillaInstallation
import dev.lukebemish.crochet.model.MinecraftInstallation
import org.gradle.api.provider.Provider

val MinecraftInstallation.minecraftVersion: KotlinLazyProvider<String>
    get() = object: KotlinLazyProvider<String> {
        override fun asProvider(): Provider<String> {
            return minecraft
        }
    }

val AbstractVanillaInstallation.minecraftVersion: KotlinLazyProperty<String>
    get() = object: KotlinLazyProperty<String> {
        override fun asProvider(): Provider<String> {
            return minecraft
        }

        override fun assign(provider: Provider<String>) {
            setMinecraft(provider)
        }

        override fun assign(value: String) {
            setMinecraft(value)
        }
    }
