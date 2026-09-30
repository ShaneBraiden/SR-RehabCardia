import java.util.Properties

val keyProps = Properties().also { props ->
    val f = rootProject.file("keystore.properties")
    if (f.exists()) props.load(f.inputStream())
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.srcardiocare"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.srcardiocare"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.1.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Bundle native debug symbols (from Firestore, Media3, etc.) into the AAB
        // so Play Console can symbolicate native crash/ANR stack traces.
        ndk {
            debugSymbolLevel = "FULL"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = keyProps.getProperty("storeFile")?.let { file(it) }
            storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias")
            keyPassword = keyProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // Signed only when keystore.properties is present; otherwise the build
            // still runs R8/shrinking end to end and yields an unsigned bundle
            // (which Play rejects). Android Studio's "Generate Signed Bundle"
            // injects its own signing either way.
            if (keyProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // applicationIdSuffix removed — google-services.json only has com.srcardiocare
        }
    }

    androidResources {
        // Ship only the locales we actually translate. AGP 9 removed
        // defaultConfig.resourceConfigurations — localeFilters replaces it.
        localeFilters += listOf("en", "ta")
    }

    bundle {
        language {
            // MUST stay false. Play delivers language splits by *device* locale,
            // so with splitting on, a handset whose system language is English
            // never receives values-ta at all and the in-app Tamil switch
            // silently renders English. The bug cannot be seen in a debug APK —
            // it only appears once the app is installed from a bundle.
            // This app chooses its own language, so every locale we ship has to
            // be in the base APK.
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM — single version for all Compose libraries
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // material-icons-extended is ~15 MB but R8 dead-code strips it to only the
    // icons your code references in release builds (isMinifyEnabled = true above).
    // In debug, full dex is loaded — acceptable trade-off for dev tooling.
    implementation("androidx.compose.material:material-icons-extended")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Core & AppCompat
    implementation("androidx.core:core-ktx:1.19.1")

    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0")

    // Firebase BOM — single version for all Firebase libraries
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    // Callable functions — permanent account deletion needs the Admin SDK,
    // which only the backend can reach.
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-messaging")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // Media3 ExoPlayer (for native mp4/network playback)
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Baseline Profiles — speeds up cold start by pre-compiling hot paths via ART.
    // Generate a profile once: ./gradlew :app:generateBaselineProfile
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Debug tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
