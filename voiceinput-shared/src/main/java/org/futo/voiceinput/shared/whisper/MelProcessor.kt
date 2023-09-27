package org.futo.voiceinput.shared.whisper

import org.futo.voiceinput.shared.util.AudioFeatureExtraction

private val extractor = AudioFeatureExtraction(
    chunkLength = 30,
    featureSize = 80,
    hopLength = 160,
    nFFT = 400,
    paddingValue = 0.0,
    samplingRate = 16000
)

fun extractMelSpectrogramForWhisper(samples: DoubleArray): FloatArray {
    return extractor.melSpectrogram(samples)
}