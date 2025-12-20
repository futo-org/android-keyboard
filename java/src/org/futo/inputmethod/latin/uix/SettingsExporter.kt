package org.futo.inputmethod.latin.uix

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.sink
import okio.source
import org.futo.inputmethod.engine.GlobalIMEMessage
import org.futo.inputmethod.engine.IMEMessage
import org.futo.inputmethod.engine.general.mozcUserProfileDir
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.utils.readAllBytesCompat
import org.futo.inputmethod.latin.uix.PreferenceUtils.getDefaultSharedPreferences
import org.futo.inputmethod.latin.uix.actions.ClipboardFileName
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryManager.Companion.onClipboardImportedFlow
import org.futo.inputmethod.latin.uix.actions.clipboardFile
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.pages.modelmanager.findSettingsActivity
import org.futo.inputmethod.latin.uix.theme.ZipThemes
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val IMPORT_SETTINGS_REQUEST = 1801146881
const val EXPORT_SETTINGS_REQUEST = 69835032

@Serializable
data class PersonalWord(
    val word: String,
    val frequency: Int,
    val locale: String?,
    val appId: Int,
    val shortcut: String?
)


@Suppress("HardCodedStringLiteral")
object SettingsExporter {
    @Suppress("HardCodedStringLiteral")
    @Throws(Exception::class)
    private fun writeSharedPrefs(
        prefs: SharedPreferences,
        outputStream: OutputStream
    ) {
        val root = JSONObject()

        for ((k, any) in prefs.all) {
            val item = JSONObject()
            when (any) {
                is Boolean -> {
                    item.put("t", "b"); item.put("v", any)
                }

                is Int -> {
                    item.put("t", "i"); item.put("v", any)
                }

                is Long -> {
                    item.put("t", "l"); item.put("v", any)
                }

                is Float -> {
                    item.put("t", "f"); item.put("v", any.toDouble())
                }

                is String -> {
                    item.put("t", "s"); item.put("v", any)
                }

                is Set<*> -> {
                    val jsonArr = JSONArray(any.toTypedArray())
                    item.put("t", "ss")
                    item.put("v", jsonArr)
                }

                null -> {
                    item.put("t", "n")
                }
            }
            root.put(k, item)
        }

        outputStream.write(root.toString().encodeUtf8().toByteArray())
    }

