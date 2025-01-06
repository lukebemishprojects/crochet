package dev.lukebemish.crochet.kotlin.extension

import org.gradle.api.SupportsKotlinAssignmentOverloading
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.provider.Provider

@Suppress("INAPPLICABLE_JVM_NAME")
@SupportsKotlinAssignmentOverloading
interface VersionSetter: KotlinLazySetter<String> {
    @JvmName("assignVersionConstraintProvider")
    fun assign(provider: Provider<VersionConstraint>)

    @JvmName("assignVersionConstraintProvider")
    fun assign(value: VersionConstraint)
}
