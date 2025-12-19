package org.futo.inputmethod.latin.uix.actions

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.theme.selector.ThemePicker

val ThemeAction = Action(
    icon = R.drawable.themes,
    name = R.string.action_theme_switcher_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    windowImpl = { manager, _ ->
        object : ActionWindow() {
            override val onlyShowAboveKeyboard: Boolean = true

            @Composable
            override fun windowName(): String {
                return stringResource(R.string.action_theme_switcher_title)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val context = LocalContext.current

                val openSettingsLambda = {
                    if(context.isDirectBootUnlocked && !manager.isDeviceLocked()) {
                        val intent = Intent()
                        intent.setClass(context, SettingsActivity::class.java)
                        intent.setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                        intent.putExtra("navDest", "themes")
                        context.startActivity(intent)
                    } else {
                        val toast = Toast.makeText(
                            context,
                            context.getString(R.string.action_clipboard_manager_error_device_locked_title),
                            Toast.LENGTH_SHORT
                        )

                        toast.show()

                    }
                }

                ThemePicker({ openSettingsLambda() }, openSettingsLambda)
            }
        }
    }
)