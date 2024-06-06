package org.futo.inputmethod.latin.uix.actions

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.AUDIO_FOCUS
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.ENABLE_SOUND
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.inputmethod.latin.uix.VERBOSE_PROGRESS
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.voiceinput.downloader.DownloadActivity
import org.futo.inputmethod.latin.xlm.UserDictionaryObserver
import org.futo.inputmethod.updates.openURI
import org.futo.voiceinput.shared.ModelDoesNotExistException
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.SoundPlayer
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import java.util.Locale

val SystemVoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = R.string.system_voice_input_action_title,
    simplePressImpl = { it, _ ->
        it.triggerSystemVoiceInput()
    },
    persistentState = null,
    windowImpl = null
)


class VoiceInputPersistentState(val manager: KeyboardManagerForAction) : PersistentActionState {
    val modelManager = ModelManager(manager.getContext())
    val soundPlayer = SoundPlayer(manager.getContext())
    val userDictionaryObserver = UserDictionaryObserver(manager.getContext())

    override suspend fun cleanUp() {
        modelManager.cleanUp()
    }
}

private class VoiceInputActionWindow(
    val manager: KeyboardManagerForAction, val state: VoiceInputPersistentState,
    val model: ModelLoader, val locale: Locale
) : ActionWindow, RecognizerViewListener {
    val context = manager.getContext()

    private var shouldPlaySounds: Boolean = false
    private suspend fun loadSettings(): RecognizerViewSettings = coroutineScope {
        val enableSound = async { context.getSetting(ENABLE_SOUND) }
        val verboseFeedback = async { context.getSetting(VERBOSE_PROGRESS) }
        val disallowSymbols = async { context.getSetting(DISALLOW_SYMBOLS) }
        val useBluetoothAudio = async { context.getSetting(PREFER_BLUETOOTH) }
        val requestAudioFocus = async { context.getSetting(AUDIO_FOCUS) }

        val primaryModel = model
        val languageSpecificModels = mutableMapOf<Language, ModelLoader>()
        val allowedLanguages = setOf(
            getLanguageFromWhisperString(locale.language)!!
        )

        shouldPlaySounds = enableSound.await()

        return@coroutineScope RecognizerViewSettings(
            shouldShowInlinePartialResult = false,
            shouldShowVerboseFeedback = verboseFeedback.await(),
            modelRunConfiguration = MultiModelRunConfiguration(
                primaryModel = primaryModel,
                languageSpecificModels = languageSpecificModels
            ),
            decodingConfiguration = DecodingConfiguration(
                glossary = state.userDictionaryObserver.getWords().map { it.word },
                languages = allowedLanguages,
                suppressSymbols = disallowSymbols.await()
            ),
            recordingConfiguration = RecordingSettings(
                preferBluetoothMic = useBluetoothAudio.await(),
                requestAudioFocus = requestAudioFocus.await()
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

    private var inputTransaction = manager.createInputTransaction(true)

    @Composable
    private fun ModelDownloader(modelException: ModelDoesNotExistException) {
        val context = LocalContext.current
        Box(modifier = Modifier.fillMaxSize().clickable {
            val intent = Intent(context, DownloadActivity::class.java)
            intent.putStringArrayListExtra("models", ArrayList(modelException.models.map { model -> model.getRequiredDownloadList(context) }.flatten()))

            if(context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        }) {
            Text("Tap to complete setup", modifier = Modifier.align(Alignment.Center))
        }
    }

    @Composable
    override fun windowName(): String {
        return stringResource(R.string.voice_input_action_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        Box(modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = true,
                onClickLabel = null,
                onClick = { recognizerView.value?.finish() },
                role = null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() })
            .semantics(mergeDescendants = true) {
                traversalIndex = -1.0f
            }) {
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
            inputTransaction.cancel()
        }
    }

    override fun recordingStarted(device: MicrophoneDeviceState) {
        if (shouldPlaySounds) {
            state.soundPlayer.playStartSound()

            // Only set the setting if bluetooth is available, else it would reset the setting
            // every time it's used without a bluetooth device connected.
            if(device.bluetoothAvailable) {
                manager.getLifecycleScope().launch {
                    context.setSetting(PREFER_BLUETOOTH, device.bluetoothActive)
                }
            }
        }
    }

    override fun finished(result: String) {
        wasFinished = true

        inputTransaction.commit(result)
        manager.announce(result)
        manager.closeActionWindow()
    }

    override fun partialResult(result: String) {
        inputTransaction.updatePartial(result)
    }

    override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean {
        return false
    }
}

private class VoiceInputNoModelWindow(val locale: Locale) : ActionWindow {
    @Composable
    override fun windowName(): String {
        return stringResource(R.string.voice_input_action_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val context = LocalContext.current
        Box(modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = true,
                onClickLabel = null,
                onClick = {
                    context.openURI("https://keyboard.futo.org/voice-input-models", true)
                },
                role = null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() })) {
            Text("No voice input model installed for ${locale.displayLanguage}, tap to check options?", modifier = Modifier.align(Alignment.Center).padding(8.dp), textAlign = TextAlign.Center)
        }
    }

    override fun close() {

    }

}

val VoiceInputAction = Action(icon = R.drawable.mic_fill,
    name = R.string.voice_input_action_title,
    simplePressImpl = null,
    keepScreenAwake = true,
    persistentState = { VoiceInputPersistentState(it) },
    windowImpl = { manager, persistentState ->
        val locale = manager.getActiveLocale()

        val model = ResourceHelper.tryFindingVoiceInputModelForLocale(manager.getContext(), locale)

        if(model == null) {
            VoiceInputNoModelWindow(locale)
        } else {
            VoiceInputActionWindow(
                manager = manager, state = persistentState as VoiceInputPersistentState,
                locale = locale, model = model
            )
        }
    }
)