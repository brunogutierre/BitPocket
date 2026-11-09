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
    // Argon2id (not available in the JDK or on Android).
    implementation(libs.bouncycastle.prov)
    // Wallet I/O (SQLite, Esplora) runs on a per-wallet serial dispatcher.
    implementation(libs.kotlinx.coroutines.core)
    // QR codes: pure Java, so encoding (and M2 decoding) is unit tested on the JVM.
    implementation(libs.zxing.core)

    testImplementation(libs.bdk.jvm)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val includeLiveTests = providers.gradleProperty("live").isPresent

tasks.test {
    // Tests tagged "live" talk to public Signet servers: opt in with -Plive, never in CI.
    useJUnitPlatform { if (!includeLiveTests) excludeTags("live") }
    // BDK loads its native library through JNA; recent JDKs warn unless native access is enabled.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
