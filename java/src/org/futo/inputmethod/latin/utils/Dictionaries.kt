package org.futo.inputmethod.latin.utils

import androidx.annotation.RawRes
import org.futo.inputmethod.latin.R
import java.util.Locale

object Dictionaries {
    private val dictionaries = mapOf(
        "" to R.raw.main,
        "de" to R.raw.main_de,
        "en" to R.raw.main_en,
        "es" to R.raw.main_es,
        "fr" to R.raw.main_fr,
        "it" to R.raw.main_it,
        "pt_br" to R.raw.main_pt_br,
        "ru" to R.raw.main_ru
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