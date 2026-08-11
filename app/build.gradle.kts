plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.phonk.editor"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "dev.phonk.editor"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O2", "-fvisibility=hidden")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.4.2"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.register<Copy>("copyApkToSdcard") {
    val apkDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
    from(apkDir) {
        include("*.apk", "output-metadata.json")
    }
    into("/sdcard")
    doNotTrackState("Cannot scan /sdcard for incremental state")
    doFirst {
        mkdir("/sdcard")
    }
}

// --- CURRENT STATE SNAPSHOT TOOLING (developer/debugging aid only) ---
//
// * The build-attempt marker is written when the task graph is ready — BEFORE
//   any packaging task runs. snapshot.py compares the produced APK mtime to it,
//   so a failed/incomplete assemble* is detected without ever failing the build.
// * collectTestResults runs after testDebugUnitTest/testReleaseUnitTest and
//   aggregates JUnit XML into build/outputs/state/test-results.json.
// * generateStateSnapshot runs after every assembleDebug/assembleRelease and
//   regenerates APP_CURRENT_STATE.txt on shared storage + its history.

// The build result is captured from Gradle's own task graph when the snapshot
// task runs (it is finalizedBy assemble*). If any task failed, snapshot.py is
// told to write the BUILD_FAILED report and leave the last known-good state
// untouched; otherwise it regenerates the full CURRENT STATE report. This is
// accurate even for incremental/up-to-date builds, unlike APK-mtime heuristics.
fun snapshotApkRoot(): File = File(projectDir, "build/outputs/apk")
fun snapshotStateDir(): File = File(projectDir, "build/outputs/state")

tasks.register<Exec>("collectTestResultsDebug") {
    description = "Aggregate JUnit XML (debug) into state/test-results.json"
    workingDir(rootProject.projectDir)
    commandLine("python3", "tools/state/test-summary.py",
        File(projectDir, "build/test-results/testDebugUnitTest").path,
        File(snapshotStateDir(), "test-results.json").path)
    doNotTrackState("Snapshot tooling writes derived state files")
    isIgnoreExitValue = true
}

tasks.register<Exec>("collectTestResultsRelease") {
    description = "Aggregate JUnit XML (release) into state/test-results.json"
    workingDir(rootProject.projectDir)
    commandLine("python3", "tools/state/test-summary.py",
        File(projectDir, "build/test-results/testReleaseUnitTest").path,
        File(snapshotStateDir(), "test-results.json").path)
    doNotTrackState("Snapshot tooling writes derived state files")
    isIgnoreExitValue = true
}

tasks.register<Exec>("generateStateSnapshot") {
    description = "Regenerate the CURRENT STATE snapshot after every APK build"
    workingDir(rootProject.projectDir)
    commandLine("bash", "tools/state/snapshot.sh",
        rootProject.projectDir.path,
        snapshotApkRoot().path,
        "--keep", "20")
    doNotTrackState("Snapshot tooling writes derived state files")
    isIgnoreExitValue = true
    doFirst {
        val failed = gradle.taskGraph.allTasks.any { it.state.failure != null }
        environment["STATE_SNAPSHOT_BUILD_OK"] = if (failed) "false" else "true"
    }
}

tasks.whenTaskAdded {
    when (name) {
        "assembleDebug", "assembleRelease" -> {
            finalizedBy("copyApkToSdcard")
            finalizedBy("generateStateSnapshot")
        }
        "testDebugUnitTest" -> finalizedBy("collectTestResultsDebug")
        "testReleaseUnitTest" -> finalizedBy("collectTestResultsRelease")
    }
}