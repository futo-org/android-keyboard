package org.futo.inputmethod.latin.uix.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import org.futo.inputmethod.accessibility.AccessibilityUtils

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