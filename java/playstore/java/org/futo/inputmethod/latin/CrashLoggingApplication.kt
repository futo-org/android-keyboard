package org.futo.inputmethod.latin

import android.app.Application
import androidx.datastore.preferences.core.Preferences
//import androidx.work.Configuration

class CrashLoggingApplication : Application() /*, Configuration.Provider*/ {
    //override val workManagerConfiguration: Configuration
    //    get() = Configuration.Builder().build()

    companion object {
        fun logPreferences(preferences: Preferences) {

        }
    }
}