package org.futo.inputmethod.latin.uix.actions

import android.os.Debug
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.Typography


val MemoryDebugAction = Action(
    icon = R.drawable.code,
    name = R.string.mem_debug_action_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    windowImpl = { manager, _ ->
        object : ActionWindow {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.mem_debug_action_title)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val state: MutableState<Map<String, String>> = remember { mutableStateOf(mapOf()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(250)

                        val newInfo = Debug.MemoryInfo()
                        Debug.getMemoryInfo(newInfo)
                        state.value = newInfo.memoryStats
                    }
                }

                ScrollableList {
                    state.value.forEach {
                        val value = it.value.toInt().toFloat() / 1000.0f
                        Text("${it.key}: ${String.format("%.2f", value)}MB", style = Typography.labelSmall)
                    }

                    Button(onClick = {
                        manager.updateTheme(ThemeOptions.values.random())
                    }) {
                        Text("Randomize Theme")
                    }
                }
            }

            override fun close() {

            }
        }
    }
)