package org.futo.inputmethod.latin.uix.settings

import android.app.Activity
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProviderOwner
import org.futo.inputmethod.latin.uix.EXPORT_SETTINGS_REQUEST
import org.futo.inputmethod.latin.uix.IMPORT_SETTINGS_REQUEST
import org.futo.inputmethod.latin.uix.ImportResourceActivity
import org.futo.inputmethod.latin.uix.SettingsExporter
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.getSettingFlow
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.UixThemeAuto
import org.futo.inputmethod.latin.uix.theme.getThemeOption
import org.futo.inputmethod.latin.uix.theme.orDefault
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.inputmethod.updates.checkForUpdateAndSaveToPreferences
import org.futo.inputmethod.v2keyboard.LayoutManager
import java.io.File
import kotlin.math.sqrt

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

    return value.startsWith("$packageName/")
}

private fun Context.isDoublePackage(): Boolean {
    val value = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    val standalonePackage = "org.futo.inputmethod.latin"
    val playstorePackage = "org.futo.inputmethod.latin.playstore"

    return (value.startsWith("$standalonePackage/") && packageName == playstorePackage) || (value.startsWith("$playstorePackage/") && packageName == standalonePackage)
}

public const val IMPORT_GGUF_MODEL_REQUEST = 71067309
public const val EXPORT_GGUF_MODEL_REQUEST = 80595439


class SettingsActivity : ComponentActivity(), DynamicThemeProviderOwner {
    private val themeOption: MutableState<ThemeOption?> = mutableStateOf(null)

    private val inputMethodEnabled = mutableStateOf(false)
    private val inputMethodSelected = mutableStateOf(false)
    private val doublePackage = mutableStateOf(false)

    private var wasImeEverDisabled = false

    private var fileBeingSaved: File? = null
    fun updateFileBeingSaved(to: File) {
        fileBeingSaved = to
    }

    companion object {
        private var pollJob: Job? = null
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun updateSystemState() {
        val inputMethodEnabled = isInputMethodEnabled()
        val inputMethodSelected = isDefaultIMECurrent()
        this.inputMethodEnabled.value = inputMethodEnabled
        this.inputMethodSelected.value = inputMethodSelected

        this.doublePackage.value = this.doublePackage.value || isDoublePackage()

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

    val navController = NavHostController(this).apply {
        //navigatorProvider.addNavigator(ComposeNavGraphNavigator(navigatorProvider))
        navigatorProvider.addNavigator(ComposeNavigator())
        navigatorProvider.addNavigator(DialogNavigator())
    }

    private fun updateContent() {
        setContent {
            DataStoreCacheProvider {
                SharedPrefsCacheProvider {
                    UixThemeAuto {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(Modifier.safeDrawingPadding()) {
                                SetupOrMain(
                                    inputMethodEnabled.value,
                                    inputMethodSelected.value,
                                    doublePackage.value
                                ) {
                                    SettingsNavigator(navController = navController)
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

        LayoutManager.init(this)

        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateSystemState()
                updateContent()
            }
        }

        lifecycleScope.launch {
            checkForUpdateAndSaveToPreferences(applicationContext)
        }

        lifecycleScope.launch {
            getSettingFlow(THEME_KEY).collect {
                val themeOption = getThemeOption(this@SettingsActivity, it).orDefault(this@SettingsActivity)

                this@SettingsActivity.themeOption.value = themeOption
                this@SettingsActivity.themeProvider = BasicThemeProvider(
                    context = this@SettingsActivity,
                    colorScheme = themeOption.obtainColors(this@SettingsActivity)
                )

                updateEdgeToEdge()
            }
        }

        getSettingBlocking(THEME_KEY).let {
            val themeOption = getThemeOption(this, it).orDefault(this@SettingsActivity)

            this.themeOption.value = themeOption
            this.themeProvider = BasicThemeProvider(
                context = this@SettingsActivity,
                colorScheme = themeOption.obtainColors(this@SettingsActivity)
            )

            updateEdgeToEdge()
        }

        val intent = intent
        if(intent != null) {
            val destination = intent.getStringExtra("navDest")
            if(destination != null) {
                lifecycleScope.launch {
                    // The navigation graph has to initialize, and this can take some time.
                    // For now, just keep trying every 100ms until it doesn't throw an exception
                    // for up to 10 seconds
                    var navigated = false
                    for(i in 0 until 100) {
                        delay(100L)
                        try {
                            navController.navigate(destination)
                            navigated = true
                        } catch (ignored: Exception) {

                        }

                        if(navigated) break
                    }
                }
            }
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

    val exportInProgress = mutableStateOf(0)
    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(resultCode != Activity.RESULT_OK){
            if(requestCode == EXPORT_SETTINGS_REQUEST) {
                exportInProgress.value = 0
            }
            return
        }

        if(requestCode == IMPORT_GGUF_MODEL_REQUEST || requestCode == IMPORT_SETTINGS_REQUEST) {
            data?.data?.also { uri ->
                val intent = Intent()
                intent.setClass(this, ImportResourceActivity::class.java)
                intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                intent.setData(uri)
                startActivity(intent)
            }
        } else if(requestCode == EXPORT_GGUF_MODEL_REQUEST && fileBeingSaved != null) {
            data?.data?.also { uri ->
                ModelPaths.exportModel(this, uri, fileBeingSaved!!)
                navController.navigateToInfo(
                    "Model Exported",
                    "Model saved to file"
                )
            }
        } else if(requestCode == EXPORT_SETTINGS_REQUEST) {
            lifecycleScope.launch {
                exportInProgress.value = 2
                withContext(Dispatchers.IO) {
                    data?.data?.let { uri ->
                        contentResolver.openOutputStream(uri)!!
                    }?.use {
                        SettingsExporter.exportSettings(this@SettingsActivity, it, true)
                    }
                }
                exportInProgress.value = 0
            }
        }
    }

    // Provides theme for keyboard preview
    private var themeProvider: BasicThemeProvider? = null
    override fun getDrawableProvider(): DynamicThemeProvider {
        return themeProvider!!
    }

    fun updateEdgeToEdge() {
        themeProvider?.let {
            val color = it.keyboardColor

            val luminance = sqrt(
                0.299 * Color.red(color) / 255.0
                        + 0.587 * Color.green(color) / 255.0
                        + 0.114 * Color.blue(color) / 255.0
            )

            if (luminance > 0.5 && color != android.graphics.Color.TRANSPARENT) {
                enableEdgeToEdge(
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            } else {
                enableEdgeToEdge(
                    SystemBarStyle.dark(Color.TRANSPARENT),
                    SystemBarStyle.dark(Color.TRANSPARENT)
                )
            }

        }
    }
}