package org.futo.inputmethod.latin.uix.settings.pages.langspecific.zh

import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu

object ChineseIMESettings {
    val menu = UserSettingsMenu(
        title = R.string.chinese_settings_title,
        searchTags = R.string.chinese_setting_search_tags,
        navPath = "ime/zh", registerNavPath = true,
        settings = RimeSettings.settings + PinyinSettings.settings
    )
}
