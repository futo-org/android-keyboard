package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Intent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.USE_TRANSFORMER_FINETUNING
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsBool
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.userSettingToggleSharedPrefs

private val visibilityCheckLMEnabled = @Composable {
    useSharedPrefsBool(Settings.PREF_KEY_USE_TRANSFORMER_LM, true).value
}

val PredictiveTextMenu = UserSettingsMenu(
    title = R.string.prediction_settings_title,
    navPath = "predictiveText", registerNavPath = true,
    settings = listOf(
        userSettingToggleSharedPrefs(
            title = R.string.prediction_settings_transformer,
            key = Settings.PREF_KEY_USE_TRANSFORMER_LM,
            default = { true }
        ),

        // if(transformerLmEnabled) {
        //userSettingToggleDataStore(
        //    title = R.string.prediction_settings_transformer_finetuning,
        //    subtitle = R.string.prediction_settings_transformer_finetuning_subtitle,
        //    setting = USE_TRANSFORMER_FINETUNING
        //).copy(visibilityCheck = visibilityCheckLMEnabled),

        userSettingNavigationItem(
            title = R.string.prediction_settings_transformer_models,
            style = NavigationItemStyle.HomeTertiary,
            navigateTo = "models",
            icon = R.drawable.cpu
        ).copy(visibilityCheck = visibilityCheckLMEnabled),

        userSettingNavigationItem(
            title = R.string.prediction_settings_transformer_advanced_params,
            style = NavigationItemStyle.HomeSecondary,
            navigateTo = "advancedparams",
            icon = R.drawable.code
        ).copy(visibilityCheck = visibilityCheckLMEnabled),

        userSettingDecorationOnly { Tip(stringResource(R.string.prediction_settings_transformer_alpha_notice)) }
            .copy(visibilityCheck = visibilityCheckLMEnabled),
        // }

        userSettingNavigationItem(
            title = R.string.edit_personal_dictionary,
            style = NavigationItemStyle.HomePrimary,
            icon = R.drawable.book,
            navigate = { nav ->
                nav.navigate("pdict")
            }
        ),

        userSettingNavigationItem(
            title = R.string.prediction_settings_word_blacklist,
            style = NavigationItemStyle.HomeSecondary,
            icon = R.drawable.file_text,
            navigateTo = "blacklist"
        ),

        // TODO: It doesn't make a lot of sense in the case of having autocorrect on but show_suggestions off
        userSettingToggleSharedPrefs(
            title = R.string.auto_correction,
            subtitle = R.string.auto_correction_summary,
            key = Settings.PREF_AUTO_CORRECTION,
            default = {true},
            icon = {
                Icon(painterResource(id = R.drawable.icon_spellcheck), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ).copy(searchTags = R.string.auto_correction_tags),

        userSettingToggleSharedPrefs(
            title = R.string.prediction_settings_smart_keyhit_detection,
            subtitle = R.string.prediction_settings_smart_keyhit_detection_subtitle,
            key = Settings.PREF_USE_DICT_KEY_BOOSTING,
            default = {true}
        ),

        userSettingToggleSharedPrefs(
            title = R.string.prefs_show_suggestions,
            subtitle = R.string.prefs_show_suggestions_summary,
            key = Settings.PREF_SHOW_SUGGESTIONS,
            default = {true}
        ),

        userSettingToggleSharedPrefs(
            title = R.string.use_personalized_dicts,
            subtitle = R.string.use_personalized_dicts_summary,
            key = Settings.PREF_KEY_USE_PERSONALIZED_DICTS,
            default = {true}
        ),

        //if(!transformerLmEnabled) {
        userSettingToggleSharedPrefs(
            title = R.string.bigram_prediction,
            subtitle = R.string.bigram_prediction_summary,
            key = Settings.PREF_BIGRAM_PREDICTIONS,
            default = { booleanResource(R.bool.config_default_next_word_prediction) }
        ).copy(visibilityCheck = {
            // Opposite of visibilityCheckLMEnabled
            !useSharedPrefsBool(Settings.PREF_KEY_USE_TRANSFORMER_LM, true).value
        })
        //}
    )
)