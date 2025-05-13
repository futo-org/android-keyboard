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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScreenTitleWithIcon
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.StatusBarColorSetter
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.orDefault
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.voiceinput.shared.BUILTIN_ENGLISH_MODEL
import org.futo.voiceinput.shared.types.ModelFileFile
import org.futo.voiceinput.shared.types.ModelLoader
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Locale


data class InputLanguage(
    val tag: String,
    val name: String,
    val inputMethodSubtype: InputMethodSubtype
)

fun getActiveLanguages(context: Context): List<InputLanguage> {
    SubtypeLocaleUtils.init(context)
    return context.getSettingBlocking(SubtypesSetting)
        .let { Subtypes.layoutsMappedByLanguage(it) }
        .map {
        InputLanguage(
            it.value.first().locale,
            Subtypes.getName(it.value.first()),
            it.value.first()
        )
    }
}


fun FileKind.preferenceKeyFor(locale: String): Preferences.Key<String> {
    assert(this != FileKind.Invalid)
    val locale = locale.replace("#", "H")
    return stringPreferencesKey("resource_${name}_${locale}")
}

fun FileKind.namePreferenceKeyFor(locale: String): Preferences.Key<String> {
    assert(this != FileKind.Invalid)
    val locale = locale.replace("#", "H")
    return stringPreferencesKey("resourcename_${name}_${locale}")
}

@Composable
fun SettingsImportScreen(
    metadata: SettingsExporter.CfgFileMetadata,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val importing = remember { mutableStateOf(false) }
    ScrollableList {
        ScreenTitle(title = stringResource(R.string.resource_importer_import_title, stringResource(R.string.file_kind_cfg_backup)))

        if(importing.value) {
            Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            Text(stringResource(R.string.resource_importer_importing_cfg), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp))
        } else {
            if(metadata.isNewer) {
                Tip("⚠\uFE0F " + stringResource(R.string.resource_importer_warning_cfg_backup_newer_version))
            }
            Text(stringResource(R.string.resource_importer_file_info, metadata.dateExported.toString()),
                modifier = Modifier.padding(16.dp, 8.dp))
            Text(stringResource(R.string.resource_importer_warning_cfg_backup_is_destructive),
                modifier = Modifier.padding(16.dp, 8.dp))
            Spacer(modifier = Modifier.height(32.dp))
            NavigationItem(
                title = stringResource(R.string.resource_importer_import_button),
                style = NavigationItemStyle.MiscNoArrow,
                navigate = {
                    onApply()
                    importing.value = true
                }
            )
            NavigationItem(
                title = stringResource(R.string.resource_importer_cancel_button),
                style = NavigationItemStyle.MiscNoArrow,
                navigate = {
                    onCancel()
                }
            )
        }
    }
}

