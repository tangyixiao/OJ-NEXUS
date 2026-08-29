// OJ NEXUS — root build configuration.
// Module configuration lives in app/build.gradle.kts; shared versions live in gradle/libs.versions.toml.
// AGP 9 ships built-in Kotlin support; the standalone Kotlin Android plugin must not be applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
