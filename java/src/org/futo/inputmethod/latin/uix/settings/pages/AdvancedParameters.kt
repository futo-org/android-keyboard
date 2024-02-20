package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingRadio
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.selector.ThemePicker
import org.futo.inputmethod.latin.xlm.AutocorrectThresholdSetting
import org.futo.inputmethod.latin.xlm.BinaryDictTransformerWeightSetting

@Preview
@Composable
fun AdvancedParametersScreen(navController: NavHostController = rememberNavController()) {
    ScrollableList {
        ScreenTitle("Advanced Parameters", showBack = true, navController)

        val optionsWeight = mapOf(
            Float.NEGATIVE_INFINITY to "always BinaryDictionary, except if blank",
            0.0001f to "significantly favor BinaryDictionary, except if BinaryDictionary score < 0",
            0.01f to "favor BinaryDictionary",
            0.1f to "favor BinaryDictionary",
            0.2f to "favor BinaryDictionary",
            0.3f to "favor BinaryDictionary",
            0.4f to "favor BinaryDictionary",
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