package org.futo.inputmethod.latin.uix.settings.pages.langspecific.zh

import android.util.Log
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import icu.astronot233.rime.Rime
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.utils.toEnumOrNull
import java.io.File
import java.io.IOException

object RimeSettings {
    const val TAG = "RimeSettings"
    const val ConfigVersion = "2.0.0"

    // How should we get the context?
    // Now RimeDir is initialized by ChineseIME
    lateinit var RimeDir: File
    val SharedDataDir: File get() = File(RimeDir, "shared")
    val UserDataDir: File get() = File(RimeDir, "user")

    enum class ChineseSimplificationMode(val stringResource: Int) {
        // These must not be renamed, or the existing setting value for users will break
        ByLanguage(R.string.chinese_setting_simplification_simplify_by_language_country),
        Simplified(R.string.chinese_setting_simplification_force_simplified),
        Traditional(R.string.chinese_setting_simplification_force_traditional);
    }

    val SimplificationSetting = SettingsKey(
        stringPreferencesKey("ChineseIME_simplification"),
        ChineseSimplificationMode.ByLanguage.name
    )
    val EnabledSchemaSetting = SettingsKey(
        stringSetPreferencesKey("ChineseIME_enabledSchema"),
        emptySet()
    )

    val settings = listOf(
        UserSetting(R.string.chinese_setting_simplification) {
            val resources = LocalResources.current
            val (setting, setSetting) = useDataStore(SimplificationSetting)
            DropDownPickerSettingItem<ChineseSimplificationMode>(
                stringResource(R.string.chinese_setting_simplification),
                ChineseSimplificationMode.entries,
                setting.toEnumOrNull<ChineseSimplificationMode>()
                    ?: ChineseSimplificationMode.ByLanguage,
                { setSetting(it.name) },
                { resources.getString(it.stringResource) }
            )
            generateDefaultYaml()
            // TODO: request engine redeploy
        },
        UserSetting(R.string.chinese_setting_simplification) { // TODO: need a new string for enabled schemas
            val resources = LocalResources.current
            val (setting, setSetting) = useDataStore(EnabledSchemaSetting)
            // TODO: show a list with checkboxes then save settings
            generateDefaultYaml()
            // TODO: request engine redeploy
        }
    )
    private fun generateDefaultYaml() {
        val dotYaml = File(UserDataDir, "default.yaml")
        val newYaml = StringBuilder("config_version: $ConfigVersion\n")

        newYaml.append(general)
        newYaml.append(punctuator)

        // TODO: replace (true) with ChineseSimplificationMode
        newYaml.appendLine("convert:")
        newYaml.appendLine("  - name: traditionalization")
        newYaml.appendLine("    reset: ${if (true) 1 else 0}")
        newYaml.appendLine("  - name: simplification")
        newYaml.appendLine("    reset: ${if (true) 1 else 0}")

        // TODO: As is mentioned before, use enabled schemata instead of all.
        newYaml.appendLine("schema_list:")
        availableSchemata.forEach { newYaml.appendLine("  - {schema: $it}") }

        dotYaml.writeText(newYaml.toString())
    }
}

private const val general =
"""
switches:
  fold_options: true
  abbreviate_options: true
  save_options:
    - emoji
    __include: convert

recognizer:
  patterns:
    uppercase: "[A-Z][0-9A-Za-z]*$"

"""

private const val punctuator =
"""
punctuator:
  digit_separators: ",.:"
  digit_separator_action: commit
  half_shape:
    ',' : { commit: ',' }
    '.' : { commit: '.' }
    '!' : { commit: '!' }
    '?' : { commit: '?' }
    ';' : { commit: ';' }
    ':' : { commit: ':' }
    '，' : { commit: '，' }
    '。' : { commit: '。' }
    '！' : { commit: '！' }
    '？' : { commit: '？' }
    '；' : { commit: '；' }
    '：' : { commit: '：' }

"""


// TODO: Should be able to detect automatically
private val availableSchemata = arrayOf(
    "rime_ice",               // 雾凇拼音（全拼）
    "t9",                     // 中文九键（仓输入法 & 元书输入法）
    "double_pinyin",          // 自然码双拼
    "double_pinyin_abc",      // 智能 ABC 双拼
    "double_pinyin_mspy",     // 微软双拼
    "double_pinyin_sogou",    // 搜狗双拼
    "double_pinyin_flypy",    // 小鹤双拼
    "double_pinyin_ziguang",  // 紫光双拼
    "double_pinyin_jiajia",   // 拼音加加双拼
)