package org.futo.voiceinput.shared.util

import org.futo.pocketfft.PocketFFT
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

fun createHannWindow(nFFT: Int): DoubleArray {
    val window = DoubleArray(nFFT)

    // Create a Hann window for even nFFT.
    // The Hann window is a taper formed by using a raised cosine or sine-squared
    // with ends that touch zero.
    for (i in 0 until nFFT) {
        window[i] = 0.5 - 0.5 * cos(2.0 * Math.PI * i / nFFT)
    }

    return window
}

enum class MelScale {
    Htk, Slaney
}

enum class Normalization {
    None, Slaney
}


fun melToFreq(mel: Double, melScale: MelScale): Double {
    if (melScale == MelScale.Htk) {
        return 700.0 * (10.0.pow((mel / 2595.0)) - 1.0)
    }

    val minLogHertz = 1000.0
    val minLogMel = 15.0
    val logstep = ln(6.4) / 27.0
    var freq = 200.0 * mel / 3.0

    if (mel >= minLogMel) {
        freq = minLogHertz * exp(logstep * (mel - minLogMel))
    }

    return freq
}

fun freqToMel(freq: Double, melScale: MelScale): Double {
    if (melScale == MelScale.Htk) {
        return 2595.0 * log10(1.0 + (freq / 700.0))
    }

    val minLogHertz = 1000.0
    val minLogMel = 15.0
    val logstep = 27.0 / ln(6.4)
    var mels = 3.0 * freq / 200.0

    if (freq >= minLogHertz) {
        mels = minLogMel + ln(freq / minLogHertz) * logstep
    }

    return mels
}

fun melToFreq(mels: DoubleArray, melScale: MelScale): DoubleArray {
    return mels.map { melToFreq(it, melScale) }.toDoubleArray()
}

fun linspace(min: Double, max: Double, num: Int): DoubleArray {
    val array = DoubleArray(num)
    val spacing = (max - min) / ((num - 1).toDouble())

    for (i in 0 until num) {
        array[i] = spacing * i
    }

    return array
}

fun diff(array: DoubleArray, n: Int = 1): DoubleArray {
    if (n != 1) {
        TODO()
    }

    val newArray = DoubleArray(array.size - 1)
    for (i in 0 until (array.size - 1)) {
        newArray[i] = array[i + 1] - array[i]
    }

    return newArray
}

fun createTriangularFilterBank(
    fftFreqs: DoubleArray,
    filterFreqs: DoubleArray
): Array<DoubleArray> {
    val filterDiff = diff(filterFreqs)

    val slopes = Array(fftFreqs.size) { i ->
        DoubleArray(filterFreqs.size) { j ->
            filterFreqs[j] - fftFreqs[i]
        }
    }

    val downSlopes = Array(fftFreqs.size) { i ->
        DoubleArray(filterFreqs.size - 2) { j ->
            -slopes[i][j] / filterDiff[j]
        }
    }

    val upSlopes = Array(fftFreqs.size) { i ->
        DoubleArray(filterFreqs.size - 2) { j ->
            slopes[i][2 + j] / filterDiff[1 + j]
        }
    }

    val result = Array(fftFreqs.size) { i ->
        DoubleArray(filterFreqs.size - 2) { j ->
            max(0.0, min(downSlopes[i][j], upSlopes[i][j]))
        }
    }

    return result
}

fun melFilterBank(
    numFrequencyBins: Int,
    numMelFilters: Int,
    minFrequency: Double,
    maxFrequency: Double,
    samplingRate: Int,
    norm: Normalization,
    melScale: MelScale
): Array<DoubleArray> {
    val fftFreqs = linspace(0.0, (samplingRate / 2).toDouble(), numFrequencyBins)

    val melMin = freqToMel(minFrequency, melScale = melScale)
    val melMax = freqToMel(maxFrequency, melScale = melScale)

    val melFreqs = linspace(melMin, melMax, numMelFilters + 2)
    val filterFreqs = melToFreq(melFreqs, melScale = melScale)

    val melFilters = createTriangularFilterBank(fftFreqs, filterFreqs)

    if (norm == Normalization.Slaney) {
        val enorm = DoubleArray(numMelFilters) { i ->
            2.0 / (filterFreqs[i + 2] - filterFreqs[i])
        }

        for (i in 0 until numFrequencyBins) {
            for (j in 0 until numMelFilters) {
                melFilters[i][j] *= enorm[j]
            }
        }
    }

    return melFilters
}

/*
 * This function pads the y values
 */
