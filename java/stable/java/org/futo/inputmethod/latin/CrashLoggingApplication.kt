package org.futo.inputmethod.latin

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import org.acra.ACRA
import org.acra.config.dialog
//import org.acra.config.httpSender
//import org.acra.sender.HttpSender
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class CrashLoggingApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

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
    }

    companion object {
        fun logPreferences(preferences: Preferences) {
            preferences.asMap().forEach {
                ACRA.errorReporter.putCustomData(it.key.name, it.value.toString())
            }
        }
    }
}