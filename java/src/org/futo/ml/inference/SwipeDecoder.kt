package org.futo.ml.inference


private var libraryLoaded = false

/**
 * Swipe keyboard decoder backed by ExecuTorch + XNNPACK.
 *
 * Wraps the native C++ SwipeEngine: resampling + model inference + beam search.
 * Optionally uses a context language model for post-beam-search reranking.
 */
class SwipeDecoder(
    modelPath: String,
    vocabPath: String,
    threads: Int = 1,
    beamWidth: Int = 100,
    freqBonus: Float = 0.0301f,
    lengthBonus: Float = 1.6535f,
    useExpansion: Boolean = false,
    freqKey: String = "f",
    lmModelPath: String? = null,
    lmVocabPath: String? = null,
    lmAlpha: Float = 0.0f
) : AutoCloseable {
    private val ensureLoaded: Boolean = run {
        if(!libraryLoaded) {
            System.loadLibrary("swipe_jni")
            libraryLoaded = true
        }
        libraryLoaded
    }

    data class Result(val word: String, val score: Float, val ctcScore: Float = 0f, val lmScore: Float = 0f)

    /** Per-stage timing (microseconds) from the most recent recognize() call. */
    data class Timing(
        val resampleUs: Float = 0f,
        val modelUs: Float = 0f,
        val beamUs: Float = 0f,
        val lmUs: Float = 0f,
        val totalUs: Float = 0f
    )

    private var handle: Long = nativeInit(
        modelPath, vocabPath, threads, beamWidth, freqBonus, lengthBonus, useExpansion, freqKey,
        lmModelPath, lmVocabPath, lmAlpha
    )

    // Context words for LM reranking
    private val contextWords = mutableListOf<String>()

    init {
        require(handle != 0L) { "Failed to initialize SwipeDecoder from $modelPath" }
    }

    /** Add a word to the context (e.g. after the user accepts a suggestion). */
    fun addContext(word: String) {
        if (word.isEmpty()) return
        contextWords.add(word)
    }

    /** Replace the entire context. */
    fun setContext(words: List<String>) {
        contextWords.clear()
        contextWords.addAll(words)
    }

    /** Clear all context. */
    fun clearContext() {
        contextWords.clear()
    }

    /**
     * Recognize a swipe path as top-k word candidates.
     *
     * @param x X coordinates (normalized 0-1)
     * @param y Y coordinates (normalized 0-1)
     * @param t Timestamps in milliseconds (can be null for index-based interpolation)
     * @param topK Maximum number of results
     * @return List of word candidates with scores, sorted by score descending
     */
    fun recognize(
        x: FloatArray,
        y: FloatArray,
        t: FloatArray? = null,
        topK: Int = 4
    ): List<Result> {
        check(handle != 0L) { "SwipeDecoder has been closed" }
        require(x.isNotEmpty()) { "x array must not be empty" }
        require(x.size == y.size) { "x and y arrays must have the same size (${x.size} != ${y.size})" }
        if (t != null) require(t.size == x.size) { "t array size must match x/y (${t.size} != ${x.size})" }
        require(topK > 0) { "topK must be positive, got $topK" }
        val ctx = if (contextWords.isNotEmpty()) contextWords.toTypedArray() else null
        val results = nativeRecognize(handle, x, y, t, topK, ctx)
        return results?.toList() ?: emptyList()
    }

    /**
     * Predict the top-k most likely next words given the current context.
     *
     * Requires the context LM to be loaded (lmModelPath/lmVocabPath at init).
     * Returns an empty list if the LM is not available.
     *
     * @param topK Maximum number of predictions
     * @return List of word predictions with scores, sorted by score descending
     */
    fun predictNext(topK: Int = 10): List<Result> {
        check(handle != 0L) { "SwipeDecoder has been closed" }
        require(topK > 0) { "topK must be positive, got $topK" }
        val ctx = if (contextWords.isNotEmpty()) contextWords.toTypedArray() else null
        val results = nativePredictNext(handle, ctx, topK)
        return results?.toList() ?: emptyList()
    }

    /** Per-stage timing from the most recent recognize() call. */
    fun lastTiming(): Timing {
        check(handle != 0L) { "SwipeDecoder has been closed" }
        val arr = nativeGetLastTiming(handle) ?: return Timing()
        return Timing(
            resampleUs = arr[0],
            modelUs = arr[1],
            beamUs = arr[2],
            lmUs = arr[3],
            totalUs = arr[4]
        )
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0
        }
    }

    // Native methods
    private external fun nativeInit(
        modelPath: String, vocabPath: String,
        threads: Int, beamWidth: Int,
        freqBonus: Float, lengthBonus: Float,
        useExpansion: Boolean, freqKey: String,
        lmModelPath: String?, lmVocabPath: String?,
        lmAlpha: Float
    ): Long

    private external fun nativeRecognize(
        handle: Long, x: FloatArray, y: FloatArray,
        t: FloatArray?, topK: Int, context: Array<String>?
    ): Array<Result>?

    private external fun nativePredictNext(
        handle: Long, context: Array<String>?, topK: Int
    ): Array<Result>?

    private external fun nativeGetLastTiming(handle: Long): FloatArray?

    private external fun nativeDestroy(handle: Long)
}