fun padY(yValues: DoubleArray, nFFT: Int): DoubleArray {
    val ypad = DoubleArray(nFFT + yValues.size)
    for (i in 0 until nFFT / 2) {
        ypad[nFFT / 2 - i - 1] = yValues[i + 1]
        ypad[nFFT / 2 + yValues.size + i] = yValues[yValues.size - 2 - i]
    }
    for (j in yValues.indices) {
        ypad[nFFT / 2 + j] = yValues[j]
    }
    return ypad
}

/**
 * This Class calculates the MFCC, STFT values of given audio samples.
 * Source based on [MFCC.java](https://github.com/chiachunfu/speech/blob/master/speechandroid/src/org/tensorflow/demo/mfcc/MFCC.java)
 *
 * @author abhi-rawat1
 */
class AudioFeatureExtraction(
    val featureSize: Int,
    val samplingRate: Int,
    val hopLength: Int,
    val chunkLength: Int,
    val nFFT: Int,
    val paddingValue: Double
) {
    private val numSamples = chunkLength * samplingRate
    private val nbMaxFrames = numSamples / hopLength
    private val melFilters = melFilterBank(
        numFrequencyBins = 1 + (nFFT / 2),
        numMelFilters = featureSize,
        minFrequency = 0.0,
        maxFrequency = 8000.0,
        samplingRate = samplingRate,
        norm = Normalization.Slaney,
        melScale = MelScale.Slaney
    ).transpose()
    private val window = createHannWindow(nFFT)

    private val fft = PocketFFT(nFFT)


    /**
     * This function converts input audio samples to 1x80x3000 features
     */
    fun melSpectrogram(y: DoubleArray): FloatArray {
        val paddedWaveform = DoubleArray(min(numSamples, y.size + hopLength).coerceAtLeast(nFFT)) {
            if (it < y.size) {
                y[it]
            } else {
                paddingValue
            }
        }

        val spectro = extractSTFTFeatures(paddedWaveform)

        val yShape = nbMaxFrames + 1
        val yShapeMax = spectro[0].size

        assert(melFilters[0].size == spectro.size)
        val melS = Array(melFilters.size) { DoubleArray(yShape) }
        for (i in melFilters.indices) {
            // j > yShapeMax would all be 0.0
            for (j in 0 until yShapeMax) {
                for (k in melFilters[0].indices) {
                    melS[i][j] += melFilters[i][k] * spectro[k][j]
                }
            }
        }

        for (i in melS.indices) {
            for (j in melS[0].indices) {
                melS[i][j] = log10(max(1e-10, melS[i][j]))
            }
        }

        val logSpec = Array(melS.size) { i ->
            DoubleArray(melS[0].size - 1) { j ->
                melS[i][j]
            }
        }

        val maxValue = logSpec.maxOf { it.max() }
        for (i in logSpec.indices) {
            for (j in logSpec[0].indices) {
                logSpec[i][j] = max(logSpec[i][j], maxValue - 8.0)
                logSpec[i][j] = (logSpec[i][j] + 4.0) / 4.0
            }
        }

        val mel = FloatArray(1 * 80 * 3000)
        for (i in logSpec.indices) {
            for (j in logSpec[0].indices) {
                mel[i * 3000 + j] = logSpec[i][j].toFloat()
            }
        }

        return mel
    }


    /**
     * This function extract STFT values from given Audio Magnitude Values.
     *
     */
    private fun extractSTFTFeatures(y: DoubleArray): Array<DoubleArray> {

        // pad y with reflect mode so it's centered
        val yPad = padY(y, nFFT)

        val numFrames = 1 + ((yPad.size - nFFT) / hopLength)

        val numFrequencyBins = (nFFT / 2) + 1
        val fftmagSpec = Array(numFrequencyBins) { DoubleArray(numFrames) }
        val fftFrame = DoubleArray(nFFT)

        var timestep = 0

        val magSpec = DoubleArray(numFrequencyBins)
        val complx = DoubleArray(nFFT + 1)
        for (k in 0 until numFrames) {
            for (l in 0 until nFFT) {
                fftFrame[l] = yPad[timestep + l] * window[l]
            }

            timestep += hopLength

            try {
                fft.forward(fftFrame, complx)

                for (i in 0 until numFrequencyBins) {
                    val rr = complx[i * 2]

                    val ri = if (i == (numFrequencyBins - 1)) {
                        0.0
                    } else {
                        complx[i * 2 + 1]
                    }

                    magSpec[i] = (rr * rr + ri * ri)
                }
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
            for (i in 0 until numFrequencyBins) {
                fftmagSpec[i][k] = magSpec[i]
            }
        }

        return fftmagSpec
    }
}