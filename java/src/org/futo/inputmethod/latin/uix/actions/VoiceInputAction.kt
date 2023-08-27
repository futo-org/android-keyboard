package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleCoroutineScope
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.ml.WhisperModelWrapper

class VoiceInputPersistentState(val manager: KeyboardManagerForAction) : PersistentActionState {
    var model: WhisperModelWrapper? = null

    override fun cleanUp() {
        model?.close()
        model = null
    }
}


val VoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = "Voice Input",
    //simplePressImpl = {
    //    it.triggerSystemVoiceInput()
    //},
    simplePressImpl = null,
    persistentState = { VoiceInputPersistentState(it) },

    windowImpl = { manager, persistentState ->
        object : ActionWindow, RecognizerView() {
            val state = persistentState as VoiceInputPersistentState

            override val context: Context = manager.getContext()
            override val lifecycleScope: LifecycleCoroutineScope
                get() = manager.getLifecycleScope()

            val currentContent: MutableState<@Composable () -> Unit> = mutableStateOf({})

            init {
                this.reset()
                this.init()
            }

            override fun setContent(content: @Composable () -> Unit) {
                currentContent.value = content
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

            override fun tryRestoreCachedModel(): WhisperModelWrapper? {
                return state.model
            }

            override fun cacheModel(model: WhisperModelWrapper) {
                state.model = model
            }

            @Composable
            override fun Window(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
                Column {
                    content()
                }
            }

            @Composable
            override fun windowName(): String {
                return "Voice Input"
            }

            @Composable
            override fun WindowContents() {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        currentContent.value()
                    }
                }
            }

            override fun close() {
                this.reset()
                soundPool.release()
            }
        }
    }
)