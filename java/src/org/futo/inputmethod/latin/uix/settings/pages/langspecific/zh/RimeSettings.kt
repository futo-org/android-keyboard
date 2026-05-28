package org.futo.inputmethod.latin.uix.settings.pages.langspecific.zh

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.stringPreferencesKey
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.utils.toEnumOrNull

object RimeSettings {

    val SharedDataDir: String get() = ""
    val UserDataDir: String get() = ""

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
        },
    )
}