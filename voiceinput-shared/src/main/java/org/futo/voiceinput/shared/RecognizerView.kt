package org.futo.voiceinput.shared

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
import android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
import android.media.SoundPool
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch
import org.futo.voiceinput.shared.types.AudioRecognizerListener
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.MagnitudeState
import org.futo.voiceinput.shared.ui.InnerRecognize
import org.futo.voiceinput.shared.ui.PartialDecodingResult
import org.futo.voiceinput.shared.ui.RecognizeLoadingCircle
import org.futo.voiceinput.shared.ui.RecognizeMicError
import org.futo.voiceinput.shared.util.ENABLE_SOUND
import org.futo.voiceinput.shared.util.VERBOSE_PROGRESS
import org.futo.voiceinput.shared.util.ValueFromSettings
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration

data class RecognizerViewSettings(
    val shouldShowVerboseFeedback: Boolean,
    val shouldShowInlinePartialResult: Boolean
)

private val VerboseAnnotations = hashMapOf(
    InferenceState.ExtractingMel to R.string.extracting_features,
    InferenceState.LoadingModel to R.string.loading_model,
    InferenceState.Encoding to R.string.encoding,
    InferenceState.DecodingLanguage to R.string.decoding,
    InferenceState.SwitchingModel to R.string.switching_model,
    InferenceState.DecodingStarted to R.string.decoding
)

private val DefaultAnnotations = hashMapOf(
    InferenceState.ExtractingMel to R.string.processing,
    InferenceState.LoadingModel to R.string.processing,
    InferenceState.Encoding to R.string.processing,
    InferenceState.DecodingLanguage to R.string.processing,
    InferenceState.SwitchingModel to R.string.switching_model,
    InferenceState.DecodingStarted to R.string.processing
)

interface RecognizerViewListener {
    fun cancelled()

    fun recordingStarted()

    fun finished(result: String)

    fun partialResult(result: String)

    // Return true if a permission modal was shown, otherwise return false
    fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean
}

class RecognizerView(
    private val context: Context,
    private val listener: RecognizerViewListener,
    private val settings: RecognizerViewSettings,
    lifecycleScope: LifecycleCoroutineScope,
    modelManager: ModelManager
) {
    private val magnitudeState = mutableStateOf(0.0f)
    private val statusState = mutableStateOf(MagnitudeState.NOT_TALKED_YET)

    enum class CurrentView {
        LoadingCircle, PartialDecodingResult, InnerRecognize, PermissionError
    }

    private val loadingCircleText = mutableStateOf("")
    private val partialDecodingText = mutableStateOf("")
    private val currentViewState = mutableStateOf(CurrentView.LoadingCircle)

    @Composable
    fun Content() {
        when (currentViewState.value) {
            CurrentView.LoadingCircle -> {
                Column {
                    RecognizeLoadingCircle(text = loadingCircleText.value)
                }
            }

            CurrentView.PartialDecodingResult -> {
                Column {
                    PartialDecodingResult(text = partialDecodingText.value)
                }
            }

            CurrentView.InnerRecognize -> {
                Column {
                    InnerRecognize(
                        onFinish = { recognizer.finish() },
                        magnitude = magnitudeState,
                        state = statusState
                    )
                }
            }

            CurrentView.PermissionError -> {
                Column {
                    RecognizeMicError(openSettings = { recognizer.openPermissionSettings() })
                }
            }
        }
    }

    fun finish() {
        recognizer.finish()
    }

    fun cancel() {
        recognizer.cancel()
    }

    private val audioRecognizerListener = object : AudioRecognizerListener {
        override fun cancelled() {
            listener.cancelled()
        }

        override fun finished(result: String) {
            listener.finished(result)
        }

        override fun languageDetected(language: Language) {
            // TODO
        }

        override fun partialResult(result: String) {
            listener.partialResult(result)
            if (settings.shouldShowInlinePartialResult && result.isNotBlank()) {
                partialDecodingText.value = result
                currentViewState.value = CurrentView.PartialDecodingResult
            }
        }


        override fun decodingStatus(status: InferenceState) {
            val text = context.getString(
                when (settings.shouldShowVerboseFeedback) {
                    true -> VerboseAnnotations[status]!!
                    false -> DefaultAnnotations[status]!!
                }
            )

            loadingCircleText.value = text
            currentViewState.value = CurrentView.LoadingCircle
        }

        override fun loading() {
            loadingCircleText.value = context.getString(R.string.initializing)
            currentViewState.value = CurrentView.LoadingCircle
        }

        override fun needPermission(onResult: (Boolean) -> Unit) {
            val shown = listener.requestPermission(
                onGranted = {
                    onResult(true)
                },
                onRejected = {
                    onResult(false)
                    currentViewState.value = CurrentView.PermissionError
                }
            )

            if(!shown) {
                currentViewState.value = CurrentView.PermissionError
            }
        }

        override fun recordingStarted() {
            updateMagnitude(0.0f, MagnitudeState.NOT_TALKED_YET)
            listener.recordingStarted()
        }

        override fun updateMagnitude(magnitude: Float, state: MagnitudeState) {
            magnitudeState.value = magnitude
            statusState.value = state
            currentViewState.value = CurrentView.InnerRecognize
        }

        override fun processing() {
            loadingCircleText.value = context.getString(R.string.processing)
            currentViewState.value = CurrentView.LoadingCircle
        }
    }

    // TODO: Dummy settings, should get them from constructor
    private val recognizer: AudioRecognizer = AudioRecognizer(
        context, lifecycleScope, modelManager, audioRecognizerListener, AudioRecognizerSettings(
            modelRunConfiguration = MultiModelRunConfiguration(
                primaryModel = ENGLISH_MODELS[0], languageSpecificModels = mapOf()
            ), decodingConfiguration = DecodingConfiguration(
                languages = setOf(), suppressSymbols = true
            )
        )
    )

    fun reset() {
        recognizer.reset()
    }

    fun start() {
        recognizer.start()
    }
}
