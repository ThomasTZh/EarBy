plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt", "core/*/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "core/*/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.register<Exec>("checkArchitecture") {
    group = "verification"
    description = "Checks that domain modules stay independent of Android and UI."
    commandLine("python3", "scripts/check-architecture.py")
}
