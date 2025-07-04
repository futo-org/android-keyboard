package org.futo.inputmethod.latin

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.work.Configuration
import org.futo.voiceinput.shared.util.DebugLogger

class CrashLoggingApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        DebugLogger.init(this)
    }

    companion object {
        fun logPreferences(preferences: Preferences) {

        }
    }
}