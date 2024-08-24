package org.futo.inputmethod.v2keyboard

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.modules.EmptySerializersModule
import org.futo.inputmethod.latin.uix.actions.BugInfo
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import java.util.Locale

object LayoutManager {
    private var layoutsById: Map<String, Keyboard>? = null
    private var initialized = false

    private fun listFilesRecursively(assetManager: AssetManager, path: String): List<String> {
        val files = assetManager.list(path)
        return if(files.isNullOrEmpty()) {
            listOf(path)
        } else {
            files.flatMap { listFilesRecursively(assetManager, "$path/$it") }
        }
    }

    private fun getAllLayoutPaths(assetManager: AssetManager): List<String> {
        return listFilesRecursively(assetManager, "layouts").filter {
            it.endsWith(".yml") || it.endsWith(".yaml")
        }
    }

    fun init(context: Context) {
        if(initialized) return

        initialized = true

        val assetManager = context.assets

        val layoutPaths = getAllLayoutPaths(assetManager)

        layoutsById = layoutPaths.associate { path ->
            val filename = path.split("/").last().split(".yaml").first()

            val keyboard = try {
                parseKeyboardYaml(context, path).apply { id = filename }
            } catch(e: Exception) {
                BugViewerState.pushBug(BugInfo(
                    "LayoutManager",
                    "Failed to parse layout $filename\nMessage: ${e.message}, cause: ${e.cause?.message}"
                ))

                e.printStackTrace()

                parseKeyboardYaml(context, "layouts/Special/error.yaml").apply { id = filename }
            }

            filename to keyboard
        }
    }

    fun getLayout(context: Context, name: String): Keyboard {
        init(context)

        return layoutsById?.get(name) ?: throw IllegalArgumentException("Failed to find keyboard layout $name. Available layouts: ${layoutsById?.keys}")
    }

    fun queryLayoutsForLocale(locale: Locale): List<Keyboard> {
        val language = locale.language
        val script = locale.getKeyboardScript()
        return layoutsById!!.values.filter { it.languages.contains(language) || it.script == script }
    }

    fun getAllLayoutNames(context: Context): List<String> {
        init(context)

        return getAllLayoutPaths(context.assets).map {
            it.split("/").last().split(".yaml").first()
        }
    }
}


private fun parseKeyboardYaml(context: Context, layoutPath: String): Keyboard {
    val yaml = Yaml(
        EmptySerializersModule(),
        YamlConfiguration(
            polymorphismStyle = PolymorphismStyle.Property,
            allowAnchorsAndAliases = true
        )
    )
    return context.assets.open(layoutPath).use { inputStream ->
        val yamlString = inputStream.bufferedReader().use { it.readText() }

        try {
            yaml.decodeFromString(Keyboard.serializer(), yamlString)
        } catch(e: Throwable) {
            Log.e("KeyboardParser", "Failed to parse $layoutPath")
            throw Exception("Error while parsing layout [$layoutPath]", e)
        }
    }
}