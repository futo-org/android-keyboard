package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction


// TODO: For now, this calls CODE_SHORTCUT. In the future, we will want to
// make this a window
val VoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = "Voice Input",
    //simplePressImpl = {
    //    it.triggerSystemVoiceInput()
    //},
    simplePressImpl = null,
    windowImpl = object : ActionWindow {
        @Composable
        override fun windowName(): String {
            return "Voice Input"
        }

        @Composable
        override fun WindowContents(manager: KeyboardManagerForAction) {
            Box(modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(id = R.drawable.mic_fill),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                )
            }
        }
    }
)