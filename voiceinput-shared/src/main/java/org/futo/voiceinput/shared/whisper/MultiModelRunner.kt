package org.futo.voiceinput.shared.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.util.toDoubleArray


data class MultiModelRunConfiguration(
    val primaryModel: ModelLoader, val languageSpecificModels: Map<Language, ModelLoader>
)

data class DecodingConfiguration(
    val languages: Set<Language>, val suppressSymbols: Boolean
)

class MultiModelRunner(
    private val modelManager: ModelManager
) {
    suspend fun preload(runConfiguration: MultiModelRunConfiguration) = coroutineScope {
        val jobs = mutableListOf<Job>()

        jobs.add(launch(Dispatchers.Default) {
            modelManager.obtainModel(runConfiguration.primaryModel)
        })

        if (runConfiguration.languageSpecificModels.count() < 2) {
            runConfiguration.languageSpecificModels.forEach {
                jobs.add(launch(Dispatchers.Default) {
                    modelManager.obtainModel(it.value)
                })
            }
        }

        jobs.forEach { it.join() }
    }

    suspend fun run(
        samples: FloatArray,
        runConfiguration: MultiModelRunConfiguration,
        decodingConfiguration: DecodingConfiguration,
        callback: ModelInferenceCallback
    ): String = coroutineScope {
        callback.updateStatus(InferenceState.ExtractingMel)
        val mel = extractMelSpectrogramForWhisper(samples.toDoubleArray())
        yield()

        callback.updateStatus(InferenceState.LoadingModel)
        val primaryModel = modelManager.obtainModel(runConfiguration.primaryModel)
        val session = primaryModel.startInferenceSession(decodingConfiguration)
        yield()

        callback.updateStatus(InferenceState.Encoding)
        session.melToFeatures(mel)
        yield()

        callback.updateStatus(InferenceState.DecodingLanguage)
        val metadata = session.decodeMetadata()
        yield()

        metadata.detectedLanguage?.let { callback.languageDetected(it) }

        val languageSpecificModel = metadata.detectedLanguage?.let {
            runConfiguration.languageSpecificModels[it]
        }?.let {
            callback.updateStatus(InferenceState.SwitchingModel)
            modelManager.obtainModel(it)
        }
        yield()

        return@coroutineScope when {
            (languageSpecificModel != null) -> {
                val languageSession =
                    languageSpecificModel.startInferenceSession(decodingConfiguration)

                languageSession.melToFeatures(mel)
                yield()

                callback.updateStatus(InferenceState.DecodingStarted)
                languageSession.decodeMetadata()
                yield()

                languageSession.decodeOutput {
                    callback.partialResult(it.trim())
                }.trim()
            }

            else -> {
                callback.updateStatus(InferenceState.DecodingStarted)
                session.decodeOutput {
                    callback.partialResult(it.trim())
                }.trim()
            }
        }
    }
}