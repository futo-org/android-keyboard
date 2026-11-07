package org.futo.inputmethod.latin.uix.settings

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SYSTEM_VOICE_INPUT_PACKAGE
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.theme.UixThemeAuto

class VoiceInputSwitchActivity : ComponentActivity() {
    private var targetPackage: String? = null
    private val label = mutableStateOf("?")

    private fun switch() {
        runBlocking {
            setSetting(SYSTEM_VOICE_INPUT_PACKAGE, targetPackage!!)
            setSetting(USE_SYSTEM_VOICE_INPUT, true)
        }
    }

    private fun updateContent() {
        setContent {
            DataStoreCacheProvider {
                UixThemeAuto {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(Modifier.width(380.dp).height(240.dp).padding(8.dp), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Text(stringResource(R.string.voice_input_settings_backend_system_confirm, label.value))

                                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        setResult(RESULT_CANCELED)
                                        finish()
                                    }) {
                                        Text(stringResource(R.string.voice_input_settings_backend_system_confirm_no))
                                    }

                                    TextButton(onClick = {
                                        switch()
                                        setResult(65)
                                        finish()
                                    }) {
                                        Text(stringResource(R.string.voice_input_settings_backend_system_confirm_yes))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra("targetPackage")
        this.targetPackage = targetPackage

        if(targetPackage == null) {
            Log.e("VoiceInputSwitchActivity", "Called without a targetPackage.")
            finish()
            return
        }

        val mode = intent.getStringExtra("mode")
        when(mode) {
            "popup" -> {
                label.value = try {
                    packageManager.getPackageInfo(targetPackage, 0)?.let {
                        packageManager.getApplicationLabel(it.applicationInfo!!)
                    }!!.toString()
                }catch(e: Exception) {
                    e.printStackTrace()
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }

                updateContent()
            }
            "switch" -> {
                if(targetPackage.startsWith("org.futo.")) {
                    switch()
                    setResult(66)
                    finish()
                } else {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
            "check" -> {
                val current = getSetting(SYSTEM_VOICE_INPUT_PACKAGE)
                if(current == targetPackage) {
                    setResult(67)
                    finish()
                } else {
                    setResult(68)
                    finish()
                }
            }
            else -> {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

    }
}
