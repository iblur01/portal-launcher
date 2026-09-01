package com.iblu01.portallauncher.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * openWakeWord's streaming feature pipeline, straight on ONNX Runtime.
 *
 * Mirrors `openwakeword.utils.AudioFeatures._streaming_features` from the Python reference:
 * every 1280-sample chunk (80 ms at 16 kHz) is turned into mel frames together with a 480-sample
 * tail of the previous chunk, the mel frames feed a 76-frame window into the embedding model, and
 * the last 16 embeddings go through the wake-word classifier.
 *
 * Exists because the bundled Kotlin library ran the same three models at 230-360 ms per chunk on
 * the wall panel (boxed `ArrayDeque<Float>`, per-frame `float[][][]` copies, the garbage
 * collector pausing for ~400 ms every second) while the models alone take ~40 ms there. It also
 * fed the mel model normalised -1..1 audio where the reference feeds int16-range values; a
 * log-mel front end is not indifferent to a 90 dB offset. Here every buffer is allocated once,
 * the tensors wrap direct buffers that are rewritten in place, and the maths follows the
 * reference.
 *
 * Not thread-safe: one instance per capture loop.
 */
class OpenWakeWordPipeline(context: Context, classifierAsset: String) : AutoCloseable {

    companion object {
        const val CHUNK = 1280
        const val MEL_BINS = 32
        private const val MEL_TAIL = 480 // 3 hops of context so frame boundaries line up
        private const val MEL_WINDOW = 76 // 760 ms of mel frames per embedding
        private const val MEL_RING = 256 // frames kept; only the last window + strides matter
        private const val EMBEDDING = 96
        private const val FEATURES = 16 // embeddings the classifier looks at (1.28 s)
        private const val INT16_SCALE = 32768f
    }

    private val env = OrtEnvironment.getEnvironment()

    // One thread: the panel usually has a single core online, and ORT's intra-op pool made every
    // model slower there, not faster.
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(1)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }

    private val melModel = load(context, "melspectrogram.onnx")
    private val embeddingModel = load(context, "embedding_model.onnx")
    private val classifier = load(context, classifierAsset)

    private val melInput = directFloats(CHUNK + MEL_TAIL)
    private val melTensor = OnnxTensor.createTensor(env, melInput, longArrayOf(1, (CHUNK + MEL_TAIL).toLong()))
    private val embeddingInput = directFloats(MEL_WINDOW * MEL_BINS)
    private val embeddingTensor =
        OnnxTensor.createTensor(env, embeddingInput, longArrayOf(1, MEL_WINDOW.toLong(), MEL_BINS.toLong(), 1))
    private val classifierInput = directFloats(FEATURES * EMBEDDING)
    private val classifierTensor =
        OnnxTensor.createTensor(env, classifierInput, longArrayOf(1, FEATURES.toLong(), EMBEDDING.toLong()))

    private val melInputName = melModel.inputNames.first()
    private val embeddingInputName = embeddingModel.inputNames.first()
    private val classifierInputName = classifier.inputNames.first()

    /** Tail of the previous chunk, prepended so the STFT sees continuous audio across chunks. */
    private val rawTail = FloatArray(MEL_TAIL)

    /** Samples not yet forming a full chunk (the recorder normally delivers exact chunks). */
    private val pending = FloatArray(CHUNK)
    private var pendingCount = 0

    /** Ring of mel frames, row-major [frame][bin]. */
    private val melRing = FloatArray(MEL_RING * MEL_BINS)
    private var melWritten = 0L

    /** Sliding window of the last [FEATURES] embeddings, oldest first. */
    private val features = FloatArray(FEATURES * EMBEDDING)

    private var lastScore = 0f

    init {
        // The reference seeds its feature buffer with embeddings of silence; without that the
        // classifier's first second runs on zeros, which is not a state it was trained on.
        val silence = FloatArray(CHUNK)
        repeat(FEATURES) { process(silence) }
        lastScore = 0f
    }

    /**
     * Feeds normalised (-1..1) 16 kHz mono samples. Returns the classifier's latest score; the
     * value only changes once a full 80 ms chunk has been accumulated.
     */
    fun process(samples: FloatArray): Float {
        var offset = 0
        while (offset < samples.size) {
            val take = minOf(CHUNK - pendingCount, samples.size - offset)
            System.arraycopy(samples, offset, pending, pendingCount, take)
            pendingCount += take
            offset += take
            if (pendingCount == CHUNK) {
                processChunk(pending)
                pendingCount = 0
            }
        }
        return lastScore
    }

    private fun processChunk(chunk: FloatArray) {
        // --- mel: previous tail + this chunk, in int16 range like the reference ---------------
        melInput.clear()
        for (v in rawTail) melInput.put(v * INT16_SCALE)
        for (v in chunk) melInput.put(v * INT16_SCALE)
        System.arraycopy(chunk, CHUNK - MEL_TAIL, rawTail, 0, MEL_TAIL)

        melModel.run(mapOf(melInputName to melTensor)).use { result ->
            val out = result[0] as OnnxTensor
            val frames = out.info.shape.let { it[it.size - 2] }.toInt()
            val data = out.floatBuffer
            for (f in 0 until frames) {
                val row = ((melWritten % MEL_RING) * MEL_BINS).toInt()
                for (b in 0 until MEL_BINS) {
                    // Reference transform: spec / 10 + 2
                    melRing[row + b] = data.get(f * MEL_BINS + b) / 10f + 2f
                }
                melWritten++
            }
        }
        if (melWritten < MEL_WINDOW) return

        // --- embedding of the newest 76-frame window ------------------------------------------
        embeddingInput.clear()
        val first = melWritten - MEL_WINDOW
        for (f in 0 until MEL_WINDOW) {
            val row = (((first + f) % MEL_RING) * MEL_BINS).toInt()
            embeddingInput.put(melRing, row, MEL_BINS)
        }
        embeddingModel.run(mapOf(embeddingInputName to embeddingTensor)).use { result ->
            val out = (result[0] as OnnxTensor).floatBuffer
            System.arraycopy(features, EMBEDDING, features, 0, (FEATURES - 1) * EMBEDDING)
            out.get(features, (FEATURES - 1) * EMBEDDING, EMBEDDING)
        }

        // --- classifier over the last 16 embeddings --------------------------------------------
        classifierInput.clear()
        classifierInput.put(features)
        classifier.run(mapOf(classifierInputName to classifierTensor)).use { result ->
            lastScore = (result[0] as OnnxTensor).floatBuffer.get(0)
        }
    }

    override fun close() {
        melTensor.close()
        embeddingTensor.close()
        classifierTensor.close()
        melModel.close()
        embeddingModel.close()
        classifier.close()
        options.close()
    }

    private fun load(context: Context, asset: String): OrtSession =
        context.assets.open(asset).use { env.createSession(it.readBytes(), options) }

    private fun directFloats(size: Int): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
}
