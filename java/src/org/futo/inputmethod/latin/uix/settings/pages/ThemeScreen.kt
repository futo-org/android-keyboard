package org.futo.inputmethod.latin.uix.settings.pages

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputSession
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.selector.ThemePicker
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.RotatingChevronIcon

@Preview
@Composable
fun ThemeScreen(navController: NavHostController = rememberNavController()) {
    val (theme, setTheme) = useDataStore(THEME_KEY.key, THEME_KEY.default)

    val context = LocalContext.current
    val enableKeyboardPreview = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    var showKeyboard by remember { mutableStateOf(false) }

    if (enableKeyboardPreview) {
        val textInputService = LocalTextInputService.current
        val rootView = (context as? Activity)?.window?.decorView?.rootView
        val session = remember { mutableStateOf<TextInputSession?>(null) }

        DisposableEffect(showKeyboard, theme) {
            val service = textInputService ?: return@DisposableEffect onDispose { }

            if (showKeyboard) {
                session.value = service.startInput(
                    TextFieldValue(""),
                    imeOptions = ImeOptions.Default.copy(
                        platformImeOptions = PlatformImeOptions(
                            privateImeOptions = "org.futo.inputmethod.latin"
                        )
                    ),
                    onEditCommand = { },
                    onImeActionPerformed = { }
                )
            }

            onDispose {
                service.stopInput(session.value ?: return@onDispose)
            }
        }

        // Detect manual keyboard dismissal (e.g., back button press)
        rootView?.let { view ->
            DisposableEffect(view) {
                ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                    val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                    if (!isKeyboardVisible && showKeyboard) {
                        showKeyboard = false
                    }
                    insets
                }

                onDispose {
                    ViewCompat.setOnApplyWindowInsetsListener(view, null)
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (enableKeyboardPreview) {
                SmallFloatingActionButton(
                    onClick = {
                        showKeyboard = !showKeyboard
                    }
                ) {
                    RotatingChevronIcon(!showKeyboard)
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Start
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ScreenTitle(stringResource(R.string.theme_settings_title), showBack = true, navController)
            ThemePicker {
                setTheme(it.key)
            }
        }
    }
}