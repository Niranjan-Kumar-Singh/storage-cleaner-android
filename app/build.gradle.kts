plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "com.example.storagecleaner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.storagecleaner"
        minSdk        = 30
        targetSdk     = 34
        versionCode   = 3
        versionName   = "3.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile     = file("debug.keystore")
            storePassword = "android"
            keyAlias      = "androiddebugkey"
            keyPassword   = "android"
        }
        create("release") {
            val keystorePath = System.getenv("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() } ?: "release.keystore"
            val keystoreFile = file(keystorePath)
            if (keystoreFile.exists()) {
                storeFile     = keystoreFile
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
                keyAlias      = System.getenv("RELEASE_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "androiddebugkey"
                keyPassword   = System.getenv("RELEASE_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
            } else {
                // Fallback to debug keystore for CI/local builds when release keystore is unset
                storeFile     = file("debug.keystore")
                storePassword = "android"
                keyAlias      = "androiddebugkey"
                keyPassword   = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = false
            isShrinkResources = false
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
