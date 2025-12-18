package org.futo.inputmethod.latin.uix

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodSubtype
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.engine.GlobalIMEMessage
import org.futo.inputmethod.engine.IMEMessage
import org.futo.inputmethod.latin.Dictionary
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.ReadOnlyBinaryDictionary
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.localeFromString
import org.futo.inputmethod.latin.uix.actions.BugInfo
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScreenTitleWithIcon
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.pages.DevAutoAcceptThemeImport
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.ZipThemes
import org.futo.inputmethod.latin.uix.theme.StatusBarColorSetter
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.getThemeOption
import org.futo.inputmethod.latin.uix.theme.orDefault
import org.futo.inputmethod.latin.utils.Dictionaries
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.inputmethod.updates.openURI
import org.futo.voiceinput.shared.BUILTIN_ENGLISH_MODEL
import org.futo.voiceinput.shared.types.ModelFileFile
import org.futo.voiceinput.shared.types.ModelLoader
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest
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
            Text(stringResource(R.string.resource_importer_warning_cfg_backup_is_destructive2),
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
fun ImportScreen(fileKind: FileKindAndInfo, onApply: (FileKindAndInfo, InputMethodSubtype) -> Unit, onCancel: () -> Unit) {
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

data class DetectedUserDictFile(
    val kind: String,
    val name: String,
    val locale: Locale,
    val encoding: Charset?,
    val offset: Int = 0
)

private fun detectJapaneseUserDictInner(
    firstLine: String,
    inputStream: InputStream, // this is already positioned at the start of next line
    charset: Charset? // if null, it's either UTF-8 or SHIFT_JIS. otherwise it may be set to UTF-16LE, UTF-16BE, UTF-8
): DetectedUserDictFile? {
    val l = firstLine.lowercase()

    val msIme = l.startsWith("!microsoft ime")
    val tango = l.startsWith("!!atok_tango_text_header")
    val gboard = l.startsWith("#gboard dictionary version:1")
    if(!msIme && !tango && !gboard) return null

    val lines = mutableListOf<String>()
    val charset = charset ?: run {
        // Guess charset if we don't already know from BOM. Defaults to UTF-8 unless an encoding error is encountered.
        val bytes = inputStream.readBytes()

        val utf8 = Charset.forName("UTF-8")
        val shift = Charset.forName("Shift_JIS")

        var guess = utf8

        try {
            guess.newDecoder().decode(ByteBuffer.wrap(bytes))
        }catch(e: Exception) {
            guess = shift
        }

        try {
            val decoded = guess.newDecoder().decode(ByteBuffer.wrap(bytes))
            lines.addAll(decoded.toString().split('\n'))
        } catch(e: Exception) {
            Log.e("JapaneseDictionaryImport", "Failed to decode file with neither UTF-8 nor SHIFT_JIS, rejecting file as illegal!")
            return null
        }

        guess
    }

    // read 10 lines if not filled in already during guess
    if(lines.isEmpty()) {
        val reader = BufferedReader(InputStreamReader(inputStream, charset!!))
        for (i in 0..10) {
            lines += reader.readLine() ?: break
        }
    }

    val kind = when {
        msIme -> "msime"
        tango -> "tango"
        else -> "gboard"
    }

    val name = when {
        msIme -> {
            lines.firstOrNull { it.startsWith("!user dictionary name:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                    ?: lines.firstOrNull { it.startsWith("!output file name:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                    ?: "msime"
        }
        tango -> {
            lines.firstOrNull { it.startsWith("!!対象辞書;", ignoreCase = true) }
                ?.substringAfter(';')
                ?.trim()
                ?: "tango"
        }
        else -> "Gboard"
    }

    return DetectedUserDictFile(kind, name, Locale.JAPANESE, charset)
}

internal fun detectJapaneseUserDict(inputStream: InputStream): DetectedUserDictFile? {
    val header = ByteArray(4)
    inputStream.read(header)

    // charset to offset
    val encodingAndOffset = when {
        (header[0] == 0xFF.toByte() && header[1] == 0xFE.toByte()) -> Charset.forName("UTF-16LE") to 2
        (header[0] == 0xFE.toByte() && header[1] == 0xFF.toByte()) -> Charset.forName("UTF-16BE") to 2
        (header[0] == 0xEF.toByte() && header[1] == 0xBB.toByte() && header[2] == 0xBF.toByte())
                -> Charset.forName("UTF-8") to 3

        else -> {
            // will detect either UTF-8 or SHIFT_JIS later
            null to 0
        }
    }

    // read only the first line to confirm it's a dictionary file
    // We want to avoid reading the full thing into memory until we're sure it's a dictionary,
    // in case user supplied garbage
    val firstLine = mutableListOf(header)
    while(true) {
        val nextByte = inputStream.read().toByte()
        firstLine.add(byteArrayOf(nextByte))
        if(nextByte == 0x0A.toByte()) break

        // First line should never exceed 500 bytes
        if(firstLine.size > 500) return null
    }

    // tail byte
    if(encodingAndOffset.first?.name() == "UTF-16LE") firstLine.add(byteArrayOf(inputStream.read().toByte()))

    val firstLineData = firstLine.reduce { s, t -> s + t }.let {
        it.copyOfRange(encodingAndOffset.second, it.size)
    }

    if(encodingAndOffset.first == null && !firstLineData.all { it > 0x00 && it <= 0x7F }) return null
    val firstLineBb = ByteBuffer.wrap(firstLineData)

    val firstLineStr = try {
        (encodingAndOffset.first ?: Charset.forName("ASCII")).newDecoder().decode(firstLineBb).toString()
    } catch(e: Exception) {
        return null
    }
    return detectJapaneseUserDictInner(firstLineStr, inputStream, encodingAndOffset.first)?.copy(offset = encodingAndOffset.second)
}

fun determineFileKind(inputStream: InputStream): FileKindAndInfo {
    val array = ByteArray(4)
    inputStream.read(array)

    val voiceInputMagic = 0x6c6d6767.toUInt()
    val transformerMagic = 0x47475546.toUInt()
    val dictionaryMagic = 0x9bc13afe.toUInt()
    val mozcMagic = 0xef4d4f5a.toUInt()

    val magic = ByteBuffer.wrap(array).getInt().toUInt()

    return when {
        magic == voiceInputMagic -> FileKindAndInfo(FileKind.VoiceInput, null, null)
        magic == transformerMagic -> FileKindAndInfo(FileKind.Transformer, null, null)
        magic == mozcMagic -> {
            FileKindAndInfo(
                FileKind.Dictionary,
                // TODO: Dont hardcode name? No metadata in file to tell name, but we could try hashing etc
                name = "日本語辞書",
                locale = "ja"
            )
        }
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
}

object ResourceHelper {
    val BuiltInVoiceInputFallbacks = mapOf(
        "en" to BUILTIN_ENGLISH_MODEL
    )

    fun findKeyForLocaleAndKind(context: Context, locale: Locale, kind: FileKind): String? {
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

    fun findFileForKind(context: Context, locale: Locale, kind: FileKind): File? {
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

        GlobalIMEMessage.tryEmit(IMEMessage.ReloadResources)
    }
}

// format: relativepath:locale
//  jawords1.txt:ja
//  ja_JPwords1.txt:ja_JP
val ImportedUserDictFilesSetting = SettingsKey(
    stringSetPreferencesKey("imported_user_dict_files"),
    setOf()
)

fun getImportedUserDictFilesForLocale(context: Context, locale: Locale?): List<Pair<File, String>> {
    val setting = context.getSetting(ImportedUserDictFilesSetting)
    return setting.filter {
        locale == null || (localeFromString(it.split(':', limit = 2)[1]).language == locale.language)
    }.map {
        val file = it.split(':', limit = 2)[0]
        val name = file.split(' ', limit = 2)[0]

        File(context.getExternalFilesDir(null), file) to name
    }
}

suspend fun removeImportedUserDictFile(context: Context, value: Pair<File, String>) {
    val setting = context.getSetting(ImportedUserDictFilesSetting).filter {
        !it.startsWith(value.first.name)
    }.toSet()
    value.first.delete()
    context.setSetting(ImportedUserDictFilesSetting.key, setting)
    GlobalIMEMessage.tryEmit(IMEMessage.ReloadResources)
}

sealed class ItemBeingImported {
    data class LanguageResource(val v: FileKindAndInfo) : ItemBeingImported()
    data class SettingsBackup(val v: SettingsExporter.CfgFileMetadata) : ItemBeingImported()
    data class UserDictFile(val v: DetectedUserDictFile) : ItemBeingImported()
    data class CustomTheme(val v: ZipThemes.ThemeMetadataResult) : ItemBeingImported()
}

class ImportResourceActivity : ComponentActivity() {
    private val themeOption: MutableState<ThemeOption?> = mutableStateOf(null)
    private val itemBeingImported: MutableState<ItemBeingImported?> = mutableStateOf(null)
    private var uri: Uri? = null

    private fun normalizeFilename(name: String) = name.replace("/", "_").replace(":", "_").replace(" ", "_")

    private fun applyUserDictSetting(data: DetectedUserDictFile, inputMethodSubtype: InputMethodSubtype) {
        val locale = inputMethodSubtype.locale

        val contentResolver = applicationContext.contentResolver
        val openReader = {
            contentResolver.openInputStream(uri!!)!!.apply {
                read(ByteArray(data.offset))
            }.bufferedReader(data.encoding!!)
        }

        // 1. Compute filename
        val md = MessageDigest.getInstance("SHA-1")
        val utfCharSet = Charset.forName("UTF-8")
        openReader().use { inputStream ->
            val buffer = CharArray(8192)
            var read = inputStream.read(buffer)
            while(read > 0) {
                val str = String(buffer, 0, read)
                md.update(utfCharSet.encode(str))
                read = inputStream.read(buffer)
            }
        }

        @OptIn(ExperimentalStdlibApi::class)
        val digest = md.digest().toHexString()
        val fileName = "${normalizeFilename(data.name)} $locale $digest.txt"

        // 2. Copy file
        val outputFile = File(applicationContext.getExternalFilesDir(null), fileName)
        if(outputFile.exists()) { outputFile.delete() }

        openReader().use { inputStream ->
            outputFile.outputStream().bufferedWriter().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        // 3. Update reference
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val currSetting = applicationContext.getSetting(ImportedUserDictFilesSetting)
                val updatedSetting = currSetting + setOf(
                    "$fileName:$locale"
                )
                applicationContext.setSetting(
                    ImportedUserDictFilesSetting.key,
                    updatedSetting
                )
            }

            GlobalIMEMessage.tryEmit(IMEMessage.ReloadResources)
            finish()
        }
    }

    private fun applySetting(fileKind: FileKindAndInfo, inputMethodSubtype: InputMethodSubtype) {
        val item = itemBeingImported.value
        if(item is ItemBeingImported.UserDictFile) {
            return applyUserDictSetting(item.v, inputMethodSubtype)
        }

        if(item == null) return

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
            GlobalIMEMessage.tryEmit(IMEMessage.ReloadResources)
            finish()
        }
    }


    @Composable
    private fun InnerScreen() = itemBeingImported.value.let { item -> when(item) {
        is ItemBeingImported.CustomTheme -> {
            ScrollableList {
                ScreenTitleWithIcon(
                    stringResource(R.string.theme_customizer_import_custom_theme_menu_title),
                    painterResource(R.drawable.themes)
                )

                if(item.v.meta.isNewer) {
                    Tip("⚠\uFE0F " + stringResource(R.string.resource_importer_warning_cfg_backup_newer_version))
                }

                if(item.v.config == null) {
                    Text("Error parsing theme:\n" + (item.v.error ?: "?"))

                    Button(onClick = {
                        finish()
                    }) {
                        Text("Close")
                    }
                } else {
                    Text(
                        stringResource(
                            R.string.theme_customizer_import_custom_theme_name_text,
                            item.v.config.id ?: "?"
                        )
                    )

                    Text(
                        stringResource(
                            R.string.theme_customizer_import_custom_theme_author_text,
                            item.v.config.author ?: "?"
                        )
                    )

                    Button(onClick = {
                        getInputStream()?.use {
                            ZipThemes.importTheme(applicationContext, it, item.v)
                        }
                        finish()
                    }) {
                        Text(stringResource(R.string.resource_importer_import_button))
                    }
                }
            }
        }
        is ItemBeingImported.LanguageResource -> {
            ImportScreen(
                fileKind = item.v,
                onApply = { fileKind, inputMethodSubtype ->
                    applySetting(
                        fileKind,
                        inputMethodSubtype
                    )
                },
                onCancel = { finish() }
            )
        }
        is ItemBeingImported.SettingsBackup -> {
            SettingsImportScreen(
                metadata = item.v,
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
        }
        is ItemBeingImported.UserDictFile -> {
            val pseudoKind = FileKindAndInfo(FileKind.Dictionary, name = item.v.name, locale = item.v.locale.toString())
            ImportScreen(
                fileKind = pseudoKind,
                onApply = { fileKind, inputMethodSubtype ->
                    applySetting(
                        fileKind,
                        inputMethodSubtype
                    )
                },
                onCancel = { finish() }
            )
        }
        null -> {
            val pseudoKind = FileKindAndInfo(FileKind.Invalid, null, null)
            ImportScreen(
                fileKind = pseudoKind,
                onApply = { fileKind, inputMethodSubtype ->
                    applySetting(
                        fileKind,
                        inputMethodSubtype
                    )
                },
                onCancel = { finish() }
            )
        }
    } }

    private fun updateContent() {
        setContent {
            themeOption.value?.let { themeOption ->
                val themeIdx = useDataStore(key = THEME_KEY.key, default = themeOption.key)
                val theme: ThemeOption = getThemeOption(this, themeIdx.value) ?: themeOption
                UixThemeWrapper(theme.obtainColors(LocalContext.current)) {
                    StatusBarColorSetter()
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(Modifier.safeDrawingPadding()) {
                            InnerScreen()
                        }
                    }
                }
            }
        }
    }

    private fun getInputStream(): InputStream? = try {
        uri?.let { contentResolver.openInputStream(it) }
    } catch(e: Exception) {
        null
    }

    private fun detectItemBeingImported(): ItemBeingImported? {
        val languageResource = getInputStream()?.use { determineFileKind(it) }
        if(languageResource != null && languageResource.kind != FileKind.Invalid) return ItemBeingImported.LanguageResource(languageResource)

        val settingsBackup = getInputStream()?.use { SettingsExporter.getCfgFileMetadata(it) }
        if(settingsBackup != null) return ItemBeingImported.SettingsBackup(settingsBackup)

        val userDictFile = getInputStream()?.use { detectJapaneseUserDict(it) }
        if(userDictFile != null) return ItemBeingImported.UserDictFile(userDictFile)

        val customTheme = getInputStream()?.use { ZipThemes.getMetadata(it) }
        if(customTheme != null) return ItemBeingImported.CustomTheme(customTheme)

        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.uri = intent?.data!!

        itemBeingImported.value = detectItemBeingImported()

        val item = itemBeingImported.value
        if(item is ItemBeingImported.CustomTheme && DevAutoAcceptThemeImport) {
            if(item.v.config == null) {
                BugViewerState.pushBug(BugInfo(
                    name = "your custom theme (invalid metadata json)",
                    details = item.v.error ?: "Unknown error",
                ))
                BugViewerState.triggerOpen()
            } else {
                getInputStream()?.use {
                    ZipThemes.importTheme(applicationContext, it, item.v)
                }
            }
            finish()
            return
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateContent()
            }
        }

        val key = getSetting(THEME_KEY)
        this.themeOption.value = getThemeOption(this, key).orDefault(this)
    }
}

object MissingDictionaryHelper {
    sealed class DictCheckResult {
        object CheckFailed : DictCheckResult()
        object DontShowDictNotice : DictCheckResult()
        data class ShowDictNotice(val locale: Locale, val dismissalSetting: SettingsKey<Int>) : DictCheckResult()
    }
    fun checkIfDictInstalled(context: Context): DictCheckResult {
        if(context.isDeviceLocked) return DictCheckResult.CheckFailed

        val locale = Subtypes.getLocale(Subtypes.getActiveSubtype(context))
        val hasImportedDict = ResourceHelper.findKeyForLocaleAndKind(
            context,
            locale,
            FileKind.Dictionary
        ) != null
        val hasBuiltInDict = Dictionaries.getDictionaryIfExists(context, locale, Dictionaries.DictionaryKind.Any) != null

        // These languages have an automatic prompt to download the right file on keyboard.futo.org
        val langsWithDownloadableDictionaries = setOf(
            "ar", "hy", "as", "bn", "eu", "be", "bg", "ca", "hr", "cs", "da", "nl", "en", "eo", "fi",
            "fr", "gl", "ka", "de", "gom", "el", "gu", "he", "iw", "hi", "hu", "it", "kn", "ks", "lv",
            "lt", "lb", "mai", "ml", "mr", "nb", "or", "pl", "pt", "pa", "ro", "ru", "sa", "sat", "sr",
            "sd", "sl", "es", "sv", "ta", "te", "tok", "tcy", "tr", "uk", "ur", "af", "ar", "bn", "bg",
            "cs", "fr", "de", "he", "id", "it", "kab", "kk", "pms", "ru", "sk", "es", "uk", "vi",
            "ja"
        )

        // Typing is severely broken in Japanese without the dictionary, it is vital that this message
        // is shown every time until the user downloads the dictionary
        val undismissableLanguages = setOf("ja")

        val dismissalSetting = SettingsKey(
            intPreferencesKey("dictionary_notice_dismiss_${locale.language}"),
            0
        )

        if(
            !hasImportedDict &&
            !hasBuiltInDict &&
            langsWithDownloadableDictionaries.contains(locale.language) &&
            (context.getSetting(dismissalSetting) < 15 || undismissableLanguages.contains(locale.language))
        ) {
            return DictCheckResult.ShowDictNotice(locale, dismissalSetting)
        }

        return DictCheckResult.DontShowDictNotice
    }

    class NoDictionaryNotice(
        val dismissalSetting: SettingsKey<Int>,
        val locale: Locale,
        val string: String,
        val resetNotice: () -> Unit) : ImportantNotice {
        @Composable
        override fun getText(): String {
            return string
        }

        override fun onDismiss(context: Context, auto: Boolean) {
            resetNotice()
            context.setSettingBlocking(dismissalSetting.key,
                context.getSetting(dismissalSetting) + if(auto) 1 else 5)
        }

        override fun onOpen(context: Context) {
            resetNotice()
            context.openURI(FileKind.Dictionary.getAddonUrlForLocale(locale), true)
        }
    }
}