package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.whisper.ModelManager

val SystemVoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = "Voice Input",
    simplePressImpl = { it, _ ->
        it.triggerSystemVoiceInput()
    },
    persistentState = null,
    windowImpl = null
)


class VoiceInputPersistentState(val manager: KeyboardManagerForAction) : PersistentActionState {
    var modelManager: ModelManager = ModelManager(manager.getContext())

    override suspend fun cleanUp() {
        modelManager.cleanUp()
    }
}
val VoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = "Voice Input",
    simplePressImpl = null,
    persistentState = { VoiceInputPersistentState(it) },

    windowImpl = { manager, persistentState ->
        val state = persistentState as VoiceInputPersistentState
        object : ActionWindow, RecognizerView(manager.getContext(), manager.getLifecycleScope(), state.modelManager) {
            init {
                this.reset()
                this.init()
            }

            override fun onCancel() {
                this.reset()
                manager.closeActionWindow()
            }

            override fun sendResult(result: String) {
                manager.typeText(result)
                onCancel()
            }

            override fun sendPartialResult(result: String): Boolean {
                manager.typePartialText(result)
                return true
            }

            override fun requestPermission() {
                permissionResultRejected()
            }

            @Composable
            override fun windowName(): String {
                return "Voice Input"
            }

            @Composable
            override fun WindowContents() {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        Content()
                    }
                }
            }

            override fun close() {
                this.reset()
                //soundPool.release()
            }
        }
    }
)