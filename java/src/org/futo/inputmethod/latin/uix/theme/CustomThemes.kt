package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.futo.inputmethod.dictionarypack.MD5Calculator
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.utils.readAllBytesCompat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object CustomThemes {
    val bitmapCache: MutableMap<String, ImageBitmap> = mutableMapOf()
    val themeCache: MutableMap<String, KeyboardColorScheme> = mutableMapOf()
    val thumbThemeCache: MutableMap<String, KeyboardColorScheme> = mutableMapOf()
    val updateCount: MutableIntState = mutableIntStateOf(0)

    fun getDirectory(context: Context) = File(context.filesDir, "themes")

    fun list(context: Context): List<String> = getDirectory(context).listFiles()?.map { it.nameWithoutExtension } ?: emptyList()

    fun save(ctx: ThemeDecodingContext, theme: SerializableCustomTheme, name: String) {
        val dir = getDirectory(ctx.context)
        dir.mkdirs()

        val file = File(dir, "$name.zip")

        val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(file)))
        zos.setLevel(0)

        val putEntry = { name: String ->
            zos.putNextEntry(ZipEntry(name).apply {
                //method = ZipEntry.STORED
            })
        }

        val putFile = { name: String ->
            putEntry(name)
            zos.write(ctx.getFileBytes(name)!!)
            zos.closeEntry()
        }

        putEntry("version")
        zos.write(1)
        zos.closeEntry()

        putEntry("config.json")
        zos.write(Json.Default.encodeToString(theme).encodeUtf8().toByteArray())
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

    private fun load(context: Context, name: String): Pair<ThemeDecodingContext, SerializableCustomTheme> {
        val file = File(getDirectory(context), "${name}.zip")
        if(!file.isFile) throw FileNotFoundException()

        val hash = file.inputStream().use {
            MD5Calculator.checksum(it)
        }

        val zipFile = ZipFile(file)

        val ctx = object : ThemeDecodingContext {
            override val context: Context
                get() = context

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

        val theme = Json.Default.decodeFromString<SerializableCustomTheme>(
            ctx.getFileBytes("config.json")!!.decodeToString()
        )

        return Pair(ctx, theme)
    }

    fun loadSchemeThumb(context: Context, name: String): KeyboardColorScheme {
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

    fun loadScheme(context: Context, name: String): KeyboardColorScheme =
        themeCache.getOrPut(name) {
            val i = load(context, name)
            val r = i.second.toKeyboardScheme(i.first)
            i.first.close()
            r
        }

    fun delete(context: Context, name: String) {
        val file = File(getDirectory(context), "${name}.zip")
        if(!file.isFile) return

        // Don't stay on a theme we're deleting!
        val currTheme = context.getSetting(THEME_KEY)
        if(currTheme.trimEnd('_') == "custom$name") {
            runBlocking { context.setSetting(THEME_KEY, defaultThemeOption(context).key) }
        }

        file.delete()
        themeCache.remove(name)
        updateCount.intValue += 1
    }
}