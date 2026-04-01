package com.sublime.supersherpa.core.ai

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.VersionInfo
import com.k2fsa.sherpa.onnx.getOfflineModelConfig

data class SherpaSmokeTestResult(
    val version: String,
    val gitSha1: String,
    val gitDate: String,
    val nativeVersion: String,
    val nativeGitSha1: String,
    val nativeGitDate: String,
    val featureConfigReady: Boolean,
    val recognizerConfigReady: Boolean,
    val modelConfigReady: Boolean,
    val errorMessage: String? = null,
) {
    val passed: Boolean
        get() = errorMessage == null
}

object SherpaSmokeTestRunner {
    fun run(): SherpaSmokeTestResult {
        return runCatching {
            val versionInfo = VersionInfo.Companion
            val featureConfig = FeatureConfig()
            val recognizerConfig = OfflineRecognizerConfig()
            val modelConfig = getOfflineModelConfig(0)

            SherpaSmokeTestResult(
                version = versionInfo.version,
                gitSha1 = versionInfo.gitSha1,
                gitDate = versionInfo.gitDate,
                nativeVersion = versionInfo.getVersionStr2(),
                nativeGitSha1 = versionInfo.getGitSha12(),
                nativeGitDate = versionInfo.getGitDate2(),
                featureConfigReady = featureConfig.sampleRate > 0,
                recognizerConfigReady = recognizerConfig.maxActivePaths >= 0,
                modelConfigReady = modelConfig!!.numThreads >= 0,
            )
        }.getOrElse { throwable ->
            SherpaSmokeTestResult(
                version = "unknown",
                gitSha1 = "unknown",
                gitDate = "unknown",
                nativeVersion = "unknown",
                nativeGitSha1 = "unknown",
                nativeGitDate = "unknown",
                featureConfigReady = false,
                recognizerConfigReady = false,
                modelConfigReady = false,
                errorMessage = throwable.message ?: throwable::class.java.name,
            )
        }
    }
}
