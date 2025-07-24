package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionBarHeight
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.CloseResult
import org.futo.inputmethod.latin.uix.TutorialMode
import org.futo.inputmethod.v2keyboard.KeyboardMode
import org.futo.inputmethod.v2keyboard.KeyboardSizingCalculator
import org.futo.inputmethod.latin.uix.theme.Typography

@Composable
internal fun RowScope.KeyboardMode(iconRes: Int, checkedIconRes: Int, name: String, sizingCalculator: KeyboardSizingCalculator, mode: KeyboardMode, isChecked: Boolean) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .weight(1.0f)
            .height(54.dp),
        onClick = {
            sizingCalculator.editSavedSettings { settings ->
                settings.copy(
                    currentMode = mode
                ).let {
                    // Set prefersSplit
                    when (mode) {
                        KeyboardMode.Split -> it.copy(prefersSplit = true)
                        KeyboardMode.Regular -> it.copy(prefersSplit = false)
                        else -> it
                    }
                }
            }
        },
        contentColor = if(isChecked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground
    ) {
        Box(Modifier.height(54.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(if(isChecked) checkedIconRes else iconRes),
                    contentDescription = null
                )
                Text(name, style = Typography.SmallMl)
            }
        }
    }
}

val KeyboardModeAction = Action(
    icon = R.drawable.keyboard_gear,
    name = R.string.action_keyboard_modes_title,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        val sizeCalculator = manager.getSizingCalculator()
        object : ActionWindow() {
            override val showCloseButton: Boolean
                get() = false

            override val onlyShowAboveKeyboard: Boolean
                get() = true

            override val fixedWindowHeight: Dp?
                get() = 54.dp + ActionBarHeight

            @Composable
            override fun windowName(): String =
                stringResource(R.string.action_keyboard_modes_title)

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val currMode = sizeCalculator.getSavedSettings().currentMode
                Column {
                    Row(Modifier.height(ActionBarHeight)) {
                        // Hide the back button in the resize tutorial
                        if(manager.getTutorialMode() != TutorialMode.ResizerTutorial) {
                            IconButton(onClick = {
                                manager.closeActionWindow()
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_keyboard_modes_go_back))
                            }
                        }
                        Spacer(Modifier.weight(1.0f))
                        TextButton(onClick = {
                            manager.showResizer()

                            if(manager.getTutorialMode() == TutorialMode.ResizerTutorial) {
                                manager.markTutorialCompleted()
                            }
                        }, Modifier.onGloballyPositioned {
                            if(manager.getTutorialMode() == TutorialMode.ResizerTutorial) {
                                manager.setTutorialArrowPosition(it)
                            }
                        }) {
                            Text(stringResource(R.string.action_keyboard_modes_resize_keyboard), style = Typography.Body.MediumMl)
                        }
                    }
                    Row {
                        KeyboardMode(
                            R.drawable.keyboard_regular,
                            R.drawable.keyboard_fill_check,
                            stringResource(R.string.action_keyboard_modes_standard),
                            sizeCalculator, KeyboardMode.Regular,
                            currMode == KeyboardMode.Regular
                        )

                        KeyboardMode(
                            R.drawable.keyboard_left_handed,
                            R.drawable.keyboard_left_handed_fill_check,
                            stringResource(R.string.action_keyboard_modes_one_handed),
                            sizeCalculator, KeyboardMode.OneHanded,
                            currMode == KeyboardMode.OneHanded
                        )

                        KeyboardMode(
                            R.drawable.keyboard_split,
                            R.drawable.keyboard_split_fill_check,
                            stringResource(R.string.action_keyboard_modes_split),
                            sizeCalculator, KeyboardMode.Split,
                            currMode == KeyboardMode.Split
                        )

                        KeyboardMode(
                            R.drawable.keyboard_float,
                            R.drawable.keyboard_float_fill_check,
                            stringResource(R.string.action_keyboard_modes_floating),
                            sizeCalculator, KeyboardMode.Floating,
                            currMode == KeyboardMode.Floating
                        )
                    }
                }
            }

            override fun close(): CloseResult {
                if(manager.getTutorialMode() == TutorialMode.ResizerTutorial) {
                    manager.markTutorialCompleted()
                }
                return CloseResult.Default
            }
        }
    },
)