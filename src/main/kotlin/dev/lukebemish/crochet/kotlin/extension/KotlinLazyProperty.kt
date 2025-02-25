package dev.lukebemish.crochet.kotlin.extension

import org.gradle.api.SupportsKotlinAssignmentOverloading

@SupportsKotlinAssignmentOverloading
interface KotlinLazyProperty<T>: KotlinLazyProvider<T>, KotlinLazySetter<T>
