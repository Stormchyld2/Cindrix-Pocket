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
        versionCode = 3
        versionName = "0.3.0"
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
    // Android's own JSON reader is only a stub in plain JVM tests
    testImplementation("org.json:json:20240303")
}

// The map page (Maps screen) is the standalone map viewer's map code, built from ../mapweb into app/src/main/assets/map.
// A normal build does this by itself: it needs Node.js 22 or newer installed once, nothing else.
val mapweb = rootProject.file("mapweb")
val npmCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val installMapPage by tasks.registering(Exec::class) {
    workingDir = mapweb
    commandLine(npmCommand, "install", "--no-audit", "--no-fund")
    onlyIf {
        val done = File(mapweb, "node_modules/.package-lock.json")
        !done.exists() || done.lastModified() < File(mapweb, "package.json").lastModified()
    }
    doFirst {
        val ok = try { ProcessBuilder(npmCommand, "--version").redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor() == 0 } catch (e: Exception) { false }
        if (!ok) throw GradleException("The map page in Cindrix Pocket is built with Node.js. Install Node.js 22 or newer once (https://nodejs.org), then build again.")
    }
}

val buildMapPage by tasks.registering(Exec::class) {
    dependsOn(installMapPage)
    workingDir = mapweb
    commandLine(npmCommand, "run", "build")
    inputs.dir(File(mapweb, "src"))
    inputs.files(File(mapweb, "package.json"), File(mapweb, "build.mjs"))
    outputs.dir(layout.projectDirectory.dir("src/main/assets/map"))
}

tasks.named("preBuild") { dependsOn(buildMapPage) }
