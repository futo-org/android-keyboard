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
import androidx.compose.ui.text.style.TextAlign
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
import org.futo.inputmethod.latin.LatinIMELegacy
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
fun ImportScreen(fileKind: FileKindAndInfo, file: String?, onApply: (FileKind, InputMethodSubtype) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val importing = remember { mutableStateOf(false) }
    val importingLanguage = remember { mutableStateOf("") }
    ScrollableList {
        ScreenTitle(title = "Resource Importer")

        if(fileKind.kind == FileKind.Invalid) {
            Text("This file does not appear to be a dictionary, voice input or transformer model. It may be an invalid file or corrupted. Please try a different file.")

            NavigationItem(
                title = "Close",
                style = NavigationItemStyle.MiscNoArrow,
                navigate = {
                    onCancel()
                }
            )
        } else {
            Text("You are importing a ${fileKind.kind.youAreImporting()}.", modifier = Modifier.padding(8.dp))

            Spacer(modifier = Modifier.height(32.dp))

            if(importing.value) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                Text("Importing for ${importingLanguage.value}", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    "Which language would you like to set the ${fileKind.kind.youAreImporting()} for?",
                    modifier = Modifier.padding(8.dp)
                )

                val languages = getActiveLanguages(context).let {
                    if(fileKind.guessedLanguage != null) {
                        it.filter { it.tag.lowercase() == fileKind.guessedLanguage.lowercase() || it.tag.split("_")[0].lowercase() == fileKind.guessedLanguage.split("_")[0].lowercase() }.let {
                            if(it.isEmpty()) {
                                Text("Warning: This file appears to be intended for a language (${fileKind.guessedLanguage}) which is not active", modifier = Modifier.padding(8.dp))
                                getActiveLanguages(context)
                            } else {
                                it
                            }
                        }
                    } else {
                        it
                    }
                }

                languages.forEach {
                    NavigationItem(
                        title = "${it.name} (${it.tag})",
                        style = NavigationItemStyle.MiscNoArrow,
                        navigate = {
                            importing.value = true
                            importingLanguage.value = it.name
                            onApply(fileKind.kind, it.inputMethodSubtype)
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

data class FileKindAndInfo(
    val kind: FileKind,
    val guessedLanguage: String?
)

fun determineFileKind(context: Context, file: Uri): FileKindAndInfo {
    val contentResolver = context.contentResolver

    return contentResolver.openInputStream(file)?.use { inputStream ->
        val array = ByteArray(4)
        inputStream.read(array)

        val voiceInputMagic = 0x6c6d6767.toUInt()
        val transformerMagic = 0x47475546.toUInt()
        val dictionaryMagic = 0x9bc13afe.toUInt()

        val magic = ByteBuffer.wrap(array).getInt().toUInt()

        when(magic) {
            voiceInputMagic -> FileKindAndInfo(FileKind.VoiceInput, null)
            transformerMagic -> FileKindAndInfo(FileKind.Transformer, null)
            dictionaryMagic -> {
                while(array[0] != 0x3A.toByte()) {
                    inputStream.read(array, 0, 1)
                }

                val chars: MutableList<Char> = mutableListOf()
                while(array[0] != 0x1F.toByte()) {
                    inputStream.read(array, 0, 1)
                    if(array[0] == 0x1F.toByte()) break

                    chars.add(array[0].toInt().toChar())
                }

                val language = String(chars.toCharArray())

                FileKindAndInfo(FileKind.Dictionary, language)
            }
            else -> FileKindAndInfo(FileKind.Invalid, null)
        }
    } ?: FileKindAndInfo(FileKind.Invalid, null)
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

    fun deleteResourceForLanguage(context: Context, kind: FileKind, locale: Locale) {
        val setting = kind.preferencesKeyFor(locale.toString())
        val value = runBlocking { context.getSetting(setting, "") }
        if(value.isNotBlank()) {
            runBlocking { context.setSetting(setting, "") }
            val file = File(context.getExternalFilesDir(null), value)
            file.delete()
        }

        LatinIMELegacy.mPendingDictionaryUpdate = true
    }
}

class ImportResourceActivity : ComponentActivity() {
    private val themeOption: MutableState<ThemeOption?> = mutableStateOf(null)
    private val fileBeingImported: MutableState<String?> = mutableStateOf(null)
    private val fileKind: MutableState<FileKindAndInfo> = mutableStateOf(FileKindAndInfo(FileKind.Invalid, null))
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
                    if(outputFile.exists()) { outputFile.delete() }

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
                        if(outputFile.exists()) { outputFile.delete() }

                        outputFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream, 1024)
                        }
                    }

                    // 2. Update reference
                    val key = fileKind.preferencesKeyFor(inputMethodSubtype.locale)
                    applicationContext.setSetting(key, outputFileName)
                }
            }
            LatinIMELegacy.mPendingDictionaryUpdate = true
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