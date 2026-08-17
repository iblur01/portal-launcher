import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Release signing lives in local.properties (gitignored) so the keystore and its passwords never
// touch the repo. Absent for every contributor except whoever cuts a release — release-signed
// builds fall back to no signingConfig, which still produces a valid (unsigned) build for CI.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStoreFile = localProps.getProperty("release.storeFile")

android {
    namespace = "com.iblu01.portallauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iblu01.portallauncher"
        minSdk = 28
        targetSdk = 28
        versionCode = 10
        versionName = "1.0.2"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Release-equivalent build for profiling on devices that already have the debug-signed
        // package installed. Keeping the same signing key lets adb update the app in place, so its
        // preferences, encrypted credentials, launcher layout and widget bindings are preserved.
        // This variant must never be distributed: the public release still uses the private key.
        create("productionTest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    lint {
        // This is a sideload-only project (see README) with no Play Store distribution, so the
        // Play-specific targetSdk-recency check is a false positive here, not a real defect —
        // targetSdk 28 is intentional for the wall-panel devices this actually runs on.
        disable += "ExpiredTargetSdkVersion"
    }

    androidResources {
        // The MDI name->codepoint index is binary-searched in place with AssetManager.openFd(),
        // which only works on a stored (non-deflated) APK entry. Compressing it would trade a
        // 330 KB APK saving for a megabyte of permanently resident heap.
        noCompress += "mdi"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric needs merged resources/manifest
        }
    }
}

androidComponents {
    // These are variant-agnostic Robolectric/JVM unit tests; running them twice (once per
    // build type) is redundant. More importantly, the Compose test manifest that provides
    // androidx.activity.ComponentActivity (needed by createComposeRule()/ActivityScenarioRule)
    // is only declared as debugImplementation(libs.compose.ui.test.manifest) so it merges into
    // the debug variant's manifest but never ships in the release APK, as intended. Robolectric
    // resolves the unit-test manifest from each build type's own main manifest (testImplementation
    // manifests are not merged in for JVM unit tests), so testReleaseUnitTest can never resolve
    // that activity. Disable release-equivalent unit-test variants rather than duplicating
    // test-only manifest content into a release-shipped path.
    listOf("release", "productionTest").forEach { buildType ->
        beforeVariants(selector().withBuildType(buildType)) { variantBuilder ->
            variantBuilder.enableUnitTest = false
        }
    }
}

dependencies {
    implementation(libs.paho.mqtt)
    implementation(libs.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Remote configuration screen: LAN HTTP server + QR code for the phone to scan.
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)

    // Installs the baseline profile (src/main/baseline-prof.txt + the Compose libraries' own
    // profiles, merged by AGP) so cold start runs AOT-compiled instead of interpreted+JIT.
    // Debuggable builds ignore AOT: measure on `productionTest` or `release`.
    implementation(libs.profileinstaller)

    // MAD architecture: structured concurrency + StateFlow/ViewModel + immutable collections.
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.collections.immutable)

    // Encrypted storage for HA token / MQTT password.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Hilt dependency injection.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)

    // Headless Android/Compose behavior tests (JVM, no emulator).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
