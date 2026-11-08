plugins {
    alias(libs.plugins.android.library)
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

    namespace = "com.thomas.passiveeartraining.media"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 29
    }

    // The pure mapping between a session snapshot and the Media3 player state runs as an ordinary
    // JVM test; only the service and the session need a device.
    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    api(project(":core:training"))
    api(project(":core:audio"))
    // Media3 owns the media session, its notification and the controller surface. SimpleBasePlayer
    // is used instead of a stock player because sound comes from our own AudioTrack output.
    implementation(libs.coroutines.core)
    api(libs.media3.common)
    api(libs.media3.session)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
