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

    // The pure frame/cache logic runs as an ordinary JVM test; only JNI and AudioTrack need a device.
    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
    // The EARP1 pack stays uncompressed if a consumer packages it through this library.
    androidResources { noCompress += "bin" }
    namespace = "com.thomas.passiveeartraining.audio"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 29
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DMINIAUDIO_SOURCE_DIR=${rootProject.projectDir}/.local/native-sources/miniaudio"
                )
            }
        }
        ndk {
            // The planned phone ABI plus the emulator ABI; WP2.2 records the phone's real ABI.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:training"))
    api(project(":core:music"))

    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// The isolated native instrumentation consumes exactly the app's reviewed asset inputs.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.deviceTests.values.forEach { test ->
            test.sources.assets?.addStaticSourceDirectory(
                rootProject.file("app/src/main/assets").path
            )
        }
    }
}
