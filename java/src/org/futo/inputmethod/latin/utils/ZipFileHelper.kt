package org.futo.inputmethod.latin.utils

import org.futo.inputmethod.latin.BuildConfig
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object ZipFileHelper {
    fun parse(inputStream: InputStream, vararg targets: Pair<String, (ByteArray) -> Unit>): Boolean {
        val targets = targets.associate { it.first to it.second }
        val found = targets.entries.associate { it.key to false }.toMutableMap()
        ZipInputStream(inputStream).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if(!entry.isDirectory) {
                    if (targets.containsKey(entry.name)) {
                        found[entry.name] = true

                        val bytes = zipIn.readAllBytesCompat()
                        targets[entry.name]!!.invoke(bytes)
                    }

                    zipIn.closeEntry()
                }
                entry = zipIn.nextEntry
            }
        }

        return found.all { it.value }
    }

    fun parseSafe(inputStream: InputStream, vararg targets: Pair<String, (ByteArray) -> Unit>): Boolean {
        return try {
            parse(inputStream, *targets)
        }catch(e: Exception) {
            if(BuildConfig.DEBUG) e.printStackTrace()
            false
        }
    }

    fun extract(file: File, outputDir: File) {
        require(file.isFile) { "ZIP file must be a regular file" }
        require(outputDir.mkdirs() || outputDir.isDirectory) { "Cannot create output directory" }

        val canonicalOutputDir = outputDir.canonicalFile
        val outputDirPathWithSeparator = canonicalOutputDir.path + File.separator

        file.inputStream().buffered().use { fis ->
            ZipInputStream(fis).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val outputFile = File(outputDir, entry.name).canonicalFile

                    val outputPath = outputFile.path
                    if (!outputPath.startsWith(outputDirPathWithSeparator) && outputPath != canonicalOutputDir.path) {
                        throw SecurityException("Entry is outside of the target directory: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        // Ensure parent directory exists for file entries
                        outputFile.parentFile?.mkdirs()

                        outputFile.outputStream().use { fos ->
                            zipIn.copyTo(fos)
                        }
                    }

                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
    }
}