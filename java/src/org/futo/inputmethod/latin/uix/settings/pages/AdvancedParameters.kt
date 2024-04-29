package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.xlm.AutocorrectThresholdSetting
import org.futo.inputmethod.latin.xlm.BinaryDictTransformerWeightSetting

@Preview
@Composable
fun AdvancedParametersScreen(navController: NavHostController = rememberNavController()) {
    ScrollableList {
        ScreenTitle("Advanced Parameters", showBack = true, navController)

        Tip("Options below are experimental and may be removed or changed in the future as internal workings of the app change. Changing these values may have an adverse impact on your experience.\n\nNote: These only affect English")

        SettingSlider(
            title = "Transformer LM strength",
            subtitle = "Lower value will make autocorrect behave more similarly to standard AOSP keyboard, while higher value will make it more dependent on the neural network\nDefault is ${BinaryDictTransformerWeightSetting.default}",
            setting = BinaryDictTransformerWeightSetting,
            range = 0.0f .. 100.0f,
            transform = {
                if(it > 99.9f) {
                    Float.POSITIVE_INFINITY
                } else if(it < 0.0001f) {
                    Float.NEGATIVE_INFINITY
                } else {
                    it
                }
            },
            indicator = {
                when {
                    it == Float.POSITIVE_INFINITY -> {
                        "always Transformer LM"
                    }
                    it == Float.NEGATIVE_INFINITY -> {
                        "always Binary Dictionary"
                    }
                    (it > 0.1f) -> {
                        "a = ${String.format("%.1f", it)}"
                    }
                    else -> {
                        "a = $it"
                    }
                }
            },
            power = 2.5f
        )

        SettingSlider(
            title = "Autocorrect Threshold",
            subtitle = "A lower threshold will autocorrect more often (and miscorrect more often), while a higher threshold will autocorrect less often (and miscorrect less often).\nDefault is ${AutocorrectThresholdSetting.default}",
            setting = AutocorrectThresholdSetting,
            range = 0.0f .. 25.0f,
            hardRange = 0.0f .. 25.0f,
            transform = {
                it
            },
            indicator = {
                "T = ${String.format("%.1f", it)}"
            },
            power = 2.5f
        )

    }
}