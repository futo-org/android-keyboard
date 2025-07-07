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
        // Use recents action to toggle to the previous app
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    companion object {
        var instance: QuickSwitchService? = null
    }
}
