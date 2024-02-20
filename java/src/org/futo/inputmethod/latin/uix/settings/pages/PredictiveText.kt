package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingRadio
import org.futo.inputmethod.latin.uix.settings.SettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsBool
import org.futo.inputmethod.latin.xlm.AutocorrectThresholdSetting
import org.futo.inputmethod.latin.xlm.BinaryDictTransformerWeightSetting

@Preview
@Composable
fun PredictiveTextScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    ScrollableList {
        ScreenTitle("Predictive Text", showBack = true, navController)

        val (transformerLmEnabled, _) = useSharedPrefsBool(Settings.PREF_KEY_USE_TRANSFORMER_LM, true)


        SettingToggleSharedPrefs(
            title = "Transformer LM",
            key = Settings.PREF_KEY_USE_TRANSFORMER_LM,
            default = true
        )

        if(transformerLmEnabled) {
            NavigationItem(
                title = "Models",
                style = NavigationItemStyle.HomeTertiary,
                navigate = { navController.navigate("models") },
                icon = painterResource(id = R.drawable.cpu)
            )

            Tip("Note: Transformer LM is in alpha state")
        }


        // TODO: It doesn't make a lot of sense in the case of having autocorrect on but show_suggestions off

        SettingToggleSharedPrefs(
            title = stringResource(R.string.auto_correction),
            subtitle = stringResource(R.string.auto_correction_summary),
            key = Settings.PREF_AUTO_CORRECTION,
            default = true
        )
        
        SettingToggleSharedPrefs(
            title = stringResource(R.string.prefs_show_suggestions),
            subtitle = stringResource(R.string.prefs_show_suggestions_summary),
            key = Settings.PREF_SHOW_SUGGESTIONS,
            default = true
        )

        if(!transformerLmEnabled) {
            NavigationItem(
                title = stringResource(R.string.edit_personal_dictionary),
                style = NavigationItemStyle.Misc,
                navigate = {
                    val intent = Intent("android.settings.USER_DICTIONARY_SETTINGS")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )

            /*
            NavigationItem(
                title = stringResource(R.string.configure_dictionaries_title),
                style = NavigationItemStyle.Misc,
                navigate = {
                    val intent = Intent()
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    intent.setClass(context, DictionarySettingsActivity::class.java)
                    intent.putExtra("clientId", "org.futo.inputmethod.latin")
                    context.startActivity(intent)
                }
            )
            */

            SettingToggleSharedPrefs(
                title = stringResource(R.string.prefs_block_potentially_offensive_title),
                subtitle = stringResource(R.string.prefs_block_potentially_offensive_summary),
                key = Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
                default = booleanResource(R.bool.config_block_potentially_offensive)
            )
        }

        SettingToggleSharedPrefs(
            title = stringResource(R.string.use_personalized_dicts),
            subtitle = stringResource(R.string.use_personalized_dicts_summary),
            key = Settings.PREF_KEY_USE_PERSONALIZED_DICTS,
            default = true
        )

        if(!transformerLmEnabled) {
            SettingToggleSharedPrefs(
                title = stringResource(R.string.bigram_prediction),
                subtitle = stringResource(R.string.bigram_prediction_summary),
                key = Settings.PREF_BIGRAM_PREDICTIONS,
                default = booleanResource(R.bool.config_default_next_word_prediction)
            )
        }

        if(transformerLmEnabled) {
            val optionsWeight = mapOf(
                Float.NEGATIVE_INFINITY to "always BinaryDictionary, except if blank",
                0.0001f to "significantly favor BinaryDictionary",
                0.5f to "favor BinaryDictionary",
                1.0f to "normal",
                2.0f to "favor TransformerLM",
                4.0f to "significantly favor TransformerLM",
                Float.POSITIVE_INFINITY to "always TransformerLM"
            )
            val namesWeight = optionsWeight.map { "a = ${it.key} (${it.value})" }
            SettingRadio(
                title = "Weight of Transformer LM suggestions with respect to BinaryDictionary",
                options = optionsWeight.keys.toList(),
                optionNames = namesWeight,
                setting = BinaryDictTransformerWeightSetting
            )


            Tip("Adjust the autocorrect threshold below. A lower threshold will autocorrect more often (and miscorrect more often), while a higher threshold will autocorrect less often (and miscorrect less often)" )
            val options = mapOf(
                0.0f to "none (94.6% : 5.4%)",
                1.0f to "very low (93.4% : 4.3%)",
                2.0f to "very low (91.2% : 2.4%)",
                4.0f to "low (87.3% : 1.4%)",
                6.0f to "low (no data)",
                8.0f to "medium (82.3% : 0.9%)",
                10.0f to "medium (80.1% : 0.8%)",
                14.0f to "medium (no data)",
                18.0f to "high (74.8% : 0.5%)",
                25.0f to "high (71.6% : 0.4%)",
                50.0f to "very high (63.5% : 0.3%)",
                100.0f to "very high (54.7% : 0.2%)"
            )
            val names = options.map { "T = ${it.key}" }
            SettingRadio(
                title = "Autocorrect Threshold",
                options = options.keys.toList(),
                optionNames = names,
                setting = AutocorrectThresholdSetting
            )
        }
    }
}