    @Suppress("HardCodedStringLiteral")
    @Throws(Exception::class)
    private fun readSharedPrefs(
        editor: SharedPreferences.Editor,
        inputStream: InputStream
    ) {
        val text = inputStream.readAllBytesCompat().toByteString().utf8()
        val root = JSONObject(text)

        editor.clear()

        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val item = root.getJSONObject(k)
            when (item.getString("t")) {
                "b" -> editor.putBoolean(k, item.getBoolean("v"))
                "i" -> editor.putInt(k, item.getInt("v"))
                "l" -> editor.putLong(k, item.getLong("v"))
                "f" -> editor.putFloat(k, item.getDouble("v").toFloat())
                "s" -> editor.putString(k, item.getString("v"))
                "ss" -> {
                    val arr = item.getJSONArray("v")
                    val set = LinkedHashSet<String>(arr.length())
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    editor.putStringSet(k, set)
                }

                "n" -> editor.remove(k)
            }
        }
        editor.apply()
    }

    private fun writePersonalDict(
        context: Context,
        outputStream: OutputStream
    ) {
        val words = UserDictionaryIO(context).get()
        outputStream.write(Json.encodeToString(words).encodeUtf8().toByteArray())
    }

    private fun readPersonalDict(
        context: Context,
        inputStream: InputStream,
        clear: Boolean
    ) {
        val wordsStr = inputStream.readAllBytesCompat().toByteString().utf8()
        val words = Json.decodeFromString<List<PersonalWord>>(wordsStr)
        UserDictionaryIO(context).put(words, clear)
    }


    private const val versionFileName = "FUTOKeyboardSettings_CfgExportVersion"
    private const val currentVersion: Byte = 1

    private const val datastoreFileName = "datastore.preferences_pb"
    private const val sharedPreferencesFileName = "sharedPreferences.json"
    private const val clipboardFileName = ClipboardFileName
    private const val personalDictFileName = "userdictionary.json"

    suspend fun exportSettings(
        context: Context,
        outputStream: OutputStream,
        includeHeavyResources: Boolean
    ) = ZipOutputStream(outputStream).use { zipOut ->
        zipOut.setLevel(1)

        // Write version and date
        zipOut.putNextEntry(ZipEntry(versionFileName))
        zipOut.write(ByteBuffer.allocate(9).also {
            it.order(ByteOrder.LITTLE_ENDIAN)
            it.put(currentVersion)
            it.putLong(Date().time)
        }.array())
        zipOut.closeEntry()

        // Collect preferences
        context.getUnlockedPreferences()?.let { prefs ->
            zipOut.putNextEntry(ZipEntry(datastoreFileName))
            val array = ByteArrayOutputStream()
            val sink = array.sink().buffer()
            PreferencesSerializer.writeTo(prefs, sink)
            sink.flush()
            zipOut.write(array.toByteArray())
            zipOut.closeEntry()
        }

        // Collect SharedPreferences
        getDefaultSharedPreferences(context).let { sharedPrefs ->
            zipOut.putNextEntry(ZipEntry(sharedPreferencesFileName))
            writeSharedPrefs(sharedPrefs, zipOut)
            zipOut.closeEntry()
        }

        // Collect personal dictionary
        run {
            zipOut.putNextEntry(ZipEntry(personalDictFileName))
            writePersonalDict(context, zipOut)
            zipOut.closeEntry()
        }

        // Collect clipboard
        val clipboardFile = context.clipboardFile
        if (clipboardFile.exists()) {
            zipOut.putNextEntry(ZipEntry(clipboardFileName))
            clipboardFile.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }

        // Collect resources
        context.getExternalFilesDir(null)?.listFiles()?.forEach { resourceFile ->
            // if includeHeavyResources, then only include this if its not a .dict
            if (resourceFile.extension == "dict" || includeHeavyResources) {
                zipOut.putNextEntry(ZipEntry("ext/${resourceFile.name}"))
                resourceFile.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        }

        // Collect transformer models
        val modelDirectory = ModelPaths.getModelDirectory(context)
        modelDirectory.listFiles()?.forEach { resourceFile ->
            if (includeHeavyResources && ModelPaths.shouldFileBeIncludedInExport(resourceFile)) {
                zipOut.putNextEntry(ZipEntry("transformers/${resourceFile.name}"))
                resourceFile.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        }

        // Collect UserHistoryDictionaries
        context.filesDir.listFiles()?.forEach { resourceFile ->
            if(resourceFile.name.startsWith("UserHistoryDictionary")
                && resourceFile.isDirectory
            ) {
                resourceFile.listFiles()!!.forEach { subfile ->
                    val entry = ZipEntry("userdict/${resourceFile.name}/${subfile.name}")
                    zipOut.putNextEntry(entry)
                    subfile.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }

        // Collect mozc (Japanese user typing history, etc)
        mozcUserProfileDir(context).listFiles()?.forEach { subfile ->
            assert(!subfile.isDirectory)
            val entry = ZipEntry("mozc/${subfile.name}")
            zipOut.putNextEntry(entry)
            subfile.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }

        // Collect themes
        ZipThemes.customThemesDir(context).listFiles()?.forEach { themeFile ->
            zipOut.putNextEntry(ZipEntry("themes/${themeFile.name}"))
            themeFile.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }
    }

    private fun String.splitSlash(): String = split("/", limit = 2)[1]

    suspend fun loadSettings(
        context: Context,
        inputStream: InputStream,
        destructive: Boolean
    ) = ZipInputStream(inputStream).use { zipIn ->
        var entry = zipIn.nextEntry

        val clipboardFile = context.clipboardFile
        val transformersDir = ModelPaths.getModelDirectory(context)
        val extFilesDir = context.getExternalFilesDir(null)!!
        val themesDir = ZipThemes.customThemesDir(context)
        if (destructive) {
            // delete old clipboard
            if (clipboardFile.exists()) {
                clipboardFile.delete()
            }

            // delete all transformers
            transformersDir.listFiles()?.forEach {
                it.delete()
            }

            // delete all ext resources
            extFilesDir.listFiles()?.forEach {
                it.delete()
            }

            // delete all user dictionaries
            context.filesDir.listFiles()?.forEach {
                if(it.isDirectory && it.name.startsWith("UserHistoryDictionary")) {
                    it.deleteRecursively()
                }
            }

            mozcUserProfileDir(context).deleteRecursively()

            // delete all themes
            ZipThemes.customThemesDir(context).listFiles()?.forEach { it.delete() }
        }
        while (entry != null) {
            when {
                entry.name == versionFileName -> {}

                entry.name == datastoreFileName -> {
                    val prefsData = zipIn.readAllBytesCompat()
                    val prefs = PreferencesSerializer.readFrom(prefsData.inputStream().source().buffer())
                    context.dataStore.updateData { prefs }
                }

                entry.name == sharedPreferencesFileName -> {
                    val editor = getDefaultSharedPreferences(context).edit()
                    readSharedPrefs(editor, zipIn)
                    @SuppressLint("ApplySharedPref")
                    editor.commit()
                }

                entry.name == personalDictFileName -> {
                    readPersonalDict(context, zipIn, destructive)
                }

                entry.name == clipboardFileName -> {
                    clipboardFile.outputStream().use {
                        zipIn.copyTo(it)
                    }

                    onClipboardImportedFlow.emit(clipboardFile)
                }

                entry.name.startsWith("ext/") -> {
                    File(extFilesDir, entry.name.splitSlash()).outputStream().use {
                        zipIn.copyTo(it)
                    }
                }

                entry.name.startsWith("transformers/") -> {
                    File(transformersDir, entry.name.splitSlash()).outputStream().use {
                        zipIn.copyTo(it)
                    }
                }

                entry.name.startsWith("userdict/") -> {
                    val names = entry.name.split("/")
                    assert(names.size == 3)

                    val subdirName = names[1]
                    val fileName = names[2]

                    val subdir = File(context.filesDir, subdirName)
                    subdir.mkdirs()

                    File(subdir, fileName).outputStream().use {
                        zipIn.copyTo(it)
                    }
                }

                entry.name.startsWith("mozc/") -> {
                    val relDir = entry.name.split('/', limit=2).last()

                    assert(!relDir.contains('/'))

                    val userProfileDir = mozcUserProfileDir(context)
                    userProfileDir.mkdirs()
                    File(userProfileDir, relDir).outputStream().use {
                        zipIn.copyTo(it)
                    }
                }

                entry.name.startsWith("themes/") -> {
                    themesDir.mkdirs()

                    File(themesDir, entry.name.splitSlash()).outputStream().use {
                        zipIn.copyTo(it)
                    }
                }

                else -> {
                    Log.w(
                        "SettingsExporter",
                        "Encountered unknown file when reading exported backup: ${entry.name}"
                    )
                }
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        GlobalIMEMessage.tryEmit(IMEMessage.ReloadResources)
    }

    fun triggerExportSettings(context: Context) {
        val date = Date()
        val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", context.resources.configuration.locale)
        val formattedDate = formatter.format(date)
        val defaultFileName = "FUTOKeyboardSettings_${formattedDate}.backup"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, defaultFileName)
        }

        val activity: SettingsActivity = findSettingsActivity(context)
        activity.startActivityForResult(intent, EXPORT_SETTINGS_REQUEST)
    }

    fun triggerImportSettings(context: Context) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }
        (context as Activity).startActivityForResult(intent, IMPORT_SETTINGS_REQUEST)
    }

    data class CfgFileMetadata(
        val dateExported: Date,
        val isNewer: Boolean
    )

    fun getCfgFileMetadata(inputStream: InputStream): CfgFileMetadata? {
        return try {
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == versionFileName) {
                        val bytes = zipIn.readAllBytesCompat()

                        val buff = ByteBuffer.wrap(bytes).apply { order(ByteOrder.LITTLE_ENDIAN) }
                        val version = buff.get()
                        val date = buff.getLong()

                        return CfgFileMetadata(
                            dateExported = Date(date),
                            isNewer = version > currentVersion
                        )
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }


    @Composable
    fun ExportingMenu(navController: NavHostController = rememberNavController()) {
        val context = LocalContext.current
        val activity = remember { findSettingsActivity(context) }
        val triggered = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            activity.exportInProgress.value = 1
            triggerExportSettings(context)
            triggered.value = true
        }

        LaunchedEffect(activity.exportInProgress.value, triggered.value) {
            if(activity.exportInProgress.value==0 && triggered.value) {
                navController.navigateUp()
            }
        }
        BackHandler(activity.exportInProgress.value == 2) { }
        ScrollableList {
            ScreenTitle(stringResource(R.string.settings_export_configuration_exporting))

            if(activity.exportInProgress.value == 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                Text(
                    stringResource(R.string.settings_export_configuration_exporting_text),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)
                )
            }
        }
    }
}