package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.DisplayTop4Setting
import org.futo.inputmethod.latin.LegacySwipeSetting
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.RichInputMethodSubtype
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.AutoFitText
import org.futo.inputmethod.latin.uix.KeyboardLayoutPreview
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.SettingsTextEdit
import org.futo.inputmethod.latin.uix.SuggestionSeparator
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingRadio
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.settings.userSettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.suggestionStyleAlternative
import org.futo.inputmethod.latin.uix.suggestionStylePrimary
import org.futo.inputmethod.v2keyboard.LayoutManager
import java.util.Locale


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

@Composable
@Preview
fun KASROZMenu() {
    val context = LocalContext.current
    val width = (LocalConfiguration.current.screenWidthDp - 64).coerceAtLeast(64).coerceAtMost(500)

    val subtypes = useDataStoreValue(SubtypesSetting)
    val showingKeyboard = remember { mutableStateOf(false) }

    val englishSubtypes = remember(subtypes) {
        subtypes
            .map { Subtypes.convertToSubtype(it) }
            .map { RichInputMethodSubtype(it) }
            .filter { it.locale.language.equals("en", ignoreCase = true) }
    }

    val englishLocale = remember(englishSubtypes) {
        englishSubtypes.firstOrNull()?.locale ?: Locale("en", "US")
    }

    val englishLayouts = remember(englishSubtypes) {
        englishSubtypes.map {
            it.keyboardLayoutSetName to LayoutManager.getLayout(context, it.keyboardLayoutSetName).name
        }
    }

    val kasrozEnabled = remember(englishSubtypes) {
        englishLayouts.any { it.first == "kasroz" }
    }

    val nonKasrozLayout = remember(englishSubtypes) {
        englishLayouts.firstOrNull { it.first != "kasroz" }?.second ?: "QWERTY"
    }

    val switchingInstruction = remember {
        val settings = Settings.getInstance().current
        if(settings != null) {
            if(settings.mSpacebarHoldMode == Settings.SPACEBAR_MODE_LANGUAGE) {
                "long-press the spacebar"
            } else if(settings.mSpacebarSwipeMode == Settings.SPACEBAR_MODE_LANGUAGE) {
                "swipe the spacebar left or right (up or down in KASROZ)"
            } else {
                "use the globe icon"
            }
        } else {
            "use the built-in language switcher"
        }
    }

    ScrollableList(horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenTitle(stringResource(R.string.swipe_settings_kasroz), showBack = true)
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(!showingKeyboard.value, exit = shrinkVertically()) {
            KeyboardLayoutPreview(
                id = "kasroz",
                width = width.dp,
                locale = Locale.ENGLISH,
                shifted = true
            )
        }

        if(showingKeyboard.value) {
            val textFieldValue = remember { mutableStateOf("") }
            Box(Modifier.padding(24.dp)) {
                SettingsTextEdit(textFieldValue, autofocus = true,
                    forcedLayout = "org.futo.inputmethod.latin.ForceLayout=kasroz,org.futo.inputmethod.latin.ForceLocale=${englishLocale.toLanguageTag()},")
            }
        }
        Text("KASROZ is the best way to swipe type, being specifically optimized to reduce mistakes in English.\n\nAfter enabling, ${switchingInstruction} to switch between KASROZ and ${nonKasrozLayout}.", modifier = Modifier.padding(24.dp))

        SettingToggleRaw(
            "Enable KASROZ Layout",
            enabled = kasrozEnabled,
            setValue = {
                if(it) {
                    Subtypes.addLanguage(context, englishLocale, "kasroz")
                } else {
                    context.getSetting(SubtypesSetting).filter {
                        it.startsWith("en", ignoreCase = true) && "KeyboardLayoutSet=kasroz" in it
                    }.forEach {
                        Subtypes.removeLanguage(context, Subtypes.convertToSubtype(it))
                    }
                }
            }
        )

        NavigationItem("Try it",
            style = NavigationItemStyle.Misc,
            navigate = {
                showingKeyboard.value = false
                showingKeyboard.value = true
            })
        NavigationItem("Read our blog",
            style = NavigationItemStyle.ExternalLink,
            navigate = { })

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

        userSettingToggleSharedPrefs(
            title = R.string.swipe_settings_sensitive_swipe,
            subtitle = R.string.swipe_settings_sensitive_swipe_subtitle,
            key = Settings.PREF_GESTURE_INPUT_SENSITIVITY,
            default = {false},
        ),

        UserSetting(R.string.swipe_settings_kasroz, subtitle = R.string.swipe_settings_kasroz_subtitle) {
            val nav = LocalNavController.current
            NavigationItem(
                stringResource(R.string.swipe_settings_kasroz),
                subtitle = stringResource(R.string.swipe_settings_kasroz_subtitle),
                style = NavigationItemStyle.Misc,
                navigate = {
                    nav?.navigate("kasroz")
                }
            )
        },

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
            ScreenTitle(stringResource(R.string.swipe_settings_miscellaneous_title))
        },

        userSettingNavigationItem(
            title = R.string.swipe_settings_configure_shortcuts,
            subtitle = R.string.swipe_settings_configure_shortcuts_subtitle,
            navigateTo = LongPressMenu.navPath,
            style = NavigationItemStyle.Misc
        ).copy(appearsInSearch=false),

        userSettingDecorationOnly {
            val useLegacy = useDataStoreValue(LegacySwipeSetting)
            val showUseLegacy = remember { mutableStateOf(useLegacy) }

            Spacer(Modifier.height(8.dp))
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