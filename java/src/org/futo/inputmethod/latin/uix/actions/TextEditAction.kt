package org.futo.inputmethod.latin.uix.actions

import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.futo.inputmethod.latin.R
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
                    colorFilter = ColorFilter.tint(
                        iconColor
                    )
                )
            }
        }
    }
}

@Composable
fun RepeatableActionKey(
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
    contents: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if(isPressed) {
            onTrigger()
            delay(670L)
            while(isPressed) {
                onTrigger()
                delay(50L)
            }
        }
    }

    Surface(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { }
        ),
        color = MaterialTheme.colorScheme.primary
    ) {
        contents()
    }

}

@Composable
@Preview(showBackground = true)
fun TextEditScreen(onCodePoint: (Int) -> Unit = { _ -> }, onEvent: (Int, Int) -> Unit = { _, _ -> }) {
    var shiftState by remember { mutableStateOf(false) }
    var ctrlState by remember { mutableStateOf(false) }

    val metaState = 0 or
            (if(shiftState) { KeyEvent.META_SHIFT_ON } else { 0 }) or
            (if(ctrlState) { KeyEvent.META_CTRL_ON } else { 0 })


    val buttonColorsUntoggled = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    val buttonColorsToggled = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    )

    val sendEvent = { keycode: Int -> onEvent(keycode, metaState) }

    val keySize = 48.dp

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1.0f))

            Box(
                modifier = Modifier
                    .height(keySize * 2)
                    .width(keySize * 3)
            ) {
                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_INSERT) }
                ) {
                    Text("Ins", modifier = Modifier.padding(4.dp))
                }

                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_DEL) }
                ) {
                    Text("Del", modifier = Modifier.padding(4.dp))
                }

                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_MOVE_HOME) }
                ) {
                    Text("Home", modifier = Modifier.padding(4.dp))
                }

                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_MOVE_END) }
                ) {
                    Text("End", modifier = Modifier.padding(4.dp))
                }

                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_PAGE_UP) }
                ) {
                    Text("PgUp", modifier = Modifier.padding(4.dp))
                }

                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_PAGE_DOWN) }
                ) {
                    Text("PgDn", modifier = Modifier.padding(4.dp))
                }
            }

        }

        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.height(92.dp)) {
                Button(
                    onClick = { shiftState = !shiftState },
                    colors = if (shiftState) {
                        buttonColorsToggled
                    } else {
                        buttonColorsUntoggled
                    },
                    modifier = Modifier.align(Alignment.TopStart).width(keySize * 3)
                ) {
                    Row {
                        Text("Shift")
                    }
                }
                Button(
                    onClick = { ctrlState = !ctrlState },
                    colors = if (ctrlState) {
                        buttonColorsToggled
                    } else {
                        buttonColorsUntoggled
                    },
                    modifier = Modifier.align(Alignment.BottomStart).width(keySize * 2)
                ) {
                    Text("Ctrl")
                }
            }

            Spacer(modifier = Modifier.weight(1.0f))




            Box(
                modifier = Modifier
                    .height(keySize * 2)
                    .width(keySize * 3)
            ) {
                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_DPAD_UP) }
                ) {
                    IconWithColor(
                        iconId = R.drawable.arrow_up,
                        iconColor = MaterialTheme.colorScheme.onPrimary
                    )
                }


                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_DPAD_DOWN) }
                ) {
                    IconWithColor(
                        iconId = R.drawable.arrow_down,
                        iconColor = MaterialTheme.colorScheme.onPrimary
                    )
                }

                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_DPAD_LEFT) }
                ) {
                    IconWithColor(
                        iconId = R.drawable.arrow_left,
                        iconColor = MaterialTheme.colorScheme.onPrimary
                    )
                }

                RepeatableActionKey(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(keySize)
                        .height(keySize),
                    onTrigger = { sendEvent(KeyEvent.KEYCODE_DPAD_RIGHT) }
                ) {
                    IconWithColor(
                        iconId = R.drawable.arrow_right,
                        iconColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

val TextEditAction = Action(
    icon = R.drawable.edit_text,
    name = R.string.text_edit_action_title,
    simplePressImpl = null,
    persistentState = null,
    windowImpl = { manager, persistentState ->
        object : ActionWindow {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.text_edit_action_title)
            }

            @Composable
            override fun WindowContents() {
                TextEditScreen(onCodePoint = { a -> manager.sendCodePointEvent(a)}, onEvent = { a, b -> manager.sendKeyEvent(a, b) })
            }

            override fun close() {
            }
        }
    }
)