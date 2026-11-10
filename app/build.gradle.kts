plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cindrix.pocket"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cindrix.pocket"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    // Signing: if the build provides a release keystore (GitHub Actions secrets), use it.
    // Otherwise fall back to the keystore committed in this repo (app/cindrix-debug.keystore).
    // That fallback key is NOT secret. It only keeps the signature stable so new builds
    // install over old ones. Do not hand APKs signed with it to people you do not know.
    signingConfigs {
        create("cindrix") {
            val ks = System.getenv("POCKET_KEYSTORE_FILE")
            if (ks != null && ks.isNotBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("POCKET_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("POCKET_KEY_ALIAS")
                keyPassword = System.getenv("POCKET_KEY_PASSWORD")
            } else {
                storeFile = file("cindrix-debug.keystore")
                storePassword = "android"
                keyAlias = "cindrixdebug"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("cindrix")
        }
        debug {
            signingConfig = signingConfigs.getByName("cindrix")
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
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Barcode decoding, on the phone, Apache-2.0
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
