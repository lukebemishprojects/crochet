package dev.lukebemish.crochet.kotlin.extension

import org.gradle.api.SupportsKotlinAssignmentOverloading
import org.gradle.api.provider.Provider

@SupportsKotlinAssignmentOverloading
interface KotlinLazyProperty<T>: KotlinLazyProvider<T> {
    fun assign(value: T)

    fun assign(provider: Provider<T>)
}
