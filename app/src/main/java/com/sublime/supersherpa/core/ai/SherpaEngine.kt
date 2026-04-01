package com.sublime.supersherpa.core.ai

import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.HomophoneReplacerConfig
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.OnlineCtcFstDecoderConfig
import com.k2fsa.sherpa.onnx.OnlineLMConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizerResult
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

data class SherpaEngineError(
    val message: String,
    val causeMessage: String? = null,
)

data class SherpaModelAssets(
    val rootDir: String = "sherpa-onnx-streaming-zipformer-en-2023-06-26",
    val encoderFileName: String = "encoder-epoch-99-avg-1-chunk-16-left-128.onnx",
    val decoderFileName: String = "decoder-epoch-99-avg-1-chunk-16-left-128.onnx",
    val tokensFileName: String = "tokens.txt",
    val joinerFileName: String = "joiner-epoch-99-avg-1-chunk-16-left-128.onnx",
) {
    val encoder: String = pathOf(encoderFileName)
    val decoder: String = pathOf(decoderFileName)
    val tokens: String = pathOf(tokensFileName)
    val joiner: String? = joinerFileName?.let(::pathOf)
    val requiredPaths: List<String> = buildList {
        add(encoder)
        add(decoder)
        add(tokens)
        joiner?.let(::add)
    }

    fun pathOf(fileName: String): String = "$rootDir/$fileName"

}

data class SherpaEngineConfig(
    val sampleRateHz: Int = 16_000,
    val featureDim: Int = 80,
    val numThreads: Int = maxOf(1, Runtime.getRuntime().availableProcessors().coerceAtMost(4)),
    val provider: String = "cpu",
    val modelType: String = "transducer",
    val decodingMethod: String = "greedy_search",
    val maxActivePaths: Int = 1,
    val hotwordsScore: Float = 1.0f,
    val blankPenalty: Float = 0.0f,
)

internal interface SherpaRecognizerLoader {
    fun createRecognizer(): SherpaRecognizerHandle
}

internal interface SherpaRecognizerHandle : Closeable {
    fun createStream(): SherpaStreamHandle

    fun decode(stream: SherpaStreamHandle)

    fun isReady(stream: SherpaStreamHandle): Boolean

    fun getResult(stream: SherpaStreamHandle): OnlineRecognizerResult
}

internal interface SherpaStreamHandle : Closeable {
    fun acceptWaveform(samples: FloatArray, sampleRateHz: Int)
}

