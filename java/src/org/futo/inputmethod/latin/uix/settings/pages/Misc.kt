package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsExporter
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem

val MiscMenu = UserSettingsMenu(
    title = R.string.misc_settings_title,
    navPath = "misc", registerNavPath = true,
    settings = listOf(
        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.settings_export_configuration_title))
        },

        userSettingNavigationItem(
            title = (R.string.settings_export_configuration),
            subtitle = (R.string.settings_export_configuration_subtitle),
            style = NavigationItemStyle.Misc,
            navigateTo = "exportingcfg"
        ).copy(searchTags = R.string.settings_import_export_tags),
        userSettingNavigationItem(
            title = (R.string.settings_import_configuration),
            subtitle = (R.string.settings_import_configuration_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { nav ->
                SettingsExporter.triggerImportSettings(nav.context)
            }
        ).copy(searchTags = R.string.settings_import_export_tags),
    )
)