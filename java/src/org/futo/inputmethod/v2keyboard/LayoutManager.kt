package org.futo.inputmethod.v2keyboard

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.EmptySerializersModule
import org.futo.inputmethod.latin.localeFromString
import org.futo.inputmethod.latin.uix.actions.BugInfo
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.latin.uix.settings.pages.CustomLayout
import org.futo.inputmethod.latin.uix.settings.pages.getCustomLayout
import java.util.Locale

@Serializable
data class Mappings(
    val languages: Map<String, List<String>>
)

object LayoutManager {
    private var layoutsById: Map<String, LazyKeyboard>? = null
    private var localeToLayoutsMappings: Map<Locale, List<String>>? = null
    private var localeNames: Map<Locale, Map<Locale, String>>? = null
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
            (it.endsWith(".yml") || it.endsWith(".yaml")) && it != "layouts/mapping.yaml"
        }
    }

    fun init(context: Context) {
        if(initialized) return

        initialized = true

        localeToLayoutsMappings = parseMappings(context, "layouts/mapping.yaml").languages.mapKeys {
            localeFromString(it.key)
        }

        localeNames = parseNames(context, "layouts/names.yaml").mapKeys {
            localeFromString(it.key)
        }.mapValues { it.value.mapKeys { localeFromString(it.key) }}

        val assetManager = context.assets

        val layoutPaths = getAllLayoutPaths(assetManager)

        layoutsById = layoutPaths.filter { it != "layouts/names.yaml" }.associate { path ->
            val keyboard = LazyKeyboard(path)
            keyboard.filename to keyboard
        }
    }

    private fun ensureInitialized() {
        if(!initialized) throw IllegalStateException("LayoutManager method called without being initialized")
    }

    fun getLayout(context: Context, name: String): Keyboard {
        ensureInitialized()
        if(name.startsWith("custom")) return CustomLayout.getCustomLayout(context, name)

        return layoutsById?.get(name)?.get(context) ?: throw IllegalArgumentException("Failed to find keyboard layout $name. Available layouts: ${layoutsById?.keys}")
    }

    fun getLayoutOrNull(context: Context, name: String): Keyboard? {
        ensureInitialized()
        if(name.startsWith("custom")) return try {
            CustomLayout.getCustomLayout(context, name)
        } catch (_: Exception) {
            null
        }

        return layoutsById?.get(name)?.get(context)
    }

    fun getLayoutMapping(context: Context): Map<Locale, List<String>> {
        ensureInitialized()
        return localeToLayoutsMappings!!
    }

    fun getAllLayoutNames(context: Context): List<String> {
        ensureInitialized()
        return getAllLayoutPaths(context.assets).map {
            it.split("/").last().split(".yaml").first()
        }.filter { it != "names" }
    }

    private val unexceptionalLocales = mutableSetOf<Locale>()
    fun getExceptionalNameForLocale(locale: Locale, inLocale: Locale): String? {
        if(unexceptionalLocales.contains(locale)) return null
        val names = localeNames ?: return null

        val entry = names[locale] ?: run {
            // If there's an entry for "example" but we have "example_US", should still match.
            // But not the other way around, if there's an override for "example_US", it shouldn't
            // affect "example"
            if(locale.country.isNotEmpty()) {
                val localeWithoutCountry = Locale(locale.language, "", locale.variant)
                names[localeWithoutCountry]
            } else {
                null
            }
        }

        if(entry == null) {
            unexceptionalLocales.add(locale)
            return null
        }

        // Search order: try inLocale first, then try any language matching inLocale,
        // then try its native name, then try its native language name,
        // then try first name, otherwise return null
        val translatedEntry = entry[inLocale]
            ?: entry.entries.find { it.key.language == inLocale.language }?.value
            ?: entry[locale]
            ?: entry.entries.find { it.key.language == locale.language }?.value
            ?: entry.entries.firstOrNull()?.value
            ?: return null

        return translatedEntry
    }
}

private fun parseMappings(context: Context, mappingsPath: String): Mappings {
    return context.assets.open(mappingsPath).use { inputStream ->
        val yamlString = inputStream.bufferedReader().use { it.readText() }

        yaml.decodeFromString(Mappings.serializer(), yamlString)
    }
}

private fun parseNames(context: Context, namesPath: String): Map<String, Map<String, String>> {
    val namesSerializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))
    return context.assets.open(namesPath).use { inputStream ->
        val yamlString = inputStream.bufferedReader().use { it.readText() }

        yaml.decodeFromString(namesSerializer, yamlString)
    }
}

private val yaml = Yaml(
    EmptySerializersModule(),
    YamlConfiguration(
        polymorphismStyle = PolymorphismStyle.Property,
        allowAnchorsAndAliases = true
    )
)

fun parseKeyboardYamlString(yamlString: String): Keyboard {
    return yaml.decodeFromString(Keyboard.serializer(), yamlString)
}

internal class LazyKeyboard(
    val path: String
) {
    val filename = path.split("/").last().split(".yaml").first()
    var keyboard: Keyboard? = null

    private fun load(context: Context): Keyboard = try {
        parseKeyboardYaml(context, path).apply {
            id = filename
        }
    } catch(e: Exception) {
        BugViewerState.pushBug(BugInfo(
            "LayoutManager",
            "Failed to parse layout $filename\nMessage: ${e.message}, cause: ${e.cause?.message}"
        ))

        e.printStackTrace()

        parseKeyboardYaml(context, "layouts/Special/error.yaml").apply { id = filename }
    }

    fun get(context: Context): Keyboard {
        return keyboard ?: run {
            load(context).also {
                keyboard = it
            }
        }
    }
}

internal fun parseKeyboardYaml(context: Context, layoutPath: String): Keyboard {
    return context.assets.open(layoutPath).use { inputStream ->
        val yamlString = inputStream.bufferedReader().use { it.readText() }

        try {
            parseKeyboardYamlString(yamlString)
        } catch(e: Throwable) {
            Log.e("KeyboardParser", "Failed to parse $layoutPath")
            throw Exception("Error while parsing layout [$layoutPath]", e)
        }
    }
}