import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
}

// Formatting is enforced from the root so every module (and build script) follows one style.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}

// Merged coverage over all modules: add each new module to `dependencies { kover(...) }`.
dependencies {
    kover(project(":app"))
    kover(project(":core"))
}

kover {
    reports {
        filters {
            excludes {
                // UI, Android entry points, DI wiring and generated code are covered by
                // UI/instrumented tests (or not at all), not by the JVM unit test gate.
                classes(
                    "*.ui.*",
                    "*.di.*",
                    // Android Keystore/BiometricManager code: covered by instrumented tests.
                    "*.keystore.*",
                    "*.biometric.*",
                    "*ComposableSingletons*",
                    "*.BuildConfig",
                    "*.R",
                    "*.R$*",
                    "*.MainActivity",
                    "*.MainActivityKt",
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
        verify {
            rule("Minimum 80% line coverage") {
                minBound(80, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
            }
        }
    }
}
