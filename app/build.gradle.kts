plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties
import java.io.FileInputStream

// Load keystore properties if available
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.privateai.camera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.privateai.camera"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "2.1.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystorePropertiesFile.exists()) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Distribution flavors. Both produce a working APK today; the split exists
    // so future Pro / Sync code (Play Store only) can be cleanly excluded from
    // the F-Droid build, and so each flavor can show a different "Support
    // development" link in Settings without runtime branching elsewhere.
    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FDROID", "true")
            buildConfigField("String", "DISTRIBUTION", "\"fdroid\"")
        }
        create("playstore") {
            dimension = "distribution"
            // Default flavor — `./gradlew installDebug` resolves to installPlaystoreDebug.
            isDefault = true
            buildConfigField("boolean", "IS_FDROID", "false")
            buildConfigField("String", "DISTRIBUTION", "\"playstore\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Extract native libraries to /data/app-lib at install time instead of
    // loading them in-place from inside the APK. Required to avoid an
    // UnsatisfiedLinkError on 32-bit ARM devices (Samsung Galaxy A13 reported)
    // where ML Kit's libbarhopper_v3.so failed to dlopen via the apk!path
    // syntax in armeabi-v7a split APKs. Adds ~10-15 MB on-disk after install
    // because libs are now stored compressed in the APK; acceptable trade-off
    // for stability on low-end devices.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    // ONNX Runtime
    implementation(libs.onnxruntime.android)

    // ExifInterface
    implementation(libs.androidx.exifinterface)

    // AppCompat (per-app language switching)
    implementation(libs.androidx.appcompat)

    // Biometric
    implementation(libs.androidx.biometric)

    // WorkManager (reminder missed-sweep)
    implementation(libs.androidx.work.runtime)

    // Testing
    testImplementation(libs.junit)

    // SQLCipher (encrypted SQLite)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)

    // Wi-Fi Transfer (embedded HTTP server)
    implementation(libs.nanohttpd)

    // ZXing (QR code generation)
    implementation(libs.zxing.core)

    // PdfBox-Android — text-layer extraction from already-imported PDFs.
    // Phase 2 of "Ask My Documents": lets the Assistant read PDFs that
    // weren't scanned through Privora's scanner.
    implementation(libs.pdfbox.android)

    // LiteRT-LM (Gemma 4 on-device LLM)
    implementation(libs.litertlm.android)

    // ML Kit (Track A complete after this flavor-gate). Translate is now
    // playstoreImplementation only — the fdroid flavor uses a Gemma-based
    // Translator instead (see app/src/fdroid/java/.../bridge/TranslatorImpl.kt).
    // The fdroid APK has ZERO ML Kit packages — F-Droid main eligibility
    // unblocked once this lands.
    "playstoreImplementation"(libs.mlkit.translate)
    "playstoreImplementation"(libs.kotlinx.coroutines.play.services)
    // kotlinx-coroutines-play-services is needed for ML Kit's Task.await()
    // helpers, so it follows mlkit-translate into the playstore-only set.

    // Tesseract 5 (OCR) — Track A1.3 replacement for ML Kit
    // text-recognition. Multi-language by design; tessdata files are
    // downloaded lazily per language at runtime (TesseractDataManager).
    implementation(libs.tesseract4android)
}
