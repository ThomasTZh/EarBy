plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:music"))
    // The session controller owns a scope and publishes state as a Flow.
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    // Test-only JSON parsing of the shared voice catalog; the runtime library is already part of
    // the project.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.coroutines.test)
    // Turbine is not needed yet: the session tests assert on StateFlow values, and an unused
    // dependency would only add verification surface.
}