class SherpaEngine(
    private val assets: SherpaModelAssets = SherpaModelAssets(),
    private val config: SherpaEngineConfig = SherpaEngineConfig(),
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) : TranscriptionEngine {
    private val lock = Any()
    private var recognizer: SherpaRecognizerHandle? = null
    private var stream: SherpaStreamHandle? = null
    private var hasPendingAudio: Boolean = false

    @Volatile
    var lastError: SherpaEngineError? = null
        private set

    @Volatile
    var lastResultText: String = ""
        private set

    val isInitialized: Boolean
        get() = synchronized(lock) { recognizer != null }

    val isStreaming: Boolean
        get() = synchronized(lock) { stream != null }

    override suspend fun initialize(context: Context): Result<Unit> {
        val loader = AndroidSherpaRecognizerLoader(
            assetManager = context.applicationContext.assets,
            assets = assets,
            config = config,
        )
        return initialize(loader)
    }

    internal suspend fun initialize(loader: SherpaRecognizerLoader): Result<Unit> {
        synchronized(lock) {
            if (recognizer != null) {
                lastError = null
                return Result.success(Unit)
            }
        }

        val creationResult = runCatching {
            withContext(dispatcher) {
                loader.createRecognizer()
            }
        }

        return creationResult
            .onSuccess { created ->
                synchronized(lock) {
                    recognizer?.close()
                    recognizer = created
                    lastError = null
                }
            }
            .map { }
            .recoverCatching { throwable ->
                val error = throwable.toSherpaEngineError()
                synchronized(lock) {
                    lastError = error
                }
                throw throwable
            }
    }

    override fun startStreaming(): Result<Unit> {
        val recognizerHandle = synchronized(lock) {
            when {
                recognizer == null -> {
                    val error = SherpaEngineError("SherpaEngine has not been initialized.")
                    lastError = error
                    return Result.failure(IllegalStateException(error.message))
                }
                stream != null -> {
                    val error = SherpaEngineError("SherpaEngine is already streaming.")
                    lastError = error
                    return Result.failure(IllegalStateException(error.message))
                }
                else -> recognizer
            }
        } ?: return Result.failure(IllegalStateException("SherpaEngine has not been initialized."))

        return runCatching {
            val createdStream = recognizerHandle.createStream()
            synchronized(lock) {
                lastResultText = ""
                hasPendingAudio = false
                stream = createdStream
                lastError = null
            }
        }.map { }
            .recoverCatching { throwable ->
                synchronized(lock) {
                    lastError = throwable.toSherpaEngineError()
                }
                throw throwable
            }
    }

    override fun acceptAudio(chunk: ShortArray): Result<Unit> {
        val activeStream = synchronized(lock) {
            stream ?: return Result.failure(IllegalStateException("SherpaEngine is not streaming."))
        }
        return runCatching {
            activeStream.acceptWaveform(chunk.toFloatSamples(), config.sampleRateHz)
            synchronized(lock) {
                hasPendingAudio = true
                lastError = null
            }
        }.map { }
            .recoverCatching { throwable ->
                synchronized(lock) {
                    lastError = throwable.toSherpaEngineError()
                }
                throw throwable
            }
    }

    override fun acceptAudio(chunk: ByteArray): Result<Unit> {
        val activeStream = synchronized(lock) {
            stream ?: return Result.failure(IllegalStateException("SherpaEngine is not streaming."))
        }
        return runCatching {
            activeStream.acceptWaveform(chunk.toFloatSamples(), config.sampleRateHz)
            synchronized(lock) {
                hasPendingAudio = true
                lastError = null
            }
        }.map { }
            .recoverCatching { throwable ->
                synchronized(lock) {
                    lastError = throwable.toSherpaEngineError()
                }
                throw throwable
            }
    }

    override suspend fun getResult(): Result<String> {
        val currentStream = synchronized(lock) { stream }
        if (currentStream == null) {
            val recognizerReady = synchronized(lock) { recognizer != null }
            return if (recognizerReady) {
                Result.success(lastResultText)
            } else {
                val error = SherpaEngineError("SherpaEngine has not been initialized.")
                synchronized(lock) {
                    lastError = error
                }
                Result.failure(IllegalStateException(error.message))
            }
        }

        val shouldDecode = synchronized(lock) { hasPendingAudio || lastResultText.isEmpty() }
        if (!shouldDecode) {
            return Result.success(lastResultText)
        }

        val result = runCatching {
            withContext(dispatcher) {
                val recognizerHandle = synchronized(lock) { recognizer }
                    ?: throw IllegalStateException("SherpaEngine has not been initialized.")
                if (!recognizerHandle.isReady(currentStream)) {
                    return@withContext lastResultText
                }
                recognizerHandle.decode(currentStream)
                recognizerHandle.getResult(currentStream).text.orEmpty()
            }
        }

        return result
            .onSuccess { text ->
                synchronized(lock) {
                    lastResultText = text
                    hasPendingAudio = false
                    lastError = null
                }
            }
            .recoverCatching { throwable ->
                synchronized(lock) {
                    lastError = throwable.toSherpaEngineError()
                }
                throw throwable
            }
    }

    override suspend fun stop(): Result<String> {
        val currentStreamAndDecode = synchronized(lock) {
            val activeStream = stream ?: return Result.success(lastResultText)
            stream = null
            val shouldDecode = hasPendingAudio || lastResultText.isEmpty()
            hasPendingAudio = false
            activeStream to shouldDecode
        }

        val result = runCatching {
            val (currentStream, shouldDecode) = currentStreamAndDecode
            if (!shouldDecode) {
                lastResultText
            } else {
                (currentStream as? OfflineSherpaStreamHandle)?.finishInput()
                withContext(dispatcher) {
                    val recognizerHandle = synchronized(lock) { recognizer }
                        ?: throw IllegalStateException("SherpaEngine has not been initialized.")
                    var text = lastResultText
                    while (recognizerHandle.isReady(currentStream)) {
                        recognizerHandle.decode(currentStream)
                        text = recognizerHandle.getResult(currentStream).text.orEmpty()
                    }
                    text
                }
            }
        }

        currentStreamAndDecode.first.close()

        return result
            .onSuccess { text ->
                synchronized(lock) {
                    lastResultText = text
                    lastError = null
                }
            }
            .recoverCatching { throwable ->
                synchronized(lock) {
                    lastError = throwable.toSherpaEngineError()
                }
                throw throwable
            }
    }

    override fun close() {
        val currentStream = synchronized(lock) {
            val activeStream = stream
            stream = null
            val currentRecognizer = recognizer
            recognizer = null
            hasPendingAudio = false
            lastError = null
            activeStream to currentRecognizer
        }

        currentStream.first?.close()
        currentStream.second?.close()
    }
}

