package org.futo.inputmethod.latin.uix

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodSubtype
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.Dictionary
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.ReadOnlyBinaryDictionary
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.uix.settings.DataStoreItem
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.StatusBarColorSetter
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.voiceinput.shared.BUILTIN_ENGLISH_MODEL
import org.futo.voiceinput.shared.types.ModelFileFile
import org.futo.voiceinput.shared.types.ModelLoader
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale


data class InputLanguage(
    val tag: String,
    val name: String,
    val inputMethodSubtype: InputMethodSubtype
)

fun getActiveLanguages(context: Context): List<InputLanguage> {
    RichInputMethodManager.init(context)

    return RichInputMethodManager.getInstance().getMyEnabledInputMethodSubtypeList(true).map {
        val name = SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(it)

        InputLanguage(it.locale, name, it)
    }.toList()
}


fun FileKind.preferencesKeyFor(locale: String): Preferences.Key<String> {
    assert(this != FileKind.Invalid)
    return stringPreferencesKey("resource_${name}_${locale}")
}

@Composable
fun resourceOption(language: InputMethodSubtype, kind: FileKind): DataStoreItem<String> {
    return useDataStore(key = kind.preferencesKeyFor(language.locale), default = "")
}

@Composable
fun ImportScreen(fileKind: FileKind, file: String?, onApply: (FileKind, InputMethodSubtype) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val importing = remember { mutableStateOf(false) }
    ScrollableList {
        ScreenTitle(title = "Resource Importer")

        if(fileKind == FileKind.Invalid) {
            Text("This file does not appear to be a dictionary, voice input or transformer model. It may be an invalid file or corrupted. Please try a different file.")

            NavigationItem(
                title = "Close",
                style = NavigationItemStyle.MiscNoArrow,
                navigate = {
                    onCancel()
                }
            )
        } else {
            Text("You are importing a ${fileKind.youAreImporting()}.", modifier = Modifier.padding(8.dp))

            Spacer(modifier = Modifier.height(32.dp))

            if(importing.value) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else {
                Text(
                    "Which language would you like to set the ${fileKind.youAreImporting()} for?",
                    modifier = Modifier.padding(8.dp)
                )

                getActiveLanguages(context).forEach {
                    NavigationItem(
                        title = "${it.name} (${it.tag})",
                        style = NavigationItemStyle.MiscNoArrow,
                        navigate = {
                            importing.value = true
                            onApply(fileKind, it.inputMethodSubtype)
                        }
                    )
                }
            }
        }
    }
}

enum class FileKind {
    VoiceInput,
    Transformer,
    Dictionary,
    Invalid
}

fun FileKind.youAreImporting(): String {
    return when(this) {
        FileKind.VoiceInput -> "voice input model"
        FileKind.Transformer -> "transformer model"
        FileKind.Dictionary -> "dictionary"
        FileKind.Invalid -> "invalid file"
    }
}

fun FileKind.extension(): String {
    return when(this) {
        FileKind.VoiceInput -> ".bin"
        FileKind.Transformer -> ".gguf"
        FileKind.Dictionary -> ".dict"
        FileKind.Invalid -> ""
    }
}

fun determineFileKind(context: Context, file: Uri): FileKind {
    val contentResolver = context.contentResolver

    return contentResolver.openInputStream(file)?.use { inputStream ->
        val array = ByteArray(4)
        inputStream.read(array)

        val voiceInputMagic = 0x6c6d6767.toUInt()
        val transformerMagic = 0x47475546.toUInt()
        val dictionaryMagic = 0x9bc13afe.toUInt()

        val magic = ByteBuffer.wrap(array).getInt().toUInt()

        when(magic) {
            voiceInputMagic -> FileKind.VoiceInput
            transformerMagic -> FileKind.Transformer
            dictionaryMagic -> FileKind.Dictionary
            else -> FileKind.Invalid
        }
    } ?: FileKind.Invalid
}

object ResourceHelper {
    suspend fun findFileForKind(context: Context, locale: Locale, kind: FileKind): File? {
        val keysToTry = listOf(
            locale.language,
            "${locale.language}_${locale.country}",
            "${locale.language.lowercase()}_${locale.country.uppercase()}",
        )

        val settingValue: String = keysToTry.firstNotNullOfOrNull { key ->
            context.getSetting(kind.preferencesKeyFor(key), "").ifEmpty { null }
        } ?: return null

        val file = File(context.getExternalFilesDir(null), settingValue)

        if(!file.exists()) {
            return null
        }

        return file
    }

    fun tryFindingVoiceInputModelForLocale(context: Context, locale: Locale): ModelLoader? {
        val file = runBlocking { findFileForKind(context, locale, FileKind.VoiceInput) }
            ?: return if(locale.language == "en") {
                BUILTIN_ENGLISH_MODEL
            } else {
                null
            }

        return ModelFileFile(R.string.externally_imported_model, file)
    }

    fun tryOpeningCustomMainDictionaryForLocale(context: Context, locale: Locale): ReadOnlyBinaryDictionary? {
        val file = runBlocking { findFileForKind(context, locale, FileKind.Dictionary) } ?: return null

        return ReadOnlyBinaryDictionary(
            file.absolutePath,
            0,
            file.length(),
            false,
            locale,
            Dictionary.TYPE_MAIN
        )
    }
}

class ImportResourceActivity : ComponentActivity() {
    private val themeOption: MutableState<ThemeOption?> = mutableStateOf(null)
    private val fileBeingImported: MutableState<String?> = mutableStateOf(null)
    private val fileKind: MutableState<FileKind> = mutableStateOf(FileKind.Invalid)
    private var uri: Uri? = null

    private fun applySetting(fileKind: FileKind, inputMethodSubtype: InputMethodSubtype) {
        val outputFileName = "${fileKind.name.lowercase()}_${inputMethodSubtype.locale}${fileKind.extension()}"

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // This is a special case for now
                if (fileKind == FileKind.Transformer) {
                    // 1. Copy file
                    val contentResolver = applicationContext.contentResolver
                    val outDirectory = ModelPaths.getModelDirectory(applicationContext)
                    val outputFile = File(outDirectory, outputFileName)
                    contentResolver.openInputStream(uri!!)!!.use { inputStream ->
                        outputFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream, 1024)
                        }
                    }

                    // 2. Update reference
                    val language = inputMethodSubtype.locale.split("_").first()
                    ModelPaths.updateModelOption(applicationContext, language, outputFile)
                } else {
                    // 1. Copy file
                    val contentResolver = applicationContext.contentResolver
                    contentResolver.openInputStream(uri!!)!!.use { inputStream ->
                        val outputFile =
                            File(applicationContext.getExternalFilesDir(null), outputFileName)

                        outputFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream, 1024)
                        }
                    }

                    // 2. Update reference
                    val key = fileKind.preferencesKeyFor(inputMethodSubtype.locale)
                    applicationContext.setSetting(key, outputFileName)
                }
            }
            finish()
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
                        ImportScreen(
                            fileKind = fileKind.value,
                            file = fileBeingImported.value,
                            onApply = { fileKind, inputMethodSubtype -> applySetting(fileKind, inputMethodSubtype) },
                            onCancel = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.uri = intent?.data!!

        val filePath = intent?.data?.path
        fileBeingImported.value = filePath
        fileKind.value = determineFileKind(applicationContext, uri!!)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
}