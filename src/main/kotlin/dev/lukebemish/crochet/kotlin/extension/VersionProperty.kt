package dev.lukebemish.crochet.kotlin.extension

import org.gradle.api.SupportsKotlinAssignmentOverloading

@SupportsKotlinAssignmentOverloading
interface VersionProperty : KotlinLazyProperty<String>, VersionSetter
