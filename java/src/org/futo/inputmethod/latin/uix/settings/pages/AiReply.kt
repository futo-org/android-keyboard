package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.ENABLE_AI_REPLY
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore

val AiReplyMenu = UserSettingsMenu(
    title = R.string.ai_reply_settings_title,
    navPath = "aiReply", registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.ai_reply_enable,
            setting = ENABLE_AI_REPLY
        ),
        userSettingNavigationItem(
            title = R.string.ai_reply_groq_config,
            subtitle = R.string.ai_reply_groq_config_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = "groq"
        )
    )
)
