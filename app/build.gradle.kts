plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Typed Navigation 3 keys are @Serializable, so the back stack survives process recreation.
    alias(libs.plugins.kotlin.serialization)
    // Host UI regression checks record and verify screenshots without a device.
    alias(libs.plugins.roborazzi)
}

android {
    // The debug signing key lives inside the project when a copy exists (scripts/env.sh keeps
    // automated runs inside the workspace; AGP cannot validate a store in a read-only home).
    // The copy is byte-identical to the user's ~/.android/debug.keystore, so installed apps keep
    // upgrading in place. The file is untracked local state, never a backup input.
    signingConfigs {
        val releaseStore = providers.environmentVariable("EARBY_UPLOAD_STORE").orNull
        val releaseStorePassword =
            providers.environmentVariable("EARBY_UPLOAD_STORE_PASSWORD").orNull
        val releaseKeyAlias = providers.environmentVariable("EARBY_UPLOAD_KEY_ALIAS").orNull
        val releaseKeyPassword =
            providers.environmentVariable("EARBY_UPLOAD_KEY_PASSWORD").orNull
        if (releaseStore != null && releaseStorePassword != null &&
            releaseKeyAlias != null && releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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

    // The EARP1 pack is a large binary already close to its entropy limit. Storing it
    // uncompressed in every variant keeps staging fast and avoids inflating it on every asset read.
    androidResources {
        noCompress += "bin"
    }
    packaging {
        jniLibs {
            // DataStore 1.2 bundles a native multi-process version counter used only by the
            // multi-process store. This app has one process and uses the single-process store, so
            // the library is dead weight; the APK guard requires every packaged native library to
            // be accounted for.
            excludes += "**/libdatastore_shared_counter.so"
            // The renderer is built only for arm64-v8a and x86_64. Dependency AARs also ship
            // 32-bit copies of libandroidx.graphics.path.so; packaging those without a matching
            // libear_audio.so makes the APK look 32-bit capable and then fails at native load.
            excludes += "lib/armeabi-v7a/**"
            excludes += "lib/x86/**"
        }
    }
    namespace = "com.thomas.passiveeartraining"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.earby.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notAnnotation"] = "androidx.test.filters.LargeTest"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        create("review") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".review"
            matchingFallbacks += "debug"
        }
    }
    testBuildType = providers.gradleProperty("ear.device.variant").orElse("debug").get()
    sourceSets.maybeCreate("androidTestReview").kotlin.srcDir("src/reviewAndroidTest/kotlin")

    buildFeatures {
        compose = true
        // The Help page shows the packaged version; BuildConfig is generated per build.
        buildConfig = true
    }
    // Instrumentation tests for the debug-only diagnostic proof live in their own source set so
    // the release variant has no test that depends on the diagnostics screen.
    sourceSets.getByName("androidTestDebug") {
        kotlin.srcDir("src/debugAndroidTest/kotlin")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
    }
    testOptions {
        unitTests {
            // Robolectric needs the merged manifest and resources to render a screen on the host.
            isIncludeAndroidResources = true
        }
    }
}

configurations.named("reviewImplementation") {
    extendsFrom(configurations.getByName("debugImplementation"))
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:training"))
    // Sound comes from the native renderer through the media service, so the audio module is a
    // normal dependency rather than a debug-only one.
    implementation(project(":core:audio"))
    // Playback ownership lives in the media service; release builds ship it too, because
    // background playback is a product requirement rather than a development aid.
    implementation(project(":core:media"))
    // Settings and presets are application-scoped stores that the service and the screens share.
    implementation(project(":core:data"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    // Typed in-process destinations; Navigation 3 is the reviewed choice for WP4.3.
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.datastore.preferences)
    debugImplementation(libs.compose.ui.test.manifest)

    // ViewModel rules are ordinary host tests: repositories are injected, so no device is needed.
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Host UI regression checks: real Compose rendering on Robolectric, images via Roborazzi. The
    // Compose test artifact is the same line as the app graph; the BOM keeps it aligned.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.core)
}

// One canonical directory for both the plugin and captures. Relative overrides use repo root.
val screenshotsDir = providers.gradleProperty("ear.screenshots.dir").orNull
val screenshotTarget = screenshotsDir?.let { rootProject.file(it) }
    ?: layout.projectDirectory.dir("src/test/screenshots").asFile
roborazzi { outputDir.set(screenshotTarget) }
tasks.withType<Test>().configureEach {
    systemProperty("ear.screenshots.dir", screenshotTarget.canonicalPath)
    // Review captures may also request the dark scheme (-Pear.screenshots.theme=dark) so the dark
    // review set can be re-captured by qa.sh without editing the tests. It only changes which
    // theme a candidate render uses; it never approves anything.
    providers.gradleProperty("ear.screenshots.theme").orNull?.let {
        systemProperty("ear.screenshots.theme", it)
    }
    // Robolectric runs offline from a project-local dependency directory when one is present
    // (scripts/download-toolchain.py provisions it), so host captures never write to the user's
    // Maven cache and work in sandboxes with a read-only home.
    val robolectricDependencies =
        rootProject.layout.projectDirectory.dir(".local/robolectric-dependencies").asFile
    if (robolectricDependencies.isDirectory) {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", robolectricDependencies.canonicalPath)
    }
    if (providers.gradleProperty("ear.ui.fresh").orNull == "true") {
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }
}
gradle.taskGraph.whenReady {
    val records = allTasks.any { it.name.startsWith("recordRoborazzi") } ||
        providers.gradleProperty("roborazzi.test.record").orNull == "true" ||
        System.getProperty("roborazzi.test.record") == "true"
    if (records && screenshotTarget.canonicalFile ==
        layout.projectDirectory.dir("src/test/screenshots").asFile.canonicalFile
    ) {
        require(providers.gradleProperty("ear.screenshots.approved").orNull == "true") {
            "Baseline recording requires explicit owner approval; generate candidates instead"
        }
    }
}
