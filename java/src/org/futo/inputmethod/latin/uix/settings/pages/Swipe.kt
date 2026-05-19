package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.DisplayTop4Setting
import org.futo.inputmethod.latin.LegacySwipeSetting
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.AutoFitText
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.SuggestionSeparator
import org.futo.inputmethod.latin.uix.settings.SettingRadio
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.suggestionStyleAlternative
import org.futo.inputmethod.latin.uix.suggestionStylePrimary


@Composable
private fun RowScope.PreviewSuggestionItem(word: String, isAutocorrect: Boolean) {
    val color = when(isAutocorrect) {
        true -> LocalKeyboardScheme.current.onSurface
        else -> LocalKeyboardScheme.current.onSurfaceVariant
    }

    val textStyle = when(isAutocorrect) {
        true -> suggestionStylePrimary
        false -> suggestionStyleAlternative
    }.copy(color = color)

    Box(
        modifier = Modifier
            .weight(1.0f)
            .fillMaxHeight()
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            val modifier = Modifier
                .align(Center)
                .padding(2.dp)
            AutoFitText(word, style = textStyle, modifier = modifier)
        }
    }
}

@Composable
private fun PreviewSuggestions(word1: String, word2: String, word3: String, isAlt: Boolean) {
    Row(Modifier.fillMaxWidth().background(
        LocalKeyboardScheme.current.keyboardSurfaceDim, RoundedCornerShape(4.dp)
    ).border(
        Dp.Hairline, LocalKeyboardScheme.current.outline.copy(alpha=0.3f), RoundedCornerShape(4.dp)
    ).padding(4.dp))  {
        PreviewSuggestionItem(word1, false)
        SuggestionSeparator()
        PreviewSuggestionItem(word2, !isAlt)
        SuggestionSeparator()
        PreviewSuggestionItem(word3, false)
    }
}

val SwipeMenu = UserSettingsMenu(
    title = R.string.swipe_settings_title,
    navPath = "swipe", registerNavPath = true,
    settings = listOf(
        userSettingToggleSharedPrefs(
            title = R.string.swipe_settings_enable_swipe_typing,
            subtitle = R.string.swipe_settings_enable_swipe_typing_subtitle,
            key = Settings.PREF_GESTURE_INPUT,
            default = {true},
        ),

        UserSetting(
            name = R.string.swipe_settings_suggest_mode,
            searchTags = R.string.swipe_settings_suggest_mode_tags
        ) {
            val alt1 = stringResource(R.string.swipe_settings_suggest_mode_alternative_word_1)
            val alt2 = stringResource(R.string.swipe_settings_suggest_mode_alternative_word_2)
            val alt3 = stringResource(R.string.swipe_settings_suggest_mode_alternative_word_3)
            val main = stringResource(R.string.swipe_settings_suggest_mode_used_word)

            val setting = useDataStore(DisplayTop4Setting)

            SettingRadio(
                title = stringResource(R.string.swipe_settings_suggest_mode),
                options = listOf(true, false),
                optionNames = listOf(
                    stringResource(R.string.swipe_settings_suggest_mode_top4_title),
                    stringResource(R.string.swipe_settings_suggest_mode_top3_title),
                ),
                setting = setting,
                subcontent = listOf(
                    { PreviewSuggestions(alt2, alt1, alt3, true) },
                    { PreviewSuggestions(alt1, main, alt2, false) },
                )
            )
        },

        userSettingDecorationOnly {
            val useLegacy = useDataStoreValue(LegacySwipeSetting)
            val showUseLegacy = remember { mutableStateOf(useLegacy) }

            Spacer(Modifier.height(64.dp))
            if(!showUseLegacy.value) {
                TextButton(onClick = {
                    showUseLegacy.value = true
                }) {
                    Text(stringResource(R.string.swipe_settings_show_unsupported))
                }
            } else {
                SettingToggleDataStore(
                    title = stringResource(R.string.swipe_settings_use_old_swipe),
                    subtitle = stringResource(
                        if(useLegacy) R.string.swipe_settings_use_old_swipe_subtitle else R.string.swipe_settings_use_old_swipe_subtitle_short),
                    setting = LegacySwipeSetting
                )
            }
        },
    )
)