package org.futo.inputmethod.latin.uix.settings.pages.themes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.findActivity
import org.futo.inputmethod.latin.uix.settings.IMPORT_RESOURCE_FILE_REQUEST
import org.futo.inputmethod.latin.uix.settings.RotatingChevronIcon
import org.futo.inputmethod.latin.uix.settings.Route
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.ZipThemes
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.selector.ThemePicker
import org.futo.inputmethod.latin.uix.theme.selector.ZipThemePreview
import org.futo.inputmethod.latin.uix.urlEncode


@Composable
fun DeleteCustomThemeDialog(name: String, navController: NavHostController) {
    val context = LocalContext.current
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        icon = {
        },
        title = {
            Text(stringResource(R.string.theme_settings_custom_theme_delete_title), style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onPrimaryContainer)
        },
        text = {
            ZipThemePreview(ZipThemes.custom(name), true, Modifier, null) { }
        },
        onDismissRequest = {
            navController.navigateUp()
        },
        confirmButton = {
            OutlinedButton(onClick = {
                ZipThemes.delete(context, ZipThemes.custom(name))
                navController.navigateUp()
            }) {
                Text(stringResource(R.string.theme_settings_custom_theme_delete_confirm), color = MaterialTheme.colorScheme.primary, style = Typography.Body.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                navController.navigateUp()
            }) {
                Text(stringResource(R.string.theme_settings_custom_theme_delete_cancel), color = MaterialTheme.colorScheme.primary, style = Typography.Body.Medium)
            }
        }
    )
}

@Composable
fun CustomThemeDialog(navController: NavHostController = rememberNavController()) {
    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            navController.navigateUp()
            navController.navigate(Route.CustomTheme(uri.toString()))
        }
    )

    val context = LocalContext.current
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        icon = {
        },
        title = {
            Text(stringResource(R.string.theme_settings_add_new_theme), style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onPrimaryContainer)
        },
        text = {
            Text(stringResource(R.string.theme_settings_add_new_theme_question))
        },
        onDismissRequest = {
            navController.navigateUp()
        },
        confirmButton = {
            OutlinedButton(onClick = {
                pickLauncher.launch("image/*")
                //navController.navigateUp()
            }) {
                Text(stringResource(R.string.theme_customizer_select_background_image_button), color = MaterialTheme.colorScheme.primary, style = Typography.Body.Medium)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                }
                findActivity(context)!!.startActivityForResult(intent, IMPORT_RESOURCE_FILE_REQUEST)

                navController.navigateUp()
            }) {
                Text(stringResource(R.string.theme_customizer_load_custom_theme_file), color = MaterialTheme.colorScheme.primary, style = Typography.Body.Medium)
            }
        }
    )
}

@Preview
@Composable
fun ThemeScreen(navController: NavHostController = rememberNavController()) {
    val (theme, _) = useDataStore(THEME_KEY.key, THEME_KEY.default)

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
                            privateImeOptions = "org.futo.inputmethod.latin.NoSuggestions=1"
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
            ThemePicker({
                navController.navigate(Route.DeleteTheme(it))
            }, {
                navController.navigate("customThemeDialog")
            })
        }
    }
}