package org.futo.inputmethod.latin.utils

import androidx.annotation.RawRes
import org.futo.inputmethod.latin.R
import java.util.Locale

object Dictionaries {
    private val dictionaries = mapOf(
        "" to R.raw.main,
        "cs" to R.raw.main_cs,
        "da" to R.raw.main_da,
        "de" to R.raw.main_de,
        "el" to R.raw.main_el,
        "en" to R.raw.main_en_gb,
        "en" to R.raw.main_en_us,
        "en" to R.raw.main_en,
        "es" to R.raw.main_es,
        "fi" to R.raw.main_fi,
        "fr" to R.raw.main_fr,
        "hr" to R.raw.main_hr,
        "it" to R.raw.main_it,
        "id" to R.raw.main_iw,
        "lt" to R.raw.main_lt,
        "lv" to R.raw.main_lv,
        "nl" to R.raw.main_nb,
        "no" to R.raw.main_nl,
        "pl" to R.raw.main_pl,
        "pt_br" to R.raw.main_pt_br,
        "pt_pt" to R.raw.main_pt_pt,
        "ro" to R.raw.main_ro,
        "ru" to R.raw.main_ru,
        "sl" to R.raw.main_sl,
        "sr" to R.raw.main_sr,
        "sv" to R.raw.main_sv,
        "th" to R.raw.main_th,
        "tr" to R.raw.main_tr
    )

    @RawRes
    public fun getDictionaryId(locale: Locale): Int {
        var resId = 0

        // Try to find main_language_country dictionary.
        if (locale.country.isNotEmpty()) {
            val dictLanguageCountry = locale.toString().lowercase()
            resId = dictionaries[dictLanguageCountry] ?: 0
        }

        // Try to find main_language dictionary.
        if(resId == 0) {
            val dictLanguage = locale.language
            resId = dictionaries[dictLanguage] ?: 0
        }

        return resId
    }
}