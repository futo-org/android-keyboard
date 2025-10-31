package org.futo.inputmethod.latin

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

object BundleHelper {
    private val TAG = "BundleHelper"
    private val DEBUG = BuildConfig.DEBUG
    private fun findBundledResourceIfExists(context: Context, name: String, locale: Locale): Pair<Resources, Int>? = try { run {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        val resources = context.createConfigurationContext(config).resources

        val pkg = BuildConfig.APPLICATION_ID
        val id = resources.getIdentifier(name, "raw", pkg)
        if (id == 0) {
            if(DEBUG) Log.d(TAG, "Identifier for [$name] in [${pkg}] is zero, return null")
            return null
        }

        // The id exists, but the file may be blank if it's a fallback (0 bytes), check if this is the case
        resources.openRawResourceFd(id)?.use {
            if(it.length < 100) {
                if(DEBUG) Log.d(TAG, "Length ${it.length} is under threshold, return null")
                return null
            }
        } ?: run {
            if(DEBUG) Log.d(TAG, "openRawResourceFd returned null, return null")
            return null
        }

        // The id exists and the file is not blank, return an input stream
        return (resources to id).also {
            //if(DEBUG) Log.d(TAG, "Yes, return $it")
        }
    } } catch(e: Exception) {
        if(DEBUG) Log.d(TAG, "An exception occurred, return null")
        null
    }

    private data class SplitLocationCache(val identity: Int, val locations: MutableMap<Pair<String, Locale>, AssetFileAddress?>)
    private var cache = SplitLocationCache(0, mutableMapOf())

    /** Note: resources must be at least 100 bytes */
    fun obtainSplitAssetFileDescriptor(context: Context, name: String, locale: Locale): AssetFileAddress? {
        if(cache.identity != System.identityHashCode(context.applicationContext)) {
            cache = SplitLocationCache(System.identityHashCode(context.applicationContext), mutableMapOf())
        }

        val key = name to locale
        if(cache.locations.containsKey(key)) return cache.locations[key]
        else return run {
            val resourceInfo = findBundledResourceIfExists(context, name, locale) ?: return@run null

            val applicationInfo = context.applicationInfo
            val filesToTry = mutableSetOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Find a split by locale
                val splitNames = applicationInfo.splitNames?.toList()
                val splitPaths = applicationInfo.splitSourceDirs?.toList()

                if (splitNames != null && splitPaths != null) {
                    val splits = splitNames.zip(splitPaths).associate { it }

                    // Top priority: locales that match
                    filesToTry.addAll(splits.filter {
                        it.key == "config." + locale.language.lowercase() || it.key == "config." + locale.toLanguageTag().lowercase()
                    }.map { it.value })

                    // Then try main app
                    filesToTry.add(applicationInfo.sourceDir)

                    // Then try other splits as last resort
                    filesToTry.addAll(splits.map { it.value })
                }
            }

            // Fallback: just try main app
            if (filesToTry.isEmpty()) filesToTry.add(applicationInfo.sourceDir)

            // Get asset info
            val (offset, length) = resourceInfo.first.openRawResourceFd(resourceInfo.second).use {
                it.startOffset to it.length
            }

            // Read first 100 bytes
            val needle = ByteArray(100)
            var read = 0
            resourceInfo.first.openRawResource(resourceInfo.second).use {
                read = it.read(needle)
            }

            // Don't support files under 100 bytes... something has to be wrong here
            if (read != needle.size) {
                throwIfDebug(Exception("Shouldn't happen: resource file under 100 bytes $name"))
                return@run null
            }

            // Test each file and return first matching, or null
            val testArray = ByteArray(100)

            return@run filesToTry.firstOrNull {
                val file = File(it)
                if (file.isDirectory) {
                    Log.e(TAG, "Source path is directory! $file")
                }

                try {
                    file.isFile && file.length() >= offset + length && FileInputStream(file).use {
                        it.skipNBytes2(offset)
                        read = it.read(testArray)
                        read == needle.size && testArray contentEquals needle
                    }
                }catch(_: IOException) {
                    false
                }
            }?.let {
                if (DEBUG) Log.d(TAG, "Found asset: [$it] [$offset] [$length]")
                AssetFileAddress(it, offset, length)
            } ?: run {
                throwIfDebug(Exception("Was not able to find asset address [$name] [$locale], tried $filesToTry"))
                return@obtainSplitAssetFileDescriptor null
            }
        }.also {
            cache.locations[key] = it
        }
    }

    private fun InputStream.skipNBytes2(n: Long) {
        var n = n
        while (n > 0) {
            val ns = skip(n)
            if (ns > 0 && ns <= n) {
                // adjust number to skip
                n -= ns
            } else if (ns == 0L) { // no bytes skipped
                // read one byte to check for EOS
                if (read() == -1) {
                    throw EOFException()
                }
                // one byte read so decrement number to skip
                n--
            } else { // skipped negative or too many bytes
                throw IOException("Unable to skip exactly")
            }
        }
    }
}