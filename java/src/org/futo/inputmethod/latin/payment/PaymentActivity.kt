package org.futo.inputmethod.latin.payment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.uix.settings.pages.PaymentScreenSwitch
import org.futo.inputmethod.latin.uix.theme.UixThemeAuto


class PaymentActivity : ComponentActivity() {
    private fun updateContent() {
        setContent {
            UixThemeAuto {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PaymentScreenSwitch(onExit = {
                        finish()
                    })
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateContent()
            }
        }
    }
}
