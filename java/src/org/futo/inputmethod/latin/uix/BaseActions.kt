@file:Suppress("LocalVariableName")

package org.futo.inputmethod.latin.uix

import android.os.Build
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOptionKeys
import org.futo.inputmethod.latin.uix.theme.ThemeOptions


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

val ThemeAction = Action(
    icon = R.drawable.eye,
    name = "Theme Switcher",
    simplePressImpl = null,
    windowImpl = object : ActionWindow {
        @Composable
        override fun windowName(): String {
            return "Theme Switcher"
        }

        @Composable
        override fun WindowContents(manager: KeyboardManagerForAction) {
            val context = LocalContext.current
            LazyColumn(modifier = Modifier
                .padding(8.dp, 0.dp)
                .fillMaxWidth())
            {
                items(ThemeOptionKeys.count()) {
                    val key = ThemeOptionKeys[it]
                    val themeOption = ThemeOptions[key]
                    if(themeOption != null && themeOption.available(context)) {
                        Button(onClick = {
                            manager.updateTheme(
                                themeOption
                            )
                        }) {
                            Text(themeOption.name)
                        }
                    }
                }
            }
        }
    }
)
