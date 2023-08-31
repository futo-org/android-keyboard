package org.futo.inputmethod.latin

import android.app.Application
import android.content.Context
import org.acra.config.dialog
import org.acra.config.httpSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender

class CrashLoggingApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        if(BuildConfig.ENABLE_ACRA) {
            initAcra {
                reportFormat = StringFormat.JSON

                dialog {
                    text = "FUTO Keyboard has crashed! Please send a report to help us fix this."
                    title = "Crash"
                    positiveButtonText = "Send Report"
                    negativeButtonText = "Ignore"
                    resTheme = android.R.style.Theme_DeviceDefault_Dialog
                }

                httpSender {
                    uri = BuildConfig.ACRA_URL
                    basicAuthLogin = BuildConfig.ACRA_USER
                    basicAuthPassword = BuildConfig.ACRA_PASSWORD
                    httpMethod = HttpSender.Method.POST
                }
            }
        }
    }
}