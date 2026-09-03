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

/**
 * This machine's Swift runtime for the simulator.
 *
 * RevenueCat's iOS SDK is Swift, and the klib it publishes carries a linker
 * search path from the machine it was built on (/Applications/Xcode-16.4.app),
 * which exists nowhere else. The app framework links anyway; the test
 * executable does not, and fails on Swift type metadata with no useful message.
 */
val swiftSimulatorLibs = providers.exec { commandLine("xcode-select", "-p") }
    .standardOutput.asText
    .map { it.trim() + "/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/iphonesimulator" }

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        iosTarget.binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>()
            .configureEach {
                linkerOpts("-L${swiftSimulatorLibs.get()}")
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
        // purchases-kmp reaches StoreKit through cinterop, so the iOS source
        // sets have to opt in explicitly or they will not compile.
        named { it.lowercase().startsWith("ios") }.configureEach {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.purchases.core)
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
    // RevenueCat publishes one public SDK key per store. Both ship inside the
    // binary by design — they identify the app and authorise nothing — but they
    // stay out of the repo like the Firebase one.
    val rcAndroid = props.getProperty("revenuecat.androidKey", "")
    val rcIos = props.getProperty("revenuecat.iosKey", "")
    inputs.property("rcAndroid", rcAndroid)
    inputs.property("rcIos", rcIos)
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
                const val REVENUECAT_ANDROID_KEY: String = "$rcAndroid"
                const val REVENUECAT_IOS_KEY: String = "$rcIos"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir(generateBuildConfig) }
