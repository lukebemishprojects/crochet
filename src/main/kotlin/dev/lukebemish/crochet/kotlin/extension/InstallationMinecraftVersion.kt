@file:Suppress("INAPPLICABLE_JVM_NAME")

package dev.lukebemish.crochet.kotlin.extension

import dev.lukebemish.crochet.model.AbstractVanillaInstallation
import dev.lukebemish.crochet.model.AbstractVanillaInstallationDependencies
import dev.lukebemish.crochet.model.MinecraftInstallation
import dev.lukebemish.crochet.model.SettingsMinecraftInstallation
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.provider.Provider

val MinecraftInstallation.minecraftVersion: KotlinLazyProvider<String>
    get() = object: KotlinLazyProvider<String> {
        override fun asProvider(): Provider<String> {
            return minecraft
        }
    }

val AbstractVanillaInstallation.minecraftVersion: VersionProperty
    get() = object: VersionProperty {
        override fun asProvider(): Provider<String> {
            return minecraft
        }

        override fun assign(provider: Provider<String>) {
            setMinecraft(provider)
        }

        override fun assign(value: String) {
            setMinecraft(value)
        }

        @JvmName("assignVersionConstraintProvider")
        override fun assign(provider: Provider<VersionConstraint>) {
            setMinecraft(provider)
        }

        override fun assign(value: VersionConstraint) {
            setMinecraft(value)
        }
    }

val <T: SettingsMinecraftInstallation<R, D>, R: AbstractVanillaInstallation, D: AbstractVanillaInstallationDependencies<D>>
    SettingsMinecraftInstallation.AbstractVanilla<T, R, D>.minecraftVersion: VersionSetter
    get() = object: VersionSetter {

        override fun assign(provider: Provider<String>) {
            setMinecraft(provider)
        }

        override fun assign(value: String) {
            setMinecraft(value)
        }

        @JvmName("assignVersionConstraintProvider")
        override fun assign(provider: Provider<VersionConstraint>) {
            setMinecraft(provider)
        }

        override fun assign(value: VersionConstraint) {
            setMinecraft(value)
        }
    }