private class AndroidSherpaRecognizerLoader(
    private val assetManager: AssetManager,
    private val assets: SherpaModelAssets,
    private val config: SherpaEngineConfig,
) : SherpaRecognizerLoader {
    override fun createRecognizer(): SherpaRecognizerHandle {
        validateAssets(assetManager, assets.requiredPaths)
        val recognizerConfig = buildRecognizerConfig(assets, config)
        return OfflineSherpaRecognizerHandle(
            recognizer = OnlineRecognizer(assetManager, recognizerConfig),
        )
    }
}

private class OfflineSherpaRecognizerHandle(
    private val recognizer: OnlineRecognizer,
) : SherpaRecognizerHandle {
    override fun createStream(): SherpaStreamHandle {
        return OfflineSherpaStreamHandle(recognizer.createStream())
    }

    override fun decode(stream: SherpaStreamHandle) {
        require(stream is OfflineSherpaStreamHandle) {
            "Stream handle was created by a different backend."
        }
        recognizer.decode(stream.stream)
    }

    override fun isReady(stream: SherpaStreamHandle): Boolean {
        require(stream is OfflineSherpaStreamHandle) {
            "Stream handle was created by a different backend."
        }
        return recognizer.isReady(stream.stream)
    }

    override fun getResult(stream: SherpaStreamHandle): OnlineRecognizerResult {
        require(stream is OfflineSherpaStreamHandle) {
            "Stream handle was created by a different backend."
        }
        return recognizer.getResult(stream.stream)
    }

    override fun close() {
        recognizer.release()
    }
}

private class OfflineSherpaStreamHandle(
    val stream: OnlineStream,
) : SherpaStreamHandle {
    override fun acceptWaveform(samples: FloatArray, sampleRateHz: Int) {
        stream.acceptWaveform(samples, sampleRateHz)
    }

    fun finishInput() {
        stream.inputFinished()
    }

    override fun close() {
        stream.release()
    }
}

private fun buildRecognizerConfig(
    assets: SherpaModelAssets,
    engineConfig: SherpaEngineConfig,
): OnlineRecognizerConfig {
    return OnlineRecognizerConfig().apply {
        featConfig = FeatureConfig().apply {
            sampleRate = engineConfig.sampleRateHz
            featureDim = engineConfig.featureDim
            dither = 0.0f
        }
        modelConfig = OnlineModelConfig().apply {
            transducer = OnlineTransducerModelConfig(
                assets.encoder,
                assets.decoder,
                assets.joiner.orEmpty(),
            )
            numThreads = engineConfig.numThreads
            debug = false
            provider = engineConfig.provider
            modelType = engineConfig.modelType
            tokens = assets.tokens
        }
        hr = HomophoneReplacerConfig()
        lmConfig = OnlineLMConfig()
        ctcFstDecoderConfig = OnlineCtcFstDecoderConfig()
        endpointConfig = EndpointConfig()
        enableEndpoint = true
        decodingMethod = engineConfig.decodingMethod
        maxActivePaths = engineConfig.maxActivePaths
        hotwordsFile = ""
        hotwordsScore = engineConfig.hotwordsScore
        ruleFsts = ""
        ruleFars = ""
        blankPenalty = engineConfig.blankPenalty
    }
}

private fun validateAssets(assetManager: AssetManager, requiredPaths: List<String>) {
    requiredPaths.forEach { path ->
        try {
            assetManager.open(path).use { }
        } catch (throwable: Throwable) {
            throw IllegalStateException("Missing Sherpa model asset: $path", throwable)
        }
    }
}

private fun ShortArray.toFloatSamples(): FloatArray {
    val floatSamples = FloatArray(size)
    for (index in indices) {
        floatSamples[index] = this[index] / Short.MAX_VALUE.toFloat()
    }
    return floatSamples
}

private fun ByteArray.toFloatSamples(): FloatArray {
    if (isEmpty()) {
        return FloatArray(0)
    }

    val sampleCount = size / Short.SIZE_BYTES
    val floatSamples = FloatArray(sampleCount)
    var byteIndex = 0
    var sampleIndex = 0
    while (byteIndex + 1 < size) {
        val sample = (this[byteIndex + 1].toInt() shl 8) or (this[byteIndex].toInt() and 0xFF)
        floatSamples[sampleIndex] = sample.toShort() / Short.MAX_VALUE.toFloat()
        byteIndex += Short.SIZE_BYTES
        sampleIndex += 1
    }
    return floatSamples
}

private fun Throwable.toSherpaEngineError(): SherpaEngineError {
    val message = message ?: this::class.java.simpleName
    return SherpaEngineError(
        message = message,
        causeMessage = cause?.message ?: this::class.java.name,
    )
}
