import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: wallet logic that can be unit tested on the JVM, without Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // The BDK flavor is chosen by the consumer: bdk-android in :app, bdk-jvm in tests.
    // Both expose the same org.bitcoindevkit API.
    compileOnly(libs.bdk.jvm)

    testImplementation(libs.bdk.jvm)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // BDK loads its native library through JNA; recent JDKs warn unless native access is enabled.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
