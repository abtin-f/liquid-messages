import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Load the release signing config from keystore.properties (git-ignored) if present.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystorePropsFile.exists()

android {
    namespace = "com.liquidglass.messages"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.liquidglass.messages"
        minSdk = 24
        targetSdk = 34
        versionCode = 161
        versionName = "1.6.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Minification is OFF by default so the signed release is guaranteed to
            // run without device testing; flip both to true once R8 is verified.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Properly sign the release build (the key fix for install/AV warnings).
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
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
    composeOptions {
        // Compose compiler 1.5.14 is the version matched to Kotlin 1.9.24.
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        // Skip the vital release lint pass (needs a separate lint-gradle artifact);
        // not required for a self-signed sideload build.
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests {
            // Robolectric needs merged resources/fonts to render real screenshots.
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // Render through HardwareRenderer so RenderNodes/RenderEffects
                // (the Liquid Glass pipeline) show up in screenshots.
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxHeapSize = "2g"
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Compose BOM keeps all Compose artifacts on one compatible version set ---
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core / lifecycle / activity
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose UI + Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Image loading (contact photos) â€” Compose integration
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Splash screen (Android 12+ API with backport)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // --- Tooling / preview ---
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // --- Test ---
    testImplementation("junit:junit:4.13.2")
    // JVM screenshot tests of the real Compose screens (no device needed).
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
