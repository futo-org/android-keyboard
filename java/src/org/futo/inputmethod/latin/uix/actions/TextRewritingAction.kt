package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.UixManager

import org.futo.inputmethod.latin.uix.UixActionKeyboardManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextRewritingAction(modifier: Modifier = Modifier, keyboardManager: KeyboardManagerForAction) {
    val tones = listOf("Angry", "Funny", "Professional", "Romantic", "Casual")
    val pagerState = rememberPagerState(pageCount = { tones.size })
    var rewrittenText by remember { mutableStateOf("") }

    val latinIME = (keyboardManager as UixActionKeyboardManager).latinIME
    val inputConnection = latinIME.currentInputConnection
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier) {
        Text("Text Rewriting", style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        HorizontalPager(state = pagerState) { page ->
            Text("Rewrite to: ${tones[page]}")
        }

        Button(onClick = {
            val originalText = inputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""
            coroutineScope.launch {
                val rewritten = latinIME.imeManager.getActiveIME(latinIME.settings.current)
                    .rewriteText(originalText, tones[pagerState.currentPage])
                rewrittenText = rewritten ?: "Failed to rewrite text."
            }
        }) {
            Text("Rewrite")
        }

        if (rewrittenText.isNotEmpty()) {
            Text(rewrittenText)
            Button(onClick = {
                val originalText = inputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""
                inputConnection?.deleteSurroundingText(originalText.length, 0)
                inputConnection?.commitText(rewrittenText, 1)
                keyboardManager.closeActionWindow()
            }) {
                Text("Use Text")
            }
        }
    }
}

object TextRewritingAction : Action(
    icon = R.drawable.sym_keyboard_settings_holo_dark,
    name = R.string.text_rewriting_action,
    windowImpl = { keyboardManager, _ ->
        object : ActionWindow() {
            @Composable
            override fun windowName(): String = "Text Rewriting"

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                TextRewritingAction(keyboardManager = keyboardManager)
            }
        }
    }
)