@Composable
fun ImportScreen(fileKind: FileKindAndInfo, file: String?, onApply: (FileKindAndInfo, InputMethodSubtype) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val importing = remember { mutableStateOf(false) }
    val importingLanguage = remember { mutableStateOf("") }
    ScrollableList {
        ScreenTitleWithIcon(title = stringResource(R.string.resource_importer_import_title, fileKind.kind.kindTitle(context)), painter = painterResource(id = fileKind.kind.icon()))

        if(fileKind.kind == FileKind.Invalid) {
            if(fileKind.invalidKindHint == InvalidFileHint.ImportedWordListInsteadOfDict) {
                Text(stringResource(R.string.resource_importer_error_wordlist_1))
                Tip(stringResource(R.string.resource_importer_error_wordlist_2))
            } else {
                Text(stringResource(R.string.resource_importer_error_invalid_fiile))
            }

            NavigationItem(
                title = stringResource(R.string.resource_importer_cancel_button),
                style = NavigationItemStyle.MiscNoArrow,
                navigate = {
                    onCancel()
                }
            )
        } else {
            fileKind.name?.let {
                Text(stringResource(R.string.resource_importer_file_info, it), modifier = Modifier.padding(16.dp, 8.dp))
                Spacer(modifier = Modifier.height(32.dp))
            }

            if(importing.value) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                Text(stringResource(R.string.resource_importer_importing, importingLanguage.value), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    stringResource(R.string.resource_importer_select_language),
                    modifier = Modifier.padding(16.dp, 8.dp)
                )

                val languages = getActiveLanguages(context).let {
                    if(fileKind.locale != null) {
                        it.filter { it.tag.lowercase() == fileKind.locale.lowercase() || it.tag.split("_")[0].lowercase() == fileKind.locale.split("_")[0].lowercase() }.let {
                            if(it.isEmpty()) {
                                Tip("⚠\uFE0F " +
                                    stringResource(
                                        R.string.resource_importer_warning_language_missing,
                                        fileKind.locale
                                    ))
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
                        style = NavigationItemStyle.Misc,
                        navigate = {
                            importing.value = true
                            importingLanguage.value = it.name
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
    Invalid;

    fun getAddonUrlForLocale(locale: Locale?): String {
        return when(this) {
            VoiceInput -> "https://keyboard.futo.org/voice-input-models?locale=${locale?.toLanguageTag() ?: ""}"
            Transformer -> "https://keyboard.futo.org/models?locale=${locale?.toLanguageTag() ?: ""}"
            Dictionary -> "https://keyboard.futo.org/dictionaries?locale=${locale?.toLanguageTag() ?: ""}"
            Invalid -> "https://keyboard.futo.org/"
        }
    }
}

fun FileKind.kindTitle(context: Context): String {
    return when(this) {
        FileKind.VoiceInput -> context.getString(R.string.file_kind_voice_input_model)
        FileKind.Transformer -> context.getString(R.string.file_kind_transformer_model)
        FileKind.Dictionary -> context.getString(R.string.file_kind_dictionary)
        FileKind.Invalid -> context.getString(R.string.file_kind_invalid_file)
    }
}

fun FileKind.icon(): Int {
    return when(this) {
        FileKind.VoiceInput -> R.drawable.mic
        FileKind.Transformer -> R.drawable.cpu
        FileKind.Dictionary -> R.drawable.book
        FileKind.Invalid -> R.drawable.close
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

enum class InvalidFileHint {
    ImportedWordListInsteadOfDict
}

data class FileKindAndInfo(
    val kind: FileKind,
    val name: String?,
    val locale: String?,
    val invalidKindHint: InvalidFileHint? = null
)

private fun parseDictionaryMetadataKV(inputStream: InputStream): Map<String, String>? {
    while (inputStream.available() > 0) {
        val v = inputStream.read()
        if(v == -1) {
            return null
        } else if (v == 'd'.code) {
            if (inputStream.read() == 'a'.code
                && inputStream.read() == 't'.code
                && inputStream.read() == 'e'.code
                && inputStream.read() == 0x1F
            ) {
                break
            } else {
                continue
            }
        }
    }

    val readUntilSeparator = {
        val codes: MutableList<Int> = mutableListOf()
        while(true) {
            val v = inputStream.read()
            if(v == -1) {
                break
            } else if(v == 0x1F) {
                break
            } else if(v == 0) {
                // 3 byte character
                // not 100% sure it's correct to compare to 0 here, but seems to work usually
                val v1 = v
                val v2 = inputStream.read()
                val v3 = inputStream.read()

                // sanity check
                if(v2 == -1 || v3 == -1 || v2 == 0x1F || v3 == 0x1F) break

                codes.add((v1 shl 16) or (v2 shl 8) or (v3))
            } else {
                codes.add(v)
            }
        }

        String(codes.toIntArray(), 0, codes.size)
    }

    val keyValueList = mutableMapOf(
        "date" to readUntilSeparator()
    )

    while(true) {
        val key = readUntilSeparator()
        val value = readUntilSeparator()

        if(key.isBlank() || value.isBlank()) {
            break
        }

        keyValueList[key] = value

        if(key == "version") {
            break
        }
    }

    return keyValueList
}

fun determineFileKind(context: Context, file: Uri): FileKindAndInfo {
    val contentResolver = context.contentResolver

    return contentResolver.openInputStream(file)?.use { inputStream ->
        val array = ByteArray(4)
        inputStream.read(array)

        val voiceInputMagic = 0x6c6d6767.toUInt()
        val transformerMagic = 0x47475546.toUInt()
        val dictionaryMagic = 0x9bc13afe.toUInt()

        val magic = ByteBuffer.wrap(array).getInt().toUInt()

        when {
            magic == voiceInputMagic -> FileKindAndInfo(FileKind.VoiceInput, null, null)
            magic == transformerMagic -> FileKindAndInfo(FileKind.Transformer, null, null)
            magic == dictionaryMagic -> {
                val metadata = parseDictionaryMetadataKV(inputStream)

                FileKindAndInfo(
                    FileKind.Dictionary,
                    name = metadata?.get("description"),
                    locale = metadata?.get("locale")
                )
            }

            (magic == 0x1f8b0808.toUInt()) || (magic == 0x1f8b0800.toUInt()) || (magic == 0x64696374.toUInt()) ->
                FileKindAndInfo(FileKind.Invalid, null, null, InvalidFileHint.ImportedWordListInsteadOfDict)

            else -> FileKindAndInfo(FileKind.Invalid, null, null)
        }
    } ?: FileKindAndInfo(FileKind.Invalid, null, null)
}

object ResourceHelper {
    val BuiltInVoiceInputFallbacks = mapOf(
        "en" to BUILTIN_ENGLISH_MODEL
    )

    suspend fun findKeyForLocaleAndKind(context: Context, locale: Locale, kind: FileKind): String? {
        val keysToTry = listOf(
            locale.toString(),
            locale.language,
            "${locale.language}_${locale.country.ifEmpty { locale.language }}",
            "${locale.language.lowercase()}_${locale.country.ifEmpty { locale.language }.uppercase()}",
        )

        val key: String = keysToTry.firstNotNullOfOrNull { key ->
            context.getSetting(kind.preferenceKeyFor(key), "").ifEmpty { null }?.let { key }
        } ?: return null

        return key
    }

    suspend fun findFileForKind(context: Context, locale: Locale, kind: FileKind): File? {
        val key = findKeyForLocaleAndKind(context, locale, kind) ?: return null

        val settingValue: String = context.getSetting(kind.preferenceKeyFor(key), "")

        val file = File(context.getExternalFilesDir(null), settingValue)

        if(!file.exists()) {
            return null
        }

        return file
    }

    fun tryFindingVoiceInputModelForLocale(context: Context, locale: Locale): ModelLoader? {
        val file = runBlocking { findFileForKind(context, locale, FileKind.VoiceInput) }
            ?: return BuiltInVoiceInputFallbacks[locale.language]

        return ModelFileFile(R.string.settings_external_model_name, file)
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
        val setting = kind.preferenceKeyFor(locale.toString())
        val value = runBlocking { context.getSetting(setting, "") }
        if(value.isNotBlank()) {
            val file = File(context.getExternalFilesDir(null), value)
            file.delete()
        }

        runBlocking { context.setSetting(kind.preferenceKeyFor(locale.toString()), "") }
        runBlocking { context.setSetting(kind.namePreferenceKeyFor(locale.toString()), "") }

        LatinIMELegacy.mPendingDictionaryUpdate = true
    }
}

class ImportResourceActivity : ComponentActivity() {
    private val themeOption: MutableState<ThemeOption?> = mutableStateOf(null)
    private val fileBeingImported: MutableState<String?> = mutableStateOf(null)
    private val fileKind: MutableState<FileKindAndInfo> = mutableStateOf(FileKindAndInfo(FileKind.Invalid, null, null))
    private val settingsCfgImportMetadata: MutableState<SettingsExporter.CfgFileMetadata?> = mutableStateOf(null)
    private var uri: Uri? = null

    private fun applySetting(fileKind: FileKindAndInfo, inputMethodSubtype: InputMethodSubtype) {
        val sanitizedLocaleForFilename = inputMethodSubtype.locale.replace("#", "H")
        val outputFileName = "${fileKind.kind.name.lowercase()}_$sanitizedLocaleForFilename${fileKind.kind.extension()}"

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // This is a special case for now
                if (fileKind.kind == FileKind.Transformer) {
                    // 1. Copy file
                    val contentResolver = applicationContext.contentResolver
                    val outDirectory = ModelPaths.getModelDirectory(applicationContext)
                    val outputFile = File(outDirectory, outputFileName)
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }

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
                    applicationContext.setSetting(
                        fileKind.kind.preferenceKeyFor(inputMethodSubtype.locale),
                        outputFileName
                    )
                    fileKind.name?.let {
                        applicationContext.setSetting(
                            fileKind.kind.namePreferenceKeyFor(inputMethodSubtype.locale),
                            it
                        )
                    }
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
                        settingsCfgImportMetadata.value?.let {
                            SettingsImportScreen(
                                metadata = it,
                                onApply = {
                                    lifecycleScope.launch {
                                        withContext(Dispatchers.IO) {
                                            contentResolver.openInputStream(uri!!)!!.use {
                                                SettingsExporter.loadSettings(
                                                    this@ImportResourceActivity,
                                                    it,
                                                    true
                                                )
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            finish()
                                        }
                                    }
                                },
                                onCancel = {
                                    finish()
                                }
                            )
                            it
                        } ?: run {
                            ImportScreen(
                                fileKind = fileKind.value,
                                file = fileBeingImported.value,
                                onApply = { fileKind, inputMethodSubtype ->
                                    applySetting(
                                        fileKind,
                                        inputMethodSubtype
                                    )
                                },
                                onCancel = { finish() }
                            )
                        }
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
        settingsCfgImportMetadata.value = if(fileKind.value.kind == FileKind.Invalid) {
            SettingsExporter.getCfgFileMetadata(contentResolver.openInputStream(uri!!)!!)
        } else {
            null
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateContent()
            }
        }

        val key = getSetting(THEME_KEY)
        this.themeOption.value = ThemeOptions[key].orDefault(this)
    }
}