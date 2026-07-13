import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Optional release signing: create android/keystore.properties (see README).
// Without it, release builds fall back to the debug key so that
// `assembleRelease` always yields an installable APK for sideloading.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

android {
    namespace = "org.hedgewars.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.hedgewars.android"
        minSdk = 21
        targetSdk = 36
        versionCode = 204 // major*10000 + minor*100 + patch
        versionName = "0.2.4"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 full-mode minification breaks the SDL activity launch: the
            // game window never reports drawn/focused, so SDL's main thread
            // (which gates on mHasFocus) never starts and the engine never
            // runs. The keep rules that would make it safe aren't pinned down
            // yet, so keep the release build unminified — the sideload APK is
            // dominated by the ~218 MB game data anyway, so the size cost of
            // skipping code shrinking is negligible.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystoreProps.isNotEmpty())
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    // Game data lives in the :data install-time asset pack (Play delivery).
    // For sideload/debug APKs, -PdataInApp bakes the same staged Data
    // directory straight into the app's assets instead.
    if (project.hasProperty("dataInApp")) {
        sourceSets["main"].assets.srcDir(
            rootProject.file("data/src/main/assets")
        )
    } else {
        assetPacks += ":data"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // Uncompressed .so in the APK: required for 16 KB page alignment
            // checks; pre-M devices extract them at install time regardless.
            useLegacyPackaging = false
        }
    }
    bundle {
        // All ABIs ship in the AAB; Play serves per-device splits.
        abi { enableSplit = true }
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
