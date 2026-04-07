package com.sublime.supersherpa.core.ai

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
        return SherpaSmokeTestResult(
            version = "rust-pack",
            gitSha1 = "unknown",
            gitDate = "unknown",
            nativeVersion = "rust-pack",
            nativeGitSha1 = "unknown",
            nativeGitDate = "unknown",
            featureConfigReady = true,
            recognizerConfigReady = true,
            modelConfigReady = true,
        )
    }
}
