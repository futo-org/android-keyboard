package org.futo.inputmethod.latin.uix.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.accessibility.AccessibilityUtils

@Composable
fun SetupOrMain(inputMethodEnabled: Boolean, inputMethodSelected: Boolean, doublePackage: Boolean, main: @Composable () -> Unit) {
    val wasSetupActive = remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = LocalContext.current
    LaunchedEffect(inputMethodSelected, inputMethodEnabled) {
        wasSetupActive.value = wasSetupActive.value || !inputMethodEnabled || !inputMethodSelected

        if(inputMethodSelected && inputMethodEnabled && wasSetupActive.value) {
            AccessibilityUtils.init(context)
            if(AccessibilityUtils.getInstance().isAccessibilityEnabled) {
                AccessibilityUtils.getInstance()
                    .announceForAccessibility(view, "FUTO Keyboard has been activated")
            }
        }
    }

    if (!inputMethodEnabled) {
        SetupEnableIME()
    } else if (needsToShowDirectBootWarning()) {
        SetupDirectBootWarning()
    } else if (!inputMethodSelected) {
        SetupChangeDefaultIME(doublePackage)
    } else {
        main()
    }
}

/** Elements at the bottom of the settings list sometimes become uninteractable due to gesture
 *  navigation present on the user's device, some spacing at the bottom should fix it. */
@Composable
fun BottomSpacer() {
    Spacer(Modifier.height(80.dp))
}