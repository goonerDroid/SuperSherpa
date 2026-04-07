import java.io.FileInputStream
import java.security.MessageDigest

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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    kotlinOptions {
        jvmTarget = "11"
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

    // Play Asset Delivery: large model files go into a separate asset pack
    // so the base module stays under the 200 MB Play Store limit.
    assetPacks += listOf(":model_assets")
}

dependencies {
    implementation(libs.coroutines.android)
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
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.onnxruntime.android)
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

// For APK builds (assemble/install), asset packs are ignored by AGP so we
// must include the asset-pack assets as an extra source directory.  For
// bundle builds the asset pack module handles delivery and we must NOT add
// the directory here (would cause duplicate-resource errors).
val isBundle = gradle.startParameter.taskNames.any {
    it.contains("bundle", ignoreCase = true)
}
if (!isBundle) {
    android.sourceSets.getByName("main") {
        assets.srcDirs(
            "src/main/assets",
            rootProject.file("model_assets/src/main/assets")
        )
    }
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

// ---------------------------------------------------------------------------
// Model asset download task
// ---------------------------------------------------------------------------

data class ModelFile(val name: String, val sha256: String)

// Small metadata files stay in app/src/main/assets (always in base module)
val appAssetFiles = listOf(
    ModelFile("config.json", ""),
    ModelFile("vocab.txt", ""),
)

// Large ONNX model files go into the model_assets asset pack so the base
// module stays under the Play Store 200 MB compressed-download limit.
val modelPackFiles = listOf(
    ModelFile("encoder-model.int8.onnx",
        "6139d2fa7e1b086097b277c7149725edbab89cc7c7ae64b23c741be4055aff09"),
    ModelFile("decoder_joint-model.int8.onnx",
        "eea7483ee3d1a30375daedc8ed83e3960c91b098812127a0d99d1c8977667a70"),
    ModelFile("nemo128.onnx",
        "a9fde1486ebfcc08f328d75ad4610c67835fea58c73ba57e3209a6f6cf019e9f"),
)

val huggingFaceRepo = "https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx/resolve/main"

fun downloadToDir(assetsDir: File, files: List<ModelFile>) {
    assetsDir.mkdirs()
    files.forEach { model ->
        val destFile = File(assetsDir, model.name)
        if (destFile.exists() && model.sha256.isNotEmpty()) {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(destFile).use { fis ->
                val buf = ByteArray(8192)
                var read: Int
                while (fis.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash == model.sha256) {
                println("  ✓ ${model.name} already downloaded and verified")
                return@forEach
            } else {
                println("  ✗ ${model.name} checksum mismatch, re-downloading...")
                destFile.delete()
            }
        }

        if (!destFile.exists()) {
            println("  ↓ Downloading ${model.name}...")
            val downloadUrl = "$huggingFaceRepo/${model.name}?download=true"
            val proc = ProcessBuilder("curl", "-L", "-f", "-o", destFile.absolutePath, downloadUrl)
                .inheritIO()
                .start()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                throw GradleException("Failed to download ${model.name} (curl exit code $exitCode)")
            }

            if (model.sha256.isNotEmpty()) {
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(destFile).use { fis ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (fis.read(buf).also { read = it } != -1) {
                        digest.update(buf, 0, read)
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (hash != model.sha256) {
                    throw GradleException(
                        "Checksum verification failed for ${model.name}:\n" +
                                "  Expected: ${model.sha256}\n" +
                                "  Got:      $hash"
                    )
                }
                println("  ✓ ${model.name} verified")
            }
        }
    }
}

val downloadModels by tasks.registering {
    description = "Download HuggingFace Parakeet model assets"
    group = "build"

    // Small metadata -> app assets (base module)
    val appAssetsDir = project.file("src/main/assets/parakeet-tdt-0.6b-v3-int8")
    // Large ONNX models -> asset pack (separate install-time delivery)
    val packAssetsDir = rootProject.file("model_assets/src/main/assets/parakeet-tdt-0.6b-v3-int8")

    outputs.dir(appAssetsDir)
    outputs.dir(packAssetsDir)

    doLast {
        downloadToDir(appAssetsDir, appAssetFiles)
        downloadToDir(packAssetsDir, modelPackFiles)
    }
}

tasks.named("preBuild") {
    dependsOn(downloadModels)
}
