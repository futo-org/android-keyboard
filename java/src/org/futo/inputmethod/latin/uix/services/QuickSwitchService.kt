package org.futo.inputmethod.latin.uix.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class QuickSwitchService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun switchToPreviousApp() {
        // GLOBAL_ACTION_TOGGLE_RECENTS should quickly switch to the last app
        performGlobalAction(GLOBAL_ACTION_TOGGLE_RECENTS)
    }

    companion object {
        var instance: QuickSwitchService? = null
    }
}
