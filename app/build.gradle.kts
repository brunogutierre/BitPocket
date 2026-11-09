import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Navigation 3 back stacks are saved as @Serializable NavKeys.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "io.github.brunogutierre.bitpocket"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.brunogutierre.bitpocket"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // minSdk 30 devices are 64-bit; shipping only 64-bit ABIs keeps the BDK native
        // libraries (~15 MB per ABI) out of the APK for architectures nobody runs.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            // BDK loads its native library through JNA (bdk-jvm in unit tests, see below).
            it.jvmArgs("--enable-native-access=ALL-UNNAMED")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.bdk.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ProcessLifecycleOwner: app-wide foreground/background for auto-lock.
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // JVM unit tests run the real BDK with its desktop natives (bdk-jvm, same API and version as
    // bdk-android, whose Android .so files cannot load on the JVM; it is excluded below).
    testImplementation(libs.bdk.jvm)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "org.bitcoindevkit", module = "bdk-android")
}
