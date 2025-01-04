package dev.lukebemish.crochet.kotlin.extension

import org.gradle.api.provider.Provider

interface KotlinLazyProvider<T> {
    fun asProvider(): Provider<T>

    fun get(): T {
        return asProvider().get()
    }
}
