package com.sublime.supersherpa.core.ai.modeldelivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteModelManifestParserTest {
    @Test
    fun parse_readsPinnedManifest() {
        val rawManifest = """
            {
              "schema_version": 1,
              "model_id": "parakeet-tdt-0.6b-v3-int8",
              "version": "parakeet-v1",
              "revision": "8f23f0c",
              "base_url": "https://huggingface.co/example/resolve/8f23f0c",
              "files": [
                { "name": "vocab.txt", "sha256": "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d" },
                { "name": "encoder-model.int8.onnx", "sha256": "6139d2fa7e1b086097b277c7149725edbab89cc7c7ae64b23c741be4055aff09" },
                { "name": "decoder_joint-model.int8.onnx", "sha256": "eea7483ee3d1a30375daedc8ed83e3960c91b098812127a0d99d1c8977667a70" },
                { "name": "nemo128.onnx", "sha256": "a9fde1486ebfcc08f328d75ad4610c67835fea58c73ba57e3209a6f6cf019e9f" }
              ]
            }
        """.trimIndent()

        val manifest = RemoteModelManifestParser.parse(rawManifest)

        assertEquals("parakeet-v1", manifest.version)
        assertEquals("8f23f0c", manifest.revision)
        assertEquals(4, manifest.files.size)
    }

    @Test
    fun parse_rejectsUnpinnedMainManifest() {
        val rawManifest = """
            {
              "schema_version": 1,
              "model_id": "parakeet-tdt-0.6b-v3-int8",
              "version": "parakeet-v1",
              "revision": "main",
              "base_url": "https://huggingface.co/example/resolve/main",
              "files": [
                { "name": "vocab.txt", "sha256": "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d" },
                { "name": "encoder-model.int8.onnx", "sha256": "6139d2fa7e1b086097b277c7149725edbab89cc7c7ae64b23c741be4055aff09" },
                { "name": "decoder_joint-model.int8.onnx", "sha256": "eea7483ee3d1a30375daedc8ed83e3960c91b098812127a0d99d1c8977667a70" },
                { "name": "nemo128.onnx", "sha256": "a9fde1486ebfcc08f328d75ad4610c67835fea58c73ba57e3209a6f6cf019e9f" }
              ]
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            RemoteModelManifestParser.parse(rawManifest)
        }
    }
}
