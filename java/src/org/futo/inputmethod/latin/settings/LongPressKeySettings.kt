package org.futo.inputmethod.latin.settings

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.KeyHintsSetting
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
        LongPressKey.Numbers -> context.getString(R.string.morekey_settings_kind_numbers)
        LongPressKey.LanguageKeys -> context.getString(R.string.morekey_settings_kind_language_keys)
        LongPressKey.Symbols -> context.getString(R.string.morekey_settings_kind_symbols)
        LongPressKey.QuickActions -> context.getString(R.string.morekey_settings_kind_actions)
        LongPressKey.MiscLetters -> context.getString(R.string.morekey_settings_kind_misc_common)
    }
}
fun LongPressKey.description(context: Context): String {
    return when(this) {
        LongPressKey.Numbers -> context.getString(R.string.morekey_settings_kind_numbers_example)
        LongPressKey.LanguageKeys -> context.getString(R.string.morekey_settings_kind_language_keys_example)
        LongPressKey.Symbols -> context.getString(R.string.morekey_settings_kind_symbols_example)
        LongPressKey.QuickActions -> context.getString(R.string.morekey_settings_kind_actions_example)
        LongPressKey.MiscLetters -> context.getString(R.string.morekey_settings_kind_misc_common_example)
    }
}

val LongPressKeyLayoutSetting = SettingsKey(
    stringPreferencesKey("longPressKeyOrdering"),
    "${LongPressKey.LanguageKeys.ordinal},${LongPressKey.Numbers.ordinal},${LongPressKey.Symbols.ordinal},${LongPressKey.QuickActions.ordinal},${LongPressKey.MiscLetters.ordinal}"
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

data class LongPressKeySettings(val currentOrder: List<LongPressKey>, val showHints: Boolean) {
    companion object {
        @JvmStatic
        fun load(context: Context): LongPressKeySettings =
            LongPressKeySettings(
                context.getSettingBlocking(LongPressKeyLayoutSetting).toLongPressKeyLayoutItems(),
                context.getSettingBlocking(KeyHintsSetting)
            )

        @JvmStatic
        fun joinMoreKeys(keys: List<String>): String =
            keys.map {
                it.replace("\\", "\\\\")
                    .replace(",", "\\,")
            }.joinToString(",")

        @JvmStatic
        fun forTest(): LongPressKeySettings =
            LongPressKeySettings(listOf(LongPressKey.Numbers, LongPressKey.LanguageKeys), false)
    }
}