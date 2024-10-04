package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionBarHeight
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.v2keyboard.KeyboardMode
import org.futo.voiceinput.shared.ui.theme.Typography

@Composable
private fun RowScope.KeyboardMode(iconRes: Int, name: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.weight(1.0f).height(72.dp), onClick = onClick) {
        Box(Modifier.height(72.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(iconRes), modifier = Modifier.size(32.dp), contentDescription = null)
                Text(name, style = Typography.labelSmall)
            }
        }
    }
}

val KeyboardModeAction = Action(
    icon = R.drawable.keyboard,
    name = R.string.keyboard_modes_action_title,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        object : ActionWindow {
            @Composable
            override fun windowName(): String =
                stringResource(R.string.keyboard_modes_action_title)

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                Column {
                    Row(Modifier.height(ActionBarHeight)) {
                        IconButton(onClick = {
                            manager.closeActionWindow()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                        }
                        Spacer(Modifier.weight(1.0f))
                        TextButton(onClick = {
                            manager.showResizer()
                        }) {
                            Text("Resize keyboard", style = Typography.bodySmall)
                        }
                    }
                    Row {
                        KeyboardMode(R.drawable.keyboard, "Standard") {
                            manager.getSizingCalculator().editSavedSettings {
                                it.copy(
                                    currentMode = KeyboardMode.Regular,
                                    prefersSplit = false
                                )
                            }
                        }
                        KeyboardMode(R.drawable.left_handed_keyboard_outline, "One-handed") {
                            manager.getSizingCalculator().editSavedSettings {
                                it.copy(
                                    currentMode = KeyboardMode.OneHanded
                                )
                            }
                        }
                        KeyboardMode(R.drawable.split_keyboard_outline, "Split") {
                            manager.getSizingCalculator().editSavedSettings {
                                it.copy(
                                    currentMode = KeyboardMode.Split,
                                    prefersSplit = true
                                )
                            }
                        }
                        KeyboardMode(R.drawable.floating_keyboard_outline, "Floating") {
                            manager.getSizingCalculator().editSavedSettings {
                                it.copy(
                                    currentMode = KeyboardMode.Floating
                                )
                            }
                        }
                    }
                }
            }

            override fun close() {

            }
        }
    },
    onlyShowAboveKeyboard = true,
    fixedWindowHeight = 72.dp + ActionBarHeight
)