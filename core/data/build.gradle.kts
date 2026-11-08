plugins {
    alias(libs.plugins.android.library)
    // Serialization compiles the preset document format. The plugin ships with Kotlin, so no new
    // toolchain version is introduced.
    alias(libs.plugins.kotlin.serialization)
}

android {
    // Keep automated builds inside the workspace: the debug signing key has a byte-identical
    // local copy (see app/build.gradle.kts and scripts/env.sh), so installs keep the same
    // signature while a read-only home cannot break the build.
    signingConfigs {
        getByName("debug") {
            val localDebugKeystore =
                rootProject.layout.projectDirectory.file(".local/debug.keystore").asFile
            if (localDebugKeystore.isFile) {
                storeFile = localDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    namespace = "com.thomas.passiveeartraining.data"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 29
        // The store's Android adapter runs against real SQLite on a device; the host tests cover
        // the same statements through a JDBC engine.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    api(project(":core:training"))
    // Settings reference presentation types, so consumers of the repository need them too.
    api(project(":core:music"))
    // Typed settings storage: one DataStore per file with atomic updates.
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jackson.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // A real SQLite engine for the host tests, so schema and migrations are tested rather than
    // mocked. Production uses the platform's own SQLite; see status notes.
    testImplementation(libs.sqlite.jdbc)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
