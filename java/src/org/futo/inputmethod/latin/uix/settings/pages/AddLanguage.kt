package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.uix.settings.DropDownPicker
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem

val QwertyVariants = listOf("qwerty", "qwertz", "dvorak", "azerty", "colemak", "bepo", "pcqwerty")

val LocaleLayoutMap = mapOf(
    "af"     to   listOf("qwerty"),
    "ar"     to   listOf("arabic"),
    "az_AZ"  to   listOf("qwerty"),
    "be_BY"  to   listOf("east_slavic", "east_slavic_phonetic"),
    "bg"     to   listOf("bulgarian"),
    "bg"     to   listOf("bulgarian_bds"),
    "bn_BD"  to   listOf("bengali_akkhor"),
    "bn_IN"  to   listOf("bengali"),
    "ca"     to   listOf("spanish"),
    "cs"     to   listOf("qwertz"),
    "da"     to   listOf("nordic"),
    "de"     to   listOf("qwertz"),
    "de_CH"  to   listOf("swiss"),
    "el"     to   listOf("greek"),
    "en_IN"  to   listOf("qwerty"),
    "en_US"  to   QwertyVariants,
    "en_GB"  to   QwertyVariants,
    "eo"     to   listOf("spanish"),
    "es"     to   listOf("spanish"),
    "es_US"  to   listOf("spanish"),
    "es_419" to   listOf("spanish"),
    "et_EE"  to   listOf("nordic"),
    "eu_ES"  to   listOf("spanish"),
    "fa"     to   listOf("farsi"),
    "fi"     to   listOf("nordic"),
    "fr"     to   listOf("azerty", "qwerty", "swiss", "bepo"),
    "fr_CA"  to   listOf("qwerty", "azerty", "swiss", "bepo"),
    "fr_CH"  to   listOf("swiss", "qwerty", "azerty", "bepo"),
    "gl_ES"  to   listOf("spanish"),
    "hi"     to   listOf("hindi", "hindi_compact"),
    //"hi_ZZ"  to   listOf("qwerty"),
    "hr"     to   listOf("qwertz"),
    "hu"     to   listOf("qwertz"),
    "hy_AM"  to   listOf("armenian_phonetic"),
    "in"     to   listOf("qwerty"),
    "is"     to   listOf("qwerty"),
    "it"     to   listOf("qwerty"),
    "it_CH"  to   listOf("swiss"),
    "iw"     to   listOf("hebrew"),
    "ka_GE"  to   listOf("georgian"),
    "kk"     to   listOf("east_slavic", "east_slavic_phonetic"),
    "km_KH"  to   listOf("khmer"),
    "kn_IN"  to   listOf("kannada"),
    "ky"     to   listOf("east_slavic", "east_slavic_phonetic"),
    "lo_LA"  to   listOf("lao"),
    "lt"     to   listOf("qwerty"),
    "lv"     to   listOf("qwerty"),
    "mk"     to   listOf("south_slavic"),
    "ml_IN"  to   listOf("malayalam"),
    "mn_MN"  to   listOf("mongolian"),
    "mr_IN"  to   listOf("marathi"),
    "ms_MY"  to   listOf("qwerty"),
    "nb"     to   listOf("nordic"),
    "ne_NP"  to   listOf("nepali_romanized", "nepali_traditional"),
    "nl"     to   listOf("qwerty"),
    "nl_BE"  to   listOf("azerty"),
    "pl"     to   listOf("qwerty"),
    "pt_BR"  to   listOf("qwerty"),
    "pt_PT"  to   listOf("qwerty"),
    "ro"     to   listOf("qwerty"),
    "ru"     to   listOf("east_slavic", "east_slavic_phonetic"),
    "si_LK"  to   listOf("sinhala"),
    "sk"     to   listOf("qwerty"),
    "sl"     to   listOf("qwerty"),
    "sr"     to   listOf("south_slavic"),
    "sr_ZZ"  to   listOf("serbian_qwertz"),
    "sv"     to   listOf("nordic"),
    "sw"     to   listOf("qwerty"),
    "ta_IN"  to   listOf("tamil"),
    "ta_LK"  to   listOf("tamil"),
    "ta_SG"  to   listOf("tamil"),
    "te_IN"  to   listOf("telugu"),
    "th"     to   listOf("thai"),
    "tl"     to   listOf("spanish"),
    "tr"     to   listOf("qwerty"),
    "uk"     to   listOf("east_slavic", "east_slavic_phonetic"),
    "uz_UZ"  to   listOf("uzbek"),
    "vi"     to   listOf("qwerty"),
    "zu"     to   listOf("qwerty"),
    "zz"     to   QwertyVariants,
)

@Preview
@Composable
fun AddLanguageScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    val selectedLocale: MutableState<String> = remember { mutableStateOf(context.resources.configuration.locale.toString()) }
    val selectedLayout: MutableState<String> = remember { mutableStateOf("qwerty") }

    val keys = remember { LocaleLayoutMap.keys.toList() }
    ScrollableList {
        ScreenTitle("Add Language", showBack = true, navController)

        SettingItem(title = "Language") {
            DropDownPicker(
                "",
                keys,
                selectedLocale.value,
                {
                    selectedLocale.value = it
                    selectedLayout.value = LocaleLayoutMap[it]!!.first()
                },
                {
                    Subtypes.getNameForLocale(it)
                },
                modifier = Modifier.width(180.dp)
            )
        }

        SettingItem(title = "Layout") {
            DropDownPicker(
                "",
                LocaleLayoutMap[selectedLocale.value] ?: listOf(),
                selectedLayout.value,
                { selectedLayout.value = it },
                { Subtypes.getLayoutName(context, it) },
                modifier = Modifier.width(180.dp)
            )
        }

        Button(onClick = {
            Subtypes.addLanguage(
                context,
                Subtypes.getLocale(selectedLocale.value),
                selectedLayout.value
            )

            navController.navigateUp()
        }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Add")
        }
    }
}