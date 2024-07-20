package org.futo.inputmethod.latin.settings

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import org.futo.inputmethod.keyboard.internal.MoreKeySpec
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSettingBlocking

enum class LongPressKey {
    Numbers,
    LanguageKeys,
    Symbols,
    QuickActions,
    MiscLetters
}

fun LongPressKey.name(context: Context): String {
    return when(this) {
        LongPressKey.Numbers -> "Numbers"
        LongPressKey.LanguageKeys -> "Language keys"
        LongPressKey.Symbols -> "Symbols"
        LongPressKey.QuickActions -> "Quick actions"
        LongPressKey.MiscLetters -> "Misc. letters from common languages"
    }
}
fun LongPressKey.description(context: Context): String {
    return when(this) {
        LongPressKey.Numbers -> "e.g. [1] on [q]"
        LongPressKey.LanguageKeys -> "e.g. [ñ] on [n] in Spanish"
        LongPressKey.Symbols -> "e.g. [@] on [a]"
        LongPressKey.QuickActions -> "e.g. [Copy] on [c]"
        LongPressKey.MiscLetters -> "e.g. [ß] on [s] in all Latin script languages"
    }
}

private fun getKind(moreKey: String): LongPressKey? {
    val moreKeyStripped = moreKey.replace("!text/", "")

    return if(moreKeyStripped.startsWith("morekeys_misc_")) {
        LongPressKey.MiscLetters
    } else if(moreKeyStripped.startsWith("actions_")) {
        LongPressKey.QuickActions
    } else if(moreKeyStripped.startsWith("qwertysyms_")) {
        LongPressKey.Symbols
    } else if(moreKeyStripped.startsWith("number_")) {
        LongPressKey.Numbers
    } else if(moreKeyStripped.startsWith("morekeys_")) {
        LongPressKey.LanguageKeys
    } else {
        null
    }
}

val LongPressKeyLayoutSetting = SettingsKey(
    stringPreferencesKey("longPressKeyOrdering"),
    "${LongPressKey.Numbers.ordinal},${LongPressKey.LanguageKeys.ordinal},${LongPressKey.Symbols.ordinal},${LongPressKey.QuickActions.ordinal},${LongPressKey.MiscLetters.ordinal}"
)

fun String.toLongPressKeyLayoutItems(): List<LongPressKey> {
    return this.split(",").mapNotNull {
        val id = it.toIntOrNull() ?: return@mapNotNull null

        LongPressKey.entries[id]
    }
}

fun List<LongPressKey>.toEncodedString(): String {
    return this.joinToString(separator = ",") {
        "${it.ordinal}"
    }
}

class LongPressKeySettings(val context: Context) {
    private val currentSetting = context.getSettingBlocking(LongPressKeyLayoutSetting).toLongPressKeyLayoutItems()

    fun reorderMoreKeys(moreKeys: String): String {
        val keys = MoreKeySpec.splitKeySpecs(moreKeys)?.toList() ?: listOf(moreKeys)

        val finalKeys = mutableListOf<String>()

        // Add non configurable keys first
        keys.forEach { key ->
            if(getKind(key) == null) {
                finalKeys.add(key)
            }
        }

        // Add the necessary configurable keys in the correct order.
        // Key kinds not enabled are not added
        currentSetting.forEach { kind ->
            keys.forEach { key ->
                if(getKind(key) == kind) {
                    finalKeys.add(key)
                }
            }
        }

        return finalKeys.joinToString(separator = ",")
    }

    override operator fun equals(other: Any?): Boolean {
        return other is LongPressKeySettings && (other.currentSetting.joinToString(",") == currentSetting.joinToString(","))
    }
}