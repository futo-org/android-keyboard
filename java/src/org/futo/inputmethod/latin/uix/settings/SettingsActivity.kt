package org.futo.inputmethod.latin.uix.settings

import android.app.Activity
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.deferGetSetting
import org.futo.inputmethod.latin.uix.theme.StatusBarColorSetter
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.inputmethod.latin.xlm.ModelPaths

private fun Context.isInputMethodEnabled(): Boolean {
    val packageName = packageName
    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    var found = false
    for (imi in imm.enabledInputMethodList) {
        if (packageName == imi.packageName) {
            found = true
        }
    }

    return found
}

private fun Context.isDefaultIMECurrent(): Boolean {
    val value = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)

    return value.startsWith(packageName)
}

public const val IMPORT_GGUF_MODEL_REQUEST = 71067309


class SettingsActivity : ComponentActivity() {
    private val themeOption: MutableState<ThemeOption?> = mutableStateOf(null)

    private val inputMethodEnabled = mutableStateOf(false)
    private val inputMethodSelected = mutableStateOf(false)

    private var wasImeEverDisabled = false

    companion object {
        private var pollJob: Job? = null
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun updateSystemState() {
        val inputMethodEnabled = isInputMethodEnabled()
        val inputMethodSelected = isDefaultIMECurrent()
        this.inputMethodEnabled.value = inputMethodEnabled
        this.inputMethodSelected.value = inputMethodSelected

        if(!inputMethodEnabled) {
            wasImeEverDisabled = true
        } else if(wasImeEverDisabled) {
            // We just went from inputMethodEnabled==false to inputMethodEnabled==true
            // This is because the user is in the input method settings screen and just turned on
            // our IME. We can bring them back here so that they don't have to press back button
            wasImeEverDisabled = false

            val intent = Intent()
            intent.setClass(this, SettingsActivity::class.java)
            intent.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            startActivity(intent)
        }

        if(!inputMethodEnabled || !inputMethodSelected) {
            if(pollJob == null || !pollJob!!.isActive) {
                pollJob = GlobalScope.launch {
                    systemStatePoller()
                }
            }
        }
    }

    private suspend fun systemStatePoller() {
        while(!this.inputMethodEnabled.value || !this.inputMethodSelected.value) {
            delay(200)
            updateSystemState()
        }
    }

    private fun updateContent() {
        setContent {
            themeOption.value?.let { themeOption ->
                val themeIdx = useDataStore(key = THEME_KEY.key, default = themeOption.key)
                val theme: ThemeOption = ThemeOptions[themeIdx.value] ?: themeOption
                UixThemeWrapper(theme.obtainColors(LocalContext.current)) {
                    StatusBarColorSetter()
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SetupOrMain(inputMethodEnabled.value, inputMethodSelected.value) {
                            SettingsNavigator()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateSystemState()
                updateContent()
            }
        }

        deferGetSetting(THEME_KEY) {
            val themeOptionFromSettings = ThemeOptions[it]
            val themeOption = when {
                themeOptionFromSettings == null -> VoiceInputTheme
                !themeOptionFromSettings.available(this) -> VoiceInputTheme
                else -> themeOptionFromSettings
            }

            this.themeOption.value = themeOption
        }
    }

    override fun onResume() {
        super.onResume()

        updateSystemState()
    }

    override fun onRestart() {
        super.onRestart()

        updateSystemState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == IMPORT_GGUF_MODEL_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                ModelPaths.importModel(this, uri)
            }
        }
    }
}