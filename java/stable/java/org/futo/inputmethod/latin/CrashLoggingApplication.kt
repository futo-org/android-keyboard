package org.futo.inputmethod.latin

import android.app.Application
import android.content.Context
import android.os.UserManager
import androidx.datastore.preferences.core.Preferences
import androidx.work.Configuration
import org.acra.ACRA
import org.acra.config.dialog
//import org.acra.config.httpSender
//import org.acra.sender.HttpSender
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class CrashLoggingApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()


    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        if(userManager.isUserUnlocked) {
            println("Initializing ACRA, as user is unlocked")
            initAcra {
                reportFormat = StringFormat.JSON

                dialog {
                    text = getString(
                        //if(BuildConfig.ENABLE_ACRA) {
                        //    R.string.crashed_text
                        //} else {
                        R.string.crashed_text_email
                        //}
                    )
                    title = getString(R.string.crashed_title)
                    positiveButtonText = getString(R.string.crash_report_accept)
                    negativeButtonText = getString(R.string.crash_report_reject)
                    resTheme = android.R.style.Theme_DeviceDefault_Dialog
                }


                //if(BuildConfig.ENABLE_ACRA) {
                //    httpSender {
                //        uri = BuildConfig.ACRA_URL
                //        basicAuthLogin = BuildConfig.ACRA_USER
                //        basicAuthPassword = BuildConfig.ACRA_PASSWORD
                //        httpMethod = HttpSender.Method.POST
                //    }
                //} else {
                mailSender {
                    mailTo = "keyboard@futo.org"
                    reportAsFile = true
                    reportFileName = "Crash.txt"
                    subject = "Keyboard Crash Report"
                    body =
                        "I experienced this crash. My version: ${BuildConfig.VERSION_NAME}.\n\n(Enter details here if necessary)"
                }
                //}
            }

            acraInitialized = true
        } else {
            println("Skipping ACRA, as user is locked")
        }
    }

    companion object {
        var acraInitialized = false

        fun logPreferences(preferences: Preferences) {
            if(acraInitialized) {
                preferences.asMap().forEach {
                    ACRA.errorReporter.putCustomData(it.key.name, it.value.toString())
                }
            }
        }
    }
}