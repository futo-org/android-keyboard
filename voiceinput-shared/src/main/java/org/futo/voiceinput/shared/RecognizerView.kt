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
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
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

abstract class RecognizerView(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val modelManager: ModelManager
) {
    // TODO: Should not get settings here, pass settings to constructor
    private val shouldPlaySounds: ValueFromSettings<Boolean> = ValueFromSettings(ENABLE_SOUND, true)
    private val shouldBeVerbose: ValueFromSettings<Boolean> =
        ValueFromSettings(VERBOSE_PROGRESS, false)

    // TODO: SoundPool should be managed by parent, not by view, as the view is short-lived
    /* val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(CONTENT_TYPE_SONIFICATION).build()
    ).build()*/

    private var startSoundId: Int = -1
    private var cancelSoundId: Int = -1

    abstract fun onCancel()
    abstract fun sendResult(result: String)
    abstract fun sendPartialResult(result: String): Boolean
    abstract fun requestPermission()

    companion object {
        private val verboseAnnotations = hashMapOf(
            InferenceState.ExtractingMel to R.string.extracting_features,
            InferenceState.LoadingModel to R.string.loading_model,
            InferenceState.Encoding to R.string.encoding,
            InferenceState.DecodingLanguage to R.string.decoding,
            InferenceState.SwitchingModel to R.string.switching_model,
            InferenceState.DecodingStarted to R.string.decoding
        )

        private val defaultAnnotations = hashMapOf(
            InferenceState.ExtractingMel to R.string.processing,
            InferenceState.LoadingModel to R.string.processing,
            InferenceState.Encoding to R.string.processing,
            InferenceState.DecodingLanguage to R.string.processing,
            InferenceState.SwitchingModel to R.string.switching_model,
            InferenceState.DecodingStarted to R.string.processing
        )
    }

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
                        onFinish = { recognizer.finishRecognizer() },
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

    fun onClose() {
        recognizer.cancelRecognizer()
    }

    private val listener = object : AudioRecognizerListener {
        // Tries to play a sound. If it's not yet ready, plays it when it's ready
        private fun playSound(id: Int) {
            /*
            lifecycleScope.launch {
                shouldPlaySounds.load(context) {
                    if (it) {
                        if (soundPool.play(id, 1.0f, 1.0f, 0, 0, 1.0f) == 0) {
                            soundPool.setOnLoadCompleteListener { soundPool, sampleId, status ->
                                if ((sampleId == id) && (status == 0)) {
                                    soundPool.play(id, 1.0f, 1.0f, 0, 0, 1.0f)
                                }
                            }
                        }
                    }
                }
            }
            */
        }

        override fun cancelled() {
            playSound(cancelSoundId)
            onCancel()
        }

        override fun finished(result: String) {
            sendResult(result)
        }

        override fun languageDetected(language: Language) {
            // TODO
        }

        override fun partialResult(result: String) {
            if (!sendPartialResult(result)) {
                if (result.isNotBlank()) {
                    partialDecodingText.value = result
                    currentViewState.value = CurrentView.PartialDecodingResult
                }
            }
        }


        override fun decodingStatus(status: InferenceState) {
            val text = context.getString(
                when (shouldBeVerbose.value) {
                    true -> verboseAnnotations[status]!!
                    false -> defaultAnnotations[status]!!
                }
            )

            loadingCircleText.value = text
            currentViewState.value = CurrentView.LoadingCircle
        }

        override fun loading() {
            loadingCircleText.value = context.getString(R.string.initializing)
            currentViewState.value = CurrentView.LoadingCircle
        }

        override fun needPermission() {
            requestPermission()
        }

        override fun permissionRejected() {
            currentViewState.value = CurrentView.PermissionError
        }

        override fun recordingStarted() {
            updateMagnitude(0.0f, MagnitudeState.NOT_TALKED_YET)

            playSound(startSoundId)
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
        context, lifecycleScope, modelManager, listener, AudioRecognizerSettings(
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

    fun init() {
        lifecycleScope.launch {
            shouldBeVerbose.load(context)
        }

        //startSoundId = soundPool.load(this.context, R.raw.start, 0)
        //cancelSoundId = soundPool.load(this.context, R.raw.cancel, 0)

        recognizer.create()
    }

    fun permissionResultGranted() {
        recognizer.permissionResultGranted()
    }

    fun permissionResultRejected() {
        recognizer.permissionResultRejected()
    }
}
