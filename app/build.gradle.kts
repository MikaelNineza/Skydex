import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase values come from local.properties or the environment, never from a committed file.
val localProperties = Properties().apply {
    val text = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
    if (text != null) load(text.reader())
}

fun secret(name: String): String =
    localProperties.getProperty(name) ?: providers.environmentVariable(name).orNull ?: ""

// Release versions come from the tag (vX.Y.Z) through the release workflow. Local builds default to 0.0.1 (code 1),
// so any published release counts as newer when testing the update check.
val appVersionName: String = providers.gradleProperty("skydex.versionName")
    .orElse(providers.environmentVariable("SKYDEX_VERSION_NAME"))
    .getOrElse("0.0.1")

// Must match AppVersion in :shared (build scripts can't use project code).
val appVersionCode: Int = Regex("""(0|[1-9]\d{0,3})\.(0|[1-9]\d?)\.(0|[1-9]\d?)""").matchEntire(appVersionName)
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt() }
    ?.takeIf { it > 0 }
    ?: throw GradleException("Version '$appVersionName' must be MAJOR.MINOR.PATCH, minor and patch below 100")

// Where release builds find the server. Unusable values become "" so checkReleaseConfig reports them clearly
// (a quote or backslash would otherwise break BuildConfig compilation first).
val releaseBaseUrl: String = secret("SKYDEX_BASE_URL").trim()
    .let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
    .takeUnless { url -> url.any { it == '"' || it == '\\' || it.isWhitespace() } } ?: ""

android {
    namespace = "com.skydex.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.skydex.app"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        for (name in listOf("FIREBASE_APP_ID", "FIREBASE_API_KEY", "FIREBASE_PROJECT_ID", "FIREBASE_SENDER_ID")) {
            buildConfigField("String", name, "\"${secret(name)}\"")
        }
    }

    signingConfigs {
        // Only when a keystore is configured; otherwise release builds come out unsigned (app-release-unsigned.apk).
        val keystorePath = secret("SKYDEX_KEYSTORE_PATH")
        if (keystorePath.isNotBlank()) create("release") {
            storeFile = file(keystorePath)
            storePassword = secret("SKYDEX_KEYSTORE_PASSWORD")
            keyAlias = secret("SKYDEX_KEY_ALIAS")
            keyPassword = secret("SKYDEX_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // `./gradlew :server:run` on this machine, forwarded by the adbReverse task below.
            buildConfigField("String", "BASE_URL", "\"http://localhost:8080/\"")
            // Off by default: debug builds are version 0.0.1, so every published release would show up.
            buildConfigField("boolean", "CHECK_UPDATES_ON_LAUNCH", "${secret("SKYDEX_DEBUG_UPDATE_CHECK") == "true"}")
        }
        release {
            // From SKYDEX_BASE_URL; checkReleaseConfig below refuses to package without a real one.
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
            buildConfigField("boolean", "CHECK_UPDATES_ON_LAUNCH", "true")
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Lets JVM tests run code that logs through android.util.Log.
        unitTests.isReturnDefaultValues = true
    }
}

// Android 17 blocks apps from reaching the host machine (10.0.2.2 or a LAN address) without a local-network
// permission, but loopback is allowed. So debug builds call localhost:8080 and adb forwards it to this machine,
// for emulators and USB phones alike. Fails harmlessly when no device is connected.
val adbReverse = tasks.register<Exec>("adbReverse") {
    executable = androidComponents.sdkComponents.adb.get().asFile.path
    args("reverse", "tcp:8080", "tcp:8080")
    isIgnoreExitValue = true
}
tasks.matching { it.name == "assembleDebug" || it.name == "installDebug" }.configureEach {
    finalizedBy(adbReverse)
}

// Release APKs/AABs must point at a real server. Runs only when release packaging runs, so debug builds, unit tests
// and lint work without SKYDEX_BASE_URL. Captures only a String, so it's configuration-cache safe.
val checkReleaseConfig = tasks.register("checkReleaseConfig") {
    val url = releaseBaseUrl
    doLast {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
        // The reserved example domains (and their subdomains) are documentation placeholders, never a real server.
        val placeholder = host != null && listOf("example.com", "example.org", "example.net")
            .any { host == it || host.endsWith(".$it") }
        if (!url.startsWith("https://") || host.isNullOrBlank() || placeholder) {
            throw GradleException(
                "Release builds need SKYDEX_BASE_URL=https://<your server>/ in the environment or local.properties (got '$url')."
            )
        }
    }
}
// packageRelease writes the APK; packageReleaseBundle / signReleaseBundle write the AAB (bundleRelease only depends on them).
val releasePackagingTasks = setOf("packageRelease", "packageReleaseBundle", "signReleaseBundle", "packageReleaseUniversalApk")
tasks.matching { it.name in releasePackagingTasks }.configureEach {
    dependsOn(checkReleaseConfig)
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
