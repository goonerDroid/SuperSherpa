import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sublime.supersherpa"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    ndkVersion = "30.0.14904198"

    defaultConfig {
        applicationId = "com.sublime.supersherpa"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Source sets — the Rust-built .so files land in jniLibs via cargo-ndk
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false          // extractNativeLibs=false (16KB safe)
            keepDebugSymbols += "**/*.so"
        }
    }

}

dependencies {
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.onnxruntime.android)
    implementation(libs.amirisback.keyboard) {
        exclude(group = "com.google.android.gms", module = "play-services-ads")
        exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
        exclude(group = "com.google.android.gms", module = "play-services-ads-base")
        exclude(group = "com.google.android.gms", module = "play-services-ads-api")
        exclude(group = "com.google.android.gms", module = "play-services-measurement-sdk-api")
        exclude(group = "com.google.android.gms", module = "play-services-appset")
        exclude(group = "com.google.android.gms", module = "play-services-cronet")
        exclude(group = "com.google.android.ump", module = "user-messaging-platform")
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Dedicated configuration to resolve the ORT AAR for the Rust build.
// The runtime dependency above packages libonnxruntime.so into the APK.
val ortNative: Configuration by configurations.creating
dependencies {
    ortNative(libs.onnxruntime.android)
}

// ---------------------------------------------------------------------------
// Task to extract ORT headers & native libs for Rust compilation
// ---------------------------------------------------------------------------

val extractOrt by tasks.registering(Copy::class) {
    description = "Extract ONNX Runtime AAR for Rust build"
    group = "build"

    from(ortNative.elements.map { fileCollection ->
        fileCollection.map { zipTree(it) }
    })
    into(layout.buildDirectory.dir("ort-extracted"))
}

// ---------------------------------------------------------------------------
// Rust / cargo-ndk build task
// ---------------------------------------------------------------------------

val cargoNdkBuild by tasks.registering(Exec::class) {
    description = "Build Rust native code via cargo-ndk"
    group = "build"

    dependsOn(extractOrt)

    inputs.files(
        rootProject.file("transcribe-rs/Cargo.toml"),
        rootProject.file("transcribe-rs/Cargo.lock"),
    )
    inputs.files(fileTree(rootProject.file("transcribe-rs/src")) {
        include("**/*.rs")
    })

    // The Rust crate lives in the transcribe-rs subproject, not the Android root.
    workingDir = rootProject.file("transcribe-rs")

    // Detect NDK path from local.properties or env
    val ndkDir = project.findProperty("ndk.dir")?.toString()
        ?: System.getenv("ANDROID_NDK_HOME")
        ?: System.getenv("ANDROID_NDK")
        ?: try { android.ndkDirectory.absolutePath } catch (e: Exception) { null }

    if (ndkDir != null) {
        environment("ANDROID_NDK_HOME", ndkDir)
    }

    val extractDir = layout.buildDirectory.dir("ort-extracted").get().asFile
    environment("ORT_LIB_LOCATION", File(extractDir, "jni/arm64-v8a").absolutePath)
    environment("ORT_INCLUDE_DIR", File(extractDir, "headers").absolutePath)

    val jniLibsDir = project.file("src/main/jniLibs")

    commandLine(
        "/Users/williamj/.cargo/bin/cargo", "ndk",
        "-t", "arm64-v8a",
        "-o", jniLibsDir.absolutePath,
        "build", "--release"
    )

    // Copy libc++_shared.so from NDK (needed because Rust links against it dynamically)
    doLast {
        val ndkPath = environment["ANDROID_NDK_HOME"] ?: throw GradleException("ANDROID_NDK_HOME not set")

        // Detect host OS for NDK toolchain path
        val osName = System.getProperty("os.name").lowercase()
        val hostTag = when {
            osName.contains("mac") -> "darwin-x86_64"
            osName.contains("win") -> "windows-x86_64"
            else -> "linux-x86_64"
        }

        val libcpp = file("$ndkPath/toolchains/llvm/prebuilt/$hostTag/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
        if (libcpp.exists()) {
            val destDir = File(jniLibsDir, "arm64-v8a")
            destDir.mkdirs()
            libcpp.copyTo(File(destDir, "libc++_shared.so"), overwrite = true)
            println("Copied libc++_shared.so from NDK")
        } else {
            throw GradleException("libc++_shared.so not found in NDK at: ${libcpp.absolutePath}")
        }
    }

    outputs.dir(jniLibsDir)
}

// Wire the cargo-ndk build into the Android build lifecycle
tasks.named("preBuild") {
    dependsOn(cargoNdkBuild)
}
