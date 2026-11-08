plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    // Test-only JSON parsing of the checked-in music21 fixtures; the runtime library is already
    // part of the project (core:data).
    testImplementation(libs.kotlinx.serialization.json)
}
