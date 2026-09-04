import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Upload-key credentials. Kept out of the repo: the keystore itself lives
// wherever storeFile points, and the passwords live in keystore.properties,
// which is gitignored. Absent on a fresh clone and on CI, so the release build
// falls back to unsigned rather than failing to configure.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.machinecharades"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Store identity, permanent once the first bundle is uploaded. Namespaced
        // under the publisher rather than the game so later apps sit beside it.
        // Deliberately not the same as `namespace` above: that one only names the
        // generated R class, and changing it would move every source file.
        applicationId = "com.techtush.machinecharades"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Play rejects a bundle whose versionCode it has already seen, and a
        // closed test wants a new build most days. Override per build with
        // `-PappVersionCode=7` rather than editing this file each time.
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: "1.0"
    }
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}


/**
 * Refuses to build a release configured with a RevenueCat Test Store key.
 *
 * RevenueCat's SDK deliberately crashes on launch in a production build holding
 * one, and Play rejects submissions that carry it. Both failures land long after
 * the mistake — a crash for every player, or a rejection days into review — and
 * local.properties is the same file the debug builds read, so the wrong key is
 * one forgotten edit away from shipping.
 */
val rejectTestStoreKeyInRelease by tasks.registering {
    val props = Properties()
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) localProps.inputStream().use { props.load(it) }
    // Same override the BuildConfig generator honours, so the guard checks the
    // key that will actually ship rather than whatever happens to sit in a
    // developer's local.properties.
    val key = (findProperty("revenuecatAndroidKey") as String?)
        ?: props.getProperty("revenuecat.androidKey", "")
    inputs.property("revenuecatKey", key)
    doLast {
        check(!key.startsWith("test_")) {
            "\n\nrevenuecat.androidKey in local.properties is a Test Store key " +
                "(test_…).\nThe RevenueCat SDK crashes on launch in a release build " +
                "with this key, and Play rejects the submission.\nSwap it for the " +
                "Google Play public key (goog_…) before building a release.\n"
        }
    }
}

tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }
    .configureEach { dependsOn(rejectTestStoreKeyInRelease) }
