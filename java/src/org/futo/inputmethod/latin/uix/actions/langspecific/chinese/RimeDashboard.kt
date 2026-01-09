package org.futo.inputmethod.latin.uix.actions.langspecific.chinese

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import icu.astronot233.rime.Rime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.futo.inputmethod.engine.general.ChineseIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.LangSpecAction

@Composable
private fun RimeDashboardScreen(rime: Rime, coroScope: CoroutineScope) {
    var schemaMenuTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RimeSchemaMenu(rime, coroScope, schemaMenuTimestamp)
            EmojiButton(emoji = "🔄️", spinTriggerer = schemaMenuTimestamp, buttonSize = 48.dp, fontSize = 24.sp) {
                coroScope.launch {
                    rime.deploy()
                    schemaMenuTimestamp = System.currentTimeMillis()
                }
            }
        }
        RimeStatusMenu(rime, coroScope)
    }
}

val RimeDashboard = LangSpecAction(
    action = Action(
        icon = R.drawable.rime_logo,
        name = R.string.action_rime_dashboard,
        simplePressImpl = null,
        persistentState = null,
        canShowKeyboard = true,
        windowImpl = { manager, _ -> object : ActionWindow() {
            @Composable override fun windowName(): String = stringResource(R.string.action_rime_dashboard)

            @Composable override fun WindowContents(keyboardShown: Boolean) {
                val ime = manager.getCurrentIME()
                if (ime !is ChineseIME)
                    return
                val (rime, coroScope) = ime.requestTakeOver(this)
                if (rime == null || coroScope == null)
                    return
                RimeDashboardScreen(rime, coroScope)
            }
        } }
    ),
    langRequired = setOf("zh")
)

@Composable
internal fun EmojiButton(emoji: String, spinTriggerer: Any? = null, enabled: Boolean = true, buttonSize: Dp = 72.dp, fontSize: TextUnit = 30.sp, onToggle: () -> Unit) {
    var rotation by remember { mutableStateOf(0f) }
    LaunchedEffect(emoji, spinTriggerer) {
        animate(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = tween(500)
        ) { value, _ -> rotation = value }
    }

    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onToggle() }
            .background(
                color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = fontSize,
            color = if (enabled)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation)
        )
    }
}

@Composable
internal fun EmojiTextButton(emoji: String, text: String, spinTriggerer: Any? = null, enabled: Boolean = true, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EmojiButton(emoji = emoji, spinTriggerer = spinTriggerer, enabled = enabled, onToggle = onToggle)
        Text(text)
    }
}
