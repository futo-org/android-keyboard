package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.SoundPlayer
import org.futo.voiceinput.shared.whisper.ModelManager

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
val VoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = R.string.voice_input_action_title,
    simplePressImpl = null,
    persistentState = { VoiceInputPersistentState(it) },

    windowImpl = { manager, persistentState ->
        val state = persistentState as VoiceInputPersistentState
        object : ActionWindow, RecognizerViewListener {
            private val recognizerView = RecognizerView(
                context = manager.getContext(),
                listener = this,
                settings = RecognizerViewSettings(
                    shouldShowInlinePartialResult = false,
                    shouldShowVerboseFeedback = true
                ),
                lifecycleScope = manager.getLifecycleScope(),
                modelManager = state.modelManager
            )

            init {
                recognizerView.reset()
                recognizerView.start()
            }

            private var inputTransaction: ActionInputTransaction? = null
            private fun getOrStartInputTransaction(): ActionInputTransaction {
                if(inputTransaction == null) {
                    inputTransaction = manager.createInputTransaction(true)
                }

                return inputTransaction!!
            }

            @Composable
            override fun windowName(): String {
                return stringResource(R.string.voice_input_action_title)
            }

            @Composable
            override fun WindowContents() {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        enabled = true,
                        onClickLabel = null,
                        onClick = { recognizerView.finish() },
                        role = null,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        recognizerView.Content()
                    }
                }
            }

            override fun close() {
                recognizerView.cancel()
            }

            private var wasFinished = false
            override fun cancelled() {
                if(!wasFinished) {
                    state.soundPlayer.playCancelSound()
                    getOrStartInputTransaction().cancel()
                }
            }

            override fun recordingStarted() {
                state.soundPlayer.playStartSound()
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
    }
)