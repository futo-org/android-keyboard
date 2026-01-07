package org.futo.inputmethod.latin.uix.actions.langspecific

import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow

@Composable
fun IconWithColor(@DrawableRes iconId: Int, iconColor: Color, modifier: Modifier = Modifier) {
    val icon = painterResource(id = iconId)

    Canvas(modifier = modifier) {
        translate(
            left = this.size.width / 2.0f - icon.intrinsicSize.width / 2.0f,
            top = this.size.height / 2.0f - icon.intrinsicSize.height / 2.0f
        ) {
            with(icon) {
                draw(
                    icon.intrinsicSize,
                    colorFilter = ColorFilter.tint(iconColor)
                )
            }
        }
    }
}

@Composable
fun Modifier.clickableAction(onTrigger: (Boolean) -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) {
        if(isPressed) {
            onTrigger(false)
        }
    }
    return this.clickable(interactionSource, indication = LocalIndication.current, onClick = { })
}

@Composable
fun ActionKey(
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contents: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.padding(4.dp).clickableAction { onTrigger() },
        shape = RoundedCornerShape(8.dp),
        color = color
    ) { contents() }
}

@Composable
fun SideKeys(modifier: Modifier, onEvent: (Int, Int) -> Unit) {
    Column(modifier = modifier) {
        ActionKey( // Schema
            onTrigger = { onEvent(KeyEvent.KEYCODE_F1, KeyEvent.META_CTRL_ON) },
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            {
                IconWithColor(
                    iconId = R.drawable.file_text,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        )

        ActionKey( // Redeploy
            onTrigger = { onEvent(KeyEvent.KEYCODE_F2, KeyEvent.META_CTRL_ON) },
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            {
                IconWithColor(
                    iconId = R.drawable.clipboard,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        )
    }
}

@Composable
fun RimeDashboardScreen(
    onEvent: (Int, Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxHeight()
            .weight(3.0f)) {

        }
        SideKeys(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1.0f),
            onEvent = onEvent
        )
    }
}

val RimeDashboard = Action(
    icon = R.drawable.cpu, // TODO: draw RIME icon
    name = R.string.action_rime_dashboard,
    simplePressImpl = null,
    persistentState = null,
    canShowKeyboard = true,
    windowImpl = { manager, persistentState -> object : ActionWindow() {
        @Composable override fun windowName(): String = stringResource(R.string.action_rime_dashboard)

        @Composable override fun WindowContents(keyboardShown: Boolean) {
            val view = LocalView.current
            RimeDashboardScreen { keyCode, metaState ->
                manager.sendKeyEvent(keyCode, metaState)
                manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
            }
        }
    } }
)
