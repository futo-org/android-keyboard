package org.futo.ml.inference

import java.io.File
import java.util.Locale

/**
 * Swipe keyboard pipeline handle: universal encoder + optional paired decoder
 * + optional context LM, plus CTC beam search.
 *
 * The encoder is layout-agnostic and works for any language on its own. When
 * a paired decoder is loaded (via the [decoderPath] ctor arg or [setMode]),
 * its refined log-probs are used instead of the encoder's direct emissions.
 * Decoders are per-layout and may be swapped without reloading the encoder.
 */
class SwipeDecoder(
    encoderPath: String,
    decoderPath: String? = null,
    threads: Int = 1,
    beamWidth: Int = 100,
    topK: Int = 4,
    useExpansion: Boolean = true,
    freqKey: String = "f",
    lmModelPath: String? = null,
    lmVocabPath: String? = null,
    lmAlpha: Float = 0.0f,
) : AutoCloseable {

    data class Result(val word: String, val score: Float, val ctcScore: Float = 0f, val lmScore: Float = 0f)

    /** Per-stage timing (microseconds) from the most recent recognize() call. */
    data class Timing(
        val resampleUs: Float = 0f,
        val encoderUs: Float = 0f,
        val decoderUs: Float = 0f,
        val beamUs: Float = 0f,
        val lmUs: Float = 0f,
        val totalUs: Float = 0f
    )

    private var handle: Long
    private val contextWords = mutableListOf<String>()

    init {
        handle = nativeInit(
            encoderPath, decoderPath,
            threads, beamWidth, topK,
            useExpansion, freqKey,
            lmModelPath, lmVocabPath, lmAlpha
        )
        require(handle != 0L) { "Failed to initialize SwipeDecoder from $encoderPath" }
    }

    /** Add a word to the context (e.g. after the user accepts a suggestion). */
    fun addContext(word: String) {
        if (word.isEmpty()) return
        contextWords.add(word.lowercase(Locale.ROOT))
    }

    /** Replace the entire context. */
    fun setContext(words: List<String>) {
        contextWords.clear()
        words.forEach { if (it.isNotEmpty()) contextWords.add(it.lowercase(Locale.ROOT)) }
    }

    /** Clear all context. */
    fun clearContext() {
        contextWords.clear()
    }

    /**
     * Recognize a swipe path as top-k word candidates.
     *
     * [t] is per-point timestamps in milliseconds. Required: the models are
     * trained on 60 Hz temporally-resampled paths, so timestamps drive the
     * resampling step. Must be the same length as [x] and [y].
     */
    fun recognize(
        x: FloatArray,
        y: FloatArray,
        t: FloatArray,
        topK: Int = 4,
        beamWidth: Int = 100,
        trieWeights: FloatArray? = null,
    ): List<Result> {
        check(handle != 0L) { "SwipeDecoder has been closed" }
        pinCoresOnce()
        require(x.isNotEmpty()) { "x array must not be empty" }
        require(x.size == y.size) { "x and y arrays must have the same size (${x.size} != ${y.size})" }
        require(t.size == x.size) { "t array size must match x/y (${t.size} != ${x.size})" }
        require(topK > 0) { "topK must be positive, got $topK" }
        val ctx = if (contextWords.isNotEmpty()) contextWords.toTypedArray() else null
        val results = nativeRecognize(handle, x, y, t, topK, ctx, beamWidth, trieWeights)
        return results?.toList() ?: emptyList()
    }

    /**
     * Predict the top-k most likely next words given the current context.
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
            encoderUs = arr[1],
            decoderUs = arr[2],
            beamUs = arr[3],
            lmUs = arr[4],
            totalUs = arr[5]
        )
    }

    /** True iff a paired decoder is currently loaded (vs. encoder-only). */
    fun hasDecoder(): Boolean {
        check(handle != 0L) { "SwipeDecoder has been closed" }
        return nativeHasDecoder(handle)
    }

    /** True iff a context LM is currently loaded. */
    fun hasLm(): Boolean {
        check(handle != 0L) { "SwipeDecoder has been closed" }
        return nativeHasLm(handle)
    }

    /**
     * Atomically apply any subset of layout / vocab / decoder / LM changes.
     *
     * Semantics per argument:
     * - `null` = keep current slot unchanged
     * - empty string for `decoderPath` / `lmModelPath` = unload that slot
     * - any other string = set or load (no-op if already matches current)
     *
     * Layout changes require all three of [letters], [cx], [cy] together;
     * passing any subset is treated as no layout change. Loads are staged
     * in scratch so a failure leaves prior state intact and returns false.
     *
     * @param lmAlpha LM rerank weight; `Float.NaN` = keep current
     */
    fun setMode(
        letters: String? = null,
        cx: FloatArray? = null,
        cy: FloatArray? = null,
        tries: LongArray? = null,
        decoderPath: String? = null,
        lmModelPath: String? = null,
        lmVocabPath: String? = null,
        lmAlpha: Float = Float.NaN,
    ): Boolean {
        check(handle != 0L) { "SwipeDecoder has been closed" }
        if (letters != null || cx != null || cy != null) {
            require(letters != null && cx != null && cy != null) {
                "setMode: letters, cx, cy must be provided together"
            }
            require(cx.size == cy.size) {
                "setMode: cx and cy must have same size (${cx.size} != ${cy.size})"
            }
            require(letters.codePointCount(0, letters.length) == cx.size) {
                "setMode: letters codepoint count (${letters.codePointCount(0, letters.length)}) must match cx/cy size (${cx.size})"
            }
        }

        return nativeSetMode(
            handle, letters, cx, cy,
            tries, decoderPath,
            lmModelPath, lmVocabPath, lmAlpha
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
        encoderPath: String, decoderPath: String?,
        threads: Int, beamWidth: Int, topK: Int,
        useExpansion: Boolean, freqKey: String,
        lmModelPath: String?, lmVocabPath: String?,
        lmAlpha: Float,
    ): Long

    private external fun nativeRecognize(
        handle: Long, x: FloatArray, y: FloatArray,
        t: FloatArray?, topK: Int, context: Array<String>?,
        beamWidth: Int, trieWeights: FloatArray?
    ): Array<Result>?

    private external fun nativePredictNext(
        handle: Long, context: Array<String>?, topK: Int
    ): Array<Result>?

    private external fun nativeGetLastTiming(handle: Long): FloatArray?

    private external fun nativeHasDecoder(handle: Long): Boolean

    private external fun nativeHasLm(handle: Long): Boolean

    private external fun nativeSetMode(
        handle: Long,
        letters: String?, cx: FloatArray?, cy: FloatArray?,
        tries: LongArray?,
        decoderPath: String?,
        lmModelPath: String?, lmVocabPath: String?,
        lmAlpha: Float
    ): Boolean

    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("swipe_jni")
        }

        /** Default core affinity. Set before creating any SwipeDecoder instance. */
        @JvmStatic
        var defaultAffinity: String = "little"

        private val pinnedThreads = java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<Long, Boolean>()
        )

        internal fun pinCoresOnce() {
            //val tid = Thread.currentThread().id
            //if (pinnedThreads.add(tid) && defaultAffinity.isNotEmpty()) {
            //    pinCores(defaultAffinity)
            //}
        }

        /**
         * Pin the calling thread to big or little CPU cores.
         *
         * Call this on your inference thread before [recognize] to avoid
         * big.LITTLE core migration, which causes P99 latency spikes.
         * "little" uses less power; "big" gives lowest latency.
         *
         * @param affinity "big" or "little"
         * @return number of cores pinned, or 0 on failure
         */
        @JvmStatic
        fun pinCores(affinity: String): Int = nativePinCores(affinity)

        @JvmStatic
        private external fun nativePinCores(affinity: String): Int

        /**
         * Install a Unicode tolower table for the given vocab.
         *
         * Scans the vocab bytes once, collects distinct non-ASCII BMP
         * codepoints, folds each via Character.toLowerCase, and installs
         * the sorted (from, to) pairs into the native side. Costs ~10-25 ms
         * on Pixel 4 for the largest shipped vocabs.
         *
         * The installed table is process-global and is refreshed by [init]
         * and by [setMode] whenever the vocab path changes.
         */
        @JvmStatic
        fun installCaseMappingFor(vocabPath: String) {
            val bytes = File(vocabPath).readBytes()
            val seen = BooleanArray(0x10000)
            var i = 0
            val n = bytes.size
            while (i < n) {
                val b = bytes[i].toInt() and 0xFF
                if (b < 0x80) { i++; continue }
                val extra = when {
                    (b and 0xE0) == 0xC0 -> 1
                    (b and 0xF0) == 0xE0 -> 2
                    (b and 0xF8) == 0xF0 -> 3
                    else -> { i++; continue }
                }
                if (i + extra >= n) break
                var cp = b and (0xFF ushr (extra + 1))
                var ok = true
                for (k in 1..extra) {
                    val c = bytes[i + k].toInt() and 0xFF
                    if ((c and 0xC0) != 0x80) { ok = false; break }
                    cp = (cp shl 6) or (c and 0x3F)
                }
                if (ok && cp < 0x10000) seen[cp] = true
                i += extra + 1
            }
            var count = 0
            for (cp in 0x80 until 0x10000) {
                if (seen[cp] && Character.toLowerCase(cp) != cp) count++
            }
            val from = IntArray(count)
            val to = IntArray(count)
            var idx = 0
            for (cp in 0x80 until 0x10000) {
                if (!seen[cp]) continue
                val lo = Character.toLowerCase(cp)
                if (lo != cp) {
                    from[idx] = cp
                    to[idx] = lo
                    idx++
                }
            }
            nativeSetCaseMapping(from, to)
        }

        @JvmStatic
        private external fun nativeSetCaseMapping(from: IntArray, to: IntArray)
    }
}
