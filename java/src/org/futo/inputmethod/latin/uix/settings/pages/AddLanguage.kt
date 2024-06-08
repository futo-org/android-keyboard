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

fun makeQwertyWithPrimary(primary: String): List<String> {
    return listOf(primary) + QwertyVariants.filter { it != primary }
}

fun makeQwertyWithPrimary(primary: String, secondary: String): List<String> {
    return listOf(primary, secondary) + QwertyVariants.filter { it != primary && it != secondary }
}


val LocaleLayoutMap = mapOf(
    "af"     to   QwertyVariants,
    "ar"     to   listOf("arabic"),
    "az_AZ"  to   QwertyVariants,
    "be_BY"  to   listOf("east_slavic", "east_slavic_phonetic"),
    "bg"     to   listOf("bulgarian"),
    "bg"     to   listOf("bulgarian_bds"),
    "bn_BD"  to   listOf("bengali_akkhor"),
    "bn_IN"  to   listOf("bengali"),
    "ca"     to   makeQwertyWithPrimary("spanish"),
    "cs"     to   makeQwertyWithPrimary("qwertz"),
    "da"     to   makeQwertyWithPrimary("nordic"),
    "de"     to   makeQwertyWithPrimary("qwertz"),
    "de_CH"  to   makeQwertyWithPrimary("swiss"),
    "el"     to   makeQwertyWithPrimary("greek"),
    "en_IN"  to   QwertyVariants,
    "en_US"  to   QwertyVariants,
    "en_GB"  to   QwertyVariants,
    "eo"     to   makeQwertyWithPrimary("spanish"),
    "es"     to   makeQwertyWithPrimary("spanish"),
    "es_US"  to   makeQwertyWithPrimary("spanish"),
    "es_419" to   makeQwertyWithPrimary("spanish"),
    "et_EE"  to   makeQwertyWithPrimary("nordic"),
    "eu_ES"  to   makeQwertyWithPrimary("spanish"),
    "fa"     to   listOf("farsi"),
    "fi"     to   makeQwertyWithPrimary("nordic"),
    "fr"     to   makeQwertyWithPrimary("azerty", "swiss"),
    "fr_CA"  to   makeQwertyWithPrimary("qwerty", "swiss"),
    "fr_CH"  to   makeQwertyWithPrimary("swiss"),
    "gl_ES"  to   makeQwertyWithPrimary("spanish"),
    "hi"     to   listOf("hindi", "hindi_compact"),
    //"hi_ZZ"  to   QwertyVariants,
    "hr"     to   makeQwertyWithPrimary("qwertz"),
    "hu"     to   makeQwertyWithPrimary("qwertz"),
    "hy_AM"  to   listOf("armenian_phonetic"),
    "in"     to   QwertyVariants,
    "is"     to   QwertyVariants,
    "it"     to   QwertyVariants,
    "it_CH"  to   makeQwertyWithPrimary("swiss"),
    "iw"     to   listOf("hebrew"),
    "ka_GE"  to   listOf("georgian"),
    "kk"     to   listOf("east_slavic", "east_slavic_phonetic"),
    "km_KH"  to   listOf("khmer"),
    "kn_IN"  to   listOf("kannada"),
    "ky"     to   listOf("east_slavic", "east_slavic_phonetic"),
    "lo_LA"  to   listOf("lao"),
    "lt"     to   QwertyVariants,
    "lv"     to   QwertyVariants,
    "mk"     to   listOf("south_slavic"),
    "ml_IN"  to   listOf("malayalam"),
    "mn_MN"  to   listOf("mongolian"),
    "mr_IN"  to   listOf("marathi"),
    "ms_MY"  to   QwertyVariants,
    "nb"     to   listOf("nordic"),
    "ne_NP"  to   listOf("nepali_romanized", "nepali_traditional"),
    "nl"     to   QwertyVariants,
    "nl_BE"  to   makeQwertyWithPrimary("azerty"),
    "pl"     to   QwertyVariants,
    "pt_BR"  to   QwertyVariants,
    "pt_PT"  to   QwertyVariants,
    "ro"     to   QwertyVariants,
    "ru"     to   listOf("east_slavic", "east_slavic_phonetic"),
    "si_LK"  to   listOf("sinhala"),
    "sk"     to   QwertyVariants,
    "sl"     to   QwertyVariants,
    "sr"     to   listOf("south_slavic"),
    "sr_ZZ"  to   listOf("serbian_qwertz"),
    "sv"     to   listOf("nordic"),
    "sw"     to   QwertyVariants,
    "ta_IN"  to   listOf("tamil"),
    "ta_LK"  to   listOf("tamil"),
    "ta_SG"  to   listOf("tamil"),
    "te_IN"  to   listOf("telugu"),
    "th"     to   listOf("thai"),
    "tl"     to   makeQwertyWithPrimary("spanish"),
    "tr"     to   QwertyVariants,
    "uk"     to   listOf("east_slavic", "east_slavic_phonetic"),
    "uz_UZ"  to   listOf("uzbek"),
    "vi"     to   QwertyVariants,
    "zu"     to   QwertyVariants,
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