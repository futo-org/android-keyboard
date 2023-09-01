package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.ENABLE_ENGLISH
import org.futo.inputmethod.latin.uix.ENABLE_MULTILINGUAL
import org.futo.inputmethod.latin.uix.ENABLE_SOUND
import org.futo.inputmethod.latin.uix.ENGLISH_MODEL_INDEX
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.LANGUAGE_TOGGLES
import org.futo.inputmethod.latin.uix.MULTILINGUAL_MODEL_INDEX
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.VERBOSE_PROGRESS
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.futo.voiceinput.shared.MULTILINGUAL_MODELS
import org.futo.voiceinput.shared.ModelDoesNotExistException
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.SoundPlayer
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration

val SystemVoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = R.string.voice_input_action_title,
    simplePressImpl = { it, _ ->
        it.triggerSystemVoiceInput()
    },
    persistentState = null,
    windowImpl = null
)


class VoiceInputPersistentState(val manager: KeyboardManagerForAction) : PersistentActionState {
    val modelManager = ModelManager(manager.getContext())
    val soundPlayer = SoundPlayer(manager.getContext())

    override suspend fun cleanUp() {
        modelManager.cleanUp()
    }
}

private class VoiceInputActionWindow(
    val manager: KeyboardManagerForAction, val state: VoiceInputPersistentState
) : ActionWindow, RecognizerViewListener {
    val context = manager.getContext()

    private var shouldPlaySounds: Boolean = false
    private suspend fun loadSettings(): RecognizerViewSettings = coroutineScope {
        val enableSound = async { context.getSetting(ENABLE_SOUND) }
        val verboseFeedback = async { context.getSetting(VERBOSE_PROGRESS) }
        val disallowSymbols = async { context.getSetting(DISALLOW_SYMBOLS) }
        val enableEnglish = async { context.getSetting(ENABLE_ENGLISH) }
        val englishModelIdx = async { context.getSetting(ENGLISH_MODEL_INDEX) }
        val enableMultilingual = async { context.getSetting(ENABLE_MULTILINGUAL) }
        val multilingualModelIdx = async { context.getSetting(MULTILINGUAL_MODEL_INDEX) }
        val allowedLanguages = async {
            context.getSetting(LANGUAGE_TOGGLES).mapNotNull { getLanguageFromWhisperString(it) }
                .toSet()
        }

        val primaryModel = if (enableMultilingual.await()) {
            MULTILINGUAL_MODELS[multilingualModelIdx.await()]
        } else {
            ENGLISH_MODELS[englishModelIdx.await()]
        }

        val languageSpecificModels = mutableMapOf<Language, ModelLoader>()
        if (enableEnglish.await()) {
            languageSpecificModels[Language.English] = ENGLISH_MODELS[englishModelIdx.await()]
        }

        shouldPlaySounds = enableSound.await()

        return@coroutineScope RecognizerViewSettings(
            shouldShowInlinePartialResult = false,
            shouldShowVerboseFeedback = verboseFeedback.await(),
            modelRunConfiguration = MultiModelRunConfiguration(
                primaryModel = primaryModel, languageSpecificModels = languageSpecificModels
            ),
            decodingConfiguration = DecodingConfiguration(
                languages = allowedLanguages.await(), suppressSymbols = disallowSymbols.await()
            )
        )
    }

    private var recognizerView: MutableState<RecognizerView?> = mutableStateOf(null)
    private var modelException: MutableState<ModelDoesNotExistException?> = mutableStateOf(null)

    private val initJob = manager.getLifecycleScope().launch {
        yield()
        val settings = withContext(Dispatchers.IO) {
            loadSettings()
        }

        yield()
        val recognizerView = try {
            RecognizerView(
                context = manager.getContext(),
                listener = this@VoiceInputActionWindow,
                settings = settings,
                lifecycleScope = manager.getLifecycleScope(),
                modelManager = state.modelManager
            )
        } catch(e: ModelDoesNotExistException) {
            modelException.value = e
            return@launch
        }

        this@VoiceInputActionWindow.recognizerView.value = recognizerView

        yield()
        recognizerView.reset()

        yield()
        recognizerView.start()
    }

    private var inputTransaction: ActionInputTransaction? = null
    private fun getOrStartInputTransaction(): ActionInputTransaction {
        if (inputTransaction == null) {
            inputTransaction = manager.createInputTransaction(true)
        }

        return inputTransaction!!
    }

    @Composable
    private fun ModelDownloader(modelException: ModelDoesNotExistException) {
        Column {
            Text("Model Download Required")
            Text("Not yet implemented")
            // TODO
        }
    }

    @Composable
    override fun windowName(): String {
        return stringResource(R.string.voice_input_action_title)
    }

    @Composable
    override fun WindowContents() {
        Box(modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = true,
                onClickLabel = null,
                onClick = { recognizerView.value?.finish() },
                role = null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() })) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                when {
                    modelException.value != null -> ModelDownloader(modelException.value!!)
                    recognizerView.value != null -> recognizerView.value!!.Content()
                }
            }
        }
    }

    override fun close() {
        initJob.cancel()
        recognizerView.value?.cancel()
    }

    private var wasFinished = false
    private var cancelPlayed = false
    override fun cancelled() {
        if (!wasFinished) {
            if (shouldPlaySounds && !cancelPlayed) {
                state.soundPlayer.playCancelSound()
                cancelPlayed = true
            }
            inputTransaction?.cancel()
        }
    }

    override fun recordingStarted() {
        if (shouldPlaySounds) {
            state.soundPlayer.playStartSound()
        }
    }

    override fun finished(result: String) {
        wasFinished = true

        getOrStartInputTransaction().commit(result)
        manager.closeActionWindow()
    }

    override fun partialResult(result: String) {
        getOrStartInputTransaction().updatePartial(result)
    }

    override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean {
        return false
    }
}

val VoiceInputAction = Action(icon = R.drawable.mic_fill,
    name = R.string.voice_input_action_title,
    simplePressImpl = null,
    persistentState = { VoiceInputPersistentState(it) },
    windowImpl = { manager, persistentState ->
        VoiceInputActionWindow(
            manager = manager, state = persistentState as VoiceInputPersistentState
        )
    }
)