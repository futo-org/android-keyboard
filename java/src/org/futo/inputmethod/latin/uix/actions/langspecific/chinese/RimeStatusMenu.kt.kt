package org.futo.inputmethod.latin.uix.actions.langspecific.chinese

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import icu.astronot233.rime.Rime
import icu.astronot233.rime.RimeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun RimeStatusMenu(rime: Rime, coroScope: CoroutineScope) {
    val status = remember { RimeApi.getStatus() }
    var isFullShape by remember { mutableStateOf(status.fullShape) }
    var isAsciiMode by remember { mutableStateOf(status.asciiMode) }
    var isAsciiPunct by remember { mutableStateOf(status.asciiPunct) }
    var isTraditional by remember { mutableStateOf(status.traditional) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiButton(emoji = if (isFullShape) "🌕" else "🌓") {
            isFullShape = !isFullShape
            coroScope.launch { rime.setOption("full_shape", isFullShape) }
        }
        EmojiButton(emoji = if (isAsciiMode) "Ａ" else "文") {
            isAsciiMode = !isAsciiMode
            coroScope.launch { rime.setOption("ascii_mode", isAsciiMode) }
        }
        EmojiButton(emoji = if (isAsciiPunct) "＂＇" else "『」") {
            isAsciiPunct = !isAsciiPunct
            coroScope.launch { rime.setOption("ascii_punct", isAsciiPunct) }
        }
        EmojiButton(emoji = if (isTraditional) "傳" else "简" ) {
            isTraditional = !isTraditional
            coroScope.launch {
                rime.setOption("traditional", isTraditional)
                rime.setOption("simplification", !isTraditional)
            }
        }
    }
}
