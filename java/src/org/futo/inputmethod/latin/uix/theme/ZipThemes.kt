package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.futo.inputmethod.dictionarypack.MD5Calculator
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.pages.DevAutoAcceptThemeImport
import org.futo.inputmethod.latin.utils.readAllBytesCompat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipThemes {
    val bitmapCache: MutableMap<String, ImageBitmap> = mutableMapOf()
    val updateCount: MutableIntState = mutableIntStateOf(0)

    enum class ThemeLocation(val settingQualifier: String) {
        Assets("asset"),
        Custom("custom")
    }

    data class ThemeFileName(val name: String, val location: ThemeLocation) {
        fun toSetting(): String = "${location.settingQualifier}$name"

        companion object {
            fun fromSetting(s: String): ThemeFileName? =
                ThemeLocation.entries.firstOrNull { s.startsWith(it.settingQualifier) }?.let {
                    ThemeFileName(s.substring(it.settingQualifier.length).trimEnd('_'), it)
                }
        }
    }

    fun custom(name: String) = ThemeFileName(name, ThemeLocation.Custom)

    val themeCache: MutableMap<ThemeFileName, KeyboardColorScheme> = mutableMapOf()
    val thumbThemeCache: MutableMap<ThemeFileName, KeyboardColorScheme> = mutableMapOf()

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        allowComments = true
        allowTrailingComma = true
        prettyPrint = true
        //encodeDefaults = true
    }

    fun customThemesDir(context: Context) = File(context.filesDir, "themes").also { it.mkdirs() }

    fun listCustom(context: Context): List<ThemeFileName> = customThemesDir(context).listFiles()?.map { custom(it.nameWithoutExtension) } ?: emptyList()
    fun listAssets(context: Context): List<ThemeFileName> =
        context.assets.list("themes/")?.filter {
            it != null && it.endsWith(".zip")
        }?.map {
            ThemeFileName(it.substring(0, it.length - 4), ThemeLocation.Assets)
        } ?: emptyList()

    private const val versionFileName = "FUTOKeyboardTheme_Version"
    private const val configFileName = "config.json"
    private const val currentVersion: Byte = 1
    fun save(ctx: ThemeDecodingContext, theme: SerializableCustomTheme, name: ThemeFileName) {
        if(name.location != ThemeLocation.Custom) throw IllegalArgumentException("Can only save custom themes.")

        val dir = customThemesDir(ctx.context)
        dir.mkdirs()

        val file = File(dir, "${name.name}.zip")

        val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(file)))
        zos.setLevel(0)

        val putEntry = { filename: String ->
            zos.putNextEntry(ZipEntry(filename).apply {
                //method = ZipEntry.STORED
            })
        }

        val putFile = { filename: String ->
            putEntry(filename)
            zos.write(ctx.getFileBytes(filename)!!)
            zos.closeEntry()
        }

        putEntry(versionFileName)
        zos.write(ByteBuffer.allocate(9).also {
            it.order(ByteOrder.LITTLE_ENDIAN)
            it.put(currentVersion)
            it.putLong(Date().time)
        }.array())
        zos.closeEntry()

        putEntry(configFileName)
        zos.write(json.encodeToString(theme).encodeUtf8().toByteArray())
        zos.closeEntry()

        theme.thumbnailImage?.let(putFile)
        theme.backgroundImage?.let(putFile)
        theme.keysFont?.let(putFile)
        theme.keyBackgrounds.values.forEach(putFile)
        theme.keyIcons.values.forEach(putFile)

        zos.close()
        themeCache.remove(name)
        updateCount.intValue += 1
    }

    data class ThemeMetadata(
        val dateExported: Date,
        val isNewer: Boolean,
    )

    data class ThemeMetadataResult(
        val meta: ThemeMetadata,
        val config: SerializableCustomTheme?,
        val error: String?
    )

    fun getMetadata(inputStream: InputStream): ThemeMetadataResult? {
        var metadata: ThemeMetadata? = null
        var config: SerializableCustomTheme? = null
        var error: String? = null

        try {
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if(entry.isDirectory) continue
                    if(entry.name == versionFileName) {
                        val bytes = zipIn.readAllBytesCompat()

                        val buff = ByteBuffer.wrap(bytes).apply { order(ByteOrder.LITTLE_ENDIAN) }
                        val version = buff.get()
                        val date = buff.getLong()

                        metadata = ThemeMetadata(
                            dateExported = Date(date),
                            isNewer = version > currentVersion
                        )
                    }

                    if(entry.name == configFileName) {
                        try {
                            val bytes = zipIn.readAllBytesCompat()
                            val string = bytes.decodeToString()
                            val cfg = json.decodeFromString<SerializableCustomTheme>(string)

                            if(cfg.id == null || cfg.id.length < 3) throw Exception("ID must be at least 3 characters for a custom theme")
                            if(cfg.id.endsWith('_')) throw Exception("ID must not end with underscores")

                            config = cfg
                        } catch(e: Exception) {
                            error += "Cause: ${e.message}\n\nStack trace: ${e.stackTrace.map { it.toString() }}"
                        }
                    }

                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (e: Exception) {
            //e.printStackTrace()
        }

        return metadata?.let {
            ThemeMetadataResult(metadata, config, error)
        }
    }

    fun importTheme(context: Context, inputStream: InputStream, metadata: ThemeMetadataResult) {
        if(metadata.config == null) {
            throwIfDebug(IllegalArgumentException("Theme must parse successfully before being imported: ${metadata.error}"))
            return
        }

        val id = metadata.config.id!!
        val file = File(customThemesDir(context), "${id}.zip")

        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }

        themeCache.remove(custom(id))

        val setting = custom(id).toSetting()
        val currTheme = context.getSetting(THEME_KEY)
        if(currTheme.trimEnd('_') == setting || DevAutoAcceptThemeImport) {
            runBlocking {
                context.setSetting(
                    THEME_KEY,
                    setting + if(currTheme.endsWith('_')) "" else "_"
                )
            }
        }
    }

    private fun loadFile(androidContext: Context, file: File): Pair<ThemeDecodingContext, SerializableCustomTheme> {
        val hash = file.inputStream().use {
            MD5Calculator.checksum(it)
        }

        val zipFile = ZipFile(file)

        val ctx = object : ThemeDecodingContext {
            override val context: Context
                get() = androidContext

            override fun getFileBytes(path: String): ByteArray? {
                val entry = zipFile.getEntry(path)
                if(entry == null) return null

                val inputStream = zipFile.getInputStream(entry)
                val data = inputStream.readAllBytesCompat()
                inputStream.close()

                return data
            }

            override fun getFileHash(path: String): String? {
                return "$hash--$path"
            }

            override fun close() {
                zipFile.close()
            }
        }

        val theme = json.decodeFromString<SerializableCustomTheme>(
            ctx.getFileBytes(configFileName)!!.decodeToString()
        )

        return Pair(ctx, theme)
    }

    private fun load(context: Context, name: ThemeFileName): Pair<ThemeDecodingContext, SerializableCustomTheme> {
        val file = when(name.location) {
            ThemeLocation.Custom -> {
                val fileName = "${name.name}.zip"

                val file = File(customThemesDir(context), fileName)
                if(!file.isFile) throw FileNotFoundException()
                file
            }

            ThemeLocation.Assets -> {
                val cacheFile = File(context.codeCacheDir, "${name.name}.zip")

                if(cacheFile.isFile) {
                    cacheFile
                } else {

                    val fileName = "themes/${name.name}.zip"
                    val assets = context.assets

                    assets.open(fileName).use { inputStream ->
                        cacheFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    cacheFile
                }
            }
        }

        return loadFile(context, file)
    }

    fun loadSchemeThumb(context: Context, name: ThemeFileName): KeyboardColorScheme {
        return themeCache[name] ?: thumbThemeCache.getOrPut(name) {
            val i = load(context, name)

            val wrapper = object : ThemeDecodingContext {
                override val context: Context
                    get() = i.first.context

                override fun getFileBytes(path: String): ByteArray? =
                    if(path == i.second.thumbnailImage) i.first.getFileBytes(path) else null

                override fun getFileHash(path: String): String? =
                    if(path == i.second.thumbnailImage) i.first.getFileHash(path) else null

                override fun close() = i.first.close()
            }

            val r = i.second.toKeyboardScheme(wrapper)
            i.first.close()
            r
        }
    }

    fun loadScheme(context: Context, name: ThemeFileName): KeyboardColorScheme =
        themeCache.getOrPut(name) {
            val i = load(context, name)
            val r = i.second.toKeyboardScheme(i.first)
            i.first.close()
            r
        }

    fun delete(context: Context, name: ThemeFileName) {
        if(name.location != ThemeLocation.Custom) throw IllegalArgumentException("Cannot delete non-custom themes")
        val file = File(customThemesDir(context), "${name.name}.zip")
        if(!file.isFile) return

        // Don't stay on a theme being deleted!
        val currTheme = context.getSetting(THEME_KEY)
        if(currTheme.trimEnd('_') == "custom${name.name}") {
            runBlocking { context.setSetting(THEME_KEY, defaultThemeOption(context).key) }
        }

        file.delete()
        themeCache.remove(name)
        updateCount.intValue += 1
    }
}