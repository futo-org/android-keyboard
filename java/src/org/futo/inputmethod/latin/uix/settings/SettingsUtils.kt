package org.futo.inputmethod.latin.uix.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import org.futo.inputmethod.accessibility.AccessibilityUtils
import org.futo.inputmethod.latin.utils.UncachedInputMethodManagerUtils

@Composable
fun SetupOrMain(inputMethodEnabled: Boolean, inputMethodSelected: Boolean, micPermissionGrantedOrUsingSystem: Boolean, doublePackage: Boolean, main: @Composable () -> Unit) {
    val wasSetupActive = remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = LocalContext.current
    LaunchedEffect(inputMethodSelected, inputMethodEnabled, micPermissionGrantedOrUsingSystem) {
        wasSetupActive.value = wasSetupActive.value || !inputMethodEnabled || !inputMethodSelected || !micPermissionGrantedOrUsingSystem

        if(inputMethodSelected && inputMethodEnabled && micPermissionGrantedOrUsingSystem && wasSetupActive.value) {
            AccessibilityUtils.init(context)
            if(AccessibilityUtils.getInstance().isAccessibilityEnabled) {
                AccessibilityUtils.getInstance()
                    .announceForAccessibility(view, "FUTO Keyboard has been activated")
            }
        }
    }

    if (!inputMethodEnabled) {
        SetupEnableIME()
    } else if (!inputMethodSelected) {
        SetupChangeDefaultIME(doublePackage)
    } else if (!micPermissionGrantedOrUsingSystem) {
        SetupEnableMic()
    } else {
        main()
    }
}

// TODO: We should have one central source of enabled languages to share between
// keyboard and voice input. We need to pass current language to voice input action
// and restrict it to that when possible. If active language has no voice input support
// we must tell the user in the UI.
fun Context.openLanguageSettings() {
    val imm = getSystemService(ComponentActivity.INPUT_METHOD_SERVICE) as InputMethodManager

    val imi = UncachedInputMethodManagerUtils.getInputMethodInfoOf(
        packageName, imm
    ) ?: return
    val intent = Intent()
    intent.action = Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS
    intent.addCategory(Intent.CATEGORY_DEFAULT)
    intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, imi.id)
    startActivity(intent)
}
