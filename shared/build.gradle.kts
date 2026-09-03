import org.jetbrains.kotlin.gradle.dsl.JvmTarget
// Imported at the top: inside the script body `java` resolves to the Gradle
// java extension, which shadows the package name.
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "com.machinecharades.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// No ui-tooling here on purpose. The KMP template adds it to androidRuntimeClasspath,
// which covers every variant, and that drags androidx.compose.ui.tooling.PreviewActivity
// — an exported activity — into the release manifest and the shipped dex. Nothing in
// :shared declares @Preview, and androidApp already carries ui-tooling on
// debugImplementation for the one preview that exists, so previews are unaffected.

/**
 * Generates BuildConfig from local.properties.
 *
 * The Worker URL and the Firebase Web API key live in local.properties, which
 * is gitignored. The Firebase key is not a secret — it ships in every client
 * binary by design and only identifies the project — but keeping it out of the
 * repo means one less thing to rotate if the repo ever goes public.
 *
 * Missing values generate empty strings rather than failing the build, so a
 * fresh clone still compiles; the app reports the misconfiguration at runtime.
 */
val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    outputs.dir(outputDir)

    // NB: not `Properties().apply { ... }` — inside a Gradle build script
    // `apply` resolves to Project.apply(Action), not the stdlib scope function.
    val props = Properties()
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) localProps.inputStream().use { stream -> props.load(stream) }
    val workerUrl = props.getProperty("worker.url", "")
    val firebaseKey = props.getProperty("firebase.webApiKey", "")
    val devPuzzle = props.getProperty("dev.puzzleNumber", "")
    inputs.property("workerUrl", workerUrl)
    inputs.property("firebaseKey", firebaseKey)
    inputs.property("devPuzzle", devPuzzle)

    doLast {
        val dir = outputDir.get().asFile.resolve("com/machinecharades/config")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """
            package com.machinecharades.config

            // GENERATED from local.properties by :shared:generateBuildConfig.
            // Do not edit; do not commit a key here.
            internal object BuildConfig {
                const val WORKER_URL: String = "$workerUrl"
                const val FIREBASE_WEB_API_KEY: String = "$firebaseKey"
                /** Dev only: force a puzzle number before the schedule starts. */
                const val DEV_PUZZLE_NUMBER: String = "$devPuzzle"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir(generateBuildConfig) }
