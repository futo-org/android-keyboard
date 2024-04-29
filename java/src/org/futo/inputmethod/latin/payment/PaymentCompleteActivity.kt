package org.futo.inputmethod.latin.payment

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.dataStore
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.pages.EXT_LICENSE_KEY
import org.futo.inputmethod.latin.uix.settings.pages.IS_ALREADY_PAID
import org.futo.inputmethod.latin.uix.settings.pages.IS_PAYMENT_PENDING
import org.futo.inputmethod.latin.uix.settings.pages.PaymentThankYouScreen
import org.futo.inputmethod.latin.uix.settings.pages.startAppActivity
import org.futo.inputmethod.latin.uix.theme.UixThemeAuto
import org.futo.inputmethod.updates.openURI

class PaymentCompleteActivity : ComponentActivity() {
    private fun updateContent() {
        setContent {
            UixThemeAuto {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PaymentThankYouScreen(onExit = {
                        startAppActivity(SettingsActivity::class.java, clearTop = true)
                        finish()
                    })
                }
            }
        }
    }

    private fun onPaid(license: String) {
        lifecycleScope.launch {
            dataStore.edit {
                it[IS_ALREADY_PAID.key] = true
                it[IS_PAYMENT_PENDING.key] = false
                it[EXT_LICENSE_KEY.key] = license
            }

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateContent()
            }
        }
    }

    private fun onInvalidKey() {
        lifecycleScope.launch {
            if(applicationContext.getSetting(IS_ALREADY_PAID)) {
                finish()
            } else {
                setContent {
                    UixThemeAuto {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column {
                                Text(
                                    getString(R.string.license_check_failed),
                                    modifier = Modifier.padding(8.dp)
                                )

                                NavigationItem(title = "Email keyboard@futo.org", style = NavigationItemStyle.Mail, navigate = {
                                    openURI("mailto:keyboard@futo.org")
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetData = intent.dataString
        if((targetData?.startsWith("futo-keyboard://license/") == true) || (targetData?.startsWith("futo-voice-input://license/") == true)) {
            onPaid("activate")
        } else {
            Log.e("PaymentCompleteActivity", "futo-keyboard launched with invalid targetData $targetData")
            finish()
        }
    }
}
