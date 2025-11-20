package org.futo.inputmethod.latin

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.core.content.edit
//import androidx.work.Configuration
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked

class CrashLoggingApplication : Application() /*, Configuration.Provider*/ {
    //override val workManagerConfiguration: Configuration
    //    get() = Configuration.Builder().build()

    companion object {
        fun logPreferences(preferences: Preferences) {

        }

        fun CopyLogsOption() {

        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        if(isDirectBootUnlocked) {
            try {
                if (getSharedPreferences("migrate", MODE_PRIVATE).getBoolean(
                        "wiped_work",
                        false
                    ) == false
                ) {
                    deleteDatabase("androidx.work.workdb")
                    getSharedPreferences("androidx.work.util.preferences", MODE_PRIVATE)
                        .edit { clear() }
                    getSharedPreferences("migrate", MODE_PRIVATE)
                        .edit { putBoolean("wiped_work", true) }
                }
            } catch(e: Exception) {
                e.printStackTrace()
            }
        }
    }
}