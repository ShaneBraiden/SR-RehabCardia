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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.srcardiocare"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

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
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    // Compose BOM — single version for all Compose libraries
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
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
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Core & AppCompat
    implementation("androidx.core:core-ktx:1.16.0")

    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Firebase BOM — single version for all Firebase libraries
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-messaging-ktx")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // Google Sign-In (for YouTube OAuth2 upload scope)
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // WebView (for YouTube embed playback)
    implementation("androidx.webkit:webkit:1.12.1")

    // Media3 ExoPlayer (for native mp4/network playback)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // Networking — Retrofit + OkHttp (kept for potential future REST APIs)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Baseline Profiles — speeds up cold start by pre-compiling hot paths via ART.
    // Generate a profile once: ./gradlew :app:generateBaselineProfile
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Debug tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
