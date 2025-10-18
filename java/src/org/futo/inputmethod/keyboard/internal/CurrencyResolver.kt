package org.futo.inputmethod.keyboard.internal

import android.icu.util.ULocale
import android.util.Log
import java.util.Currency
import java.util.Locale

object CurrencyResolver {
    private val USD = "$"
    private val EUR = "€"
    private val RUB = "₽"

    private val CurrencyCorrectionMap = mapOf(
        "RU" to "₽",
        "NP" to "रु",
        "GH" to "₵",

        // Although these countries don't use EUR, their currency is writeable
        // with alphabet and the next most relevant symbol is EUR
        "CZ" to EUR,
        "DK" to EUR,
        "CH" to EUR,
        "BG" to EUR,
        "MD" to EUR,
        "HR" to EUR,
        "HU" to EUR,
        "IS" to EUR,
        "MK" to EUR,
        "SE" to EUR,
        "PL" to EUR,
        "AL" to EUR,
        "RS" to EUR,
        "MA" to EUR,
        "NO" to EUR,
        "RO" to EUR,

        "BY" to RUB,

        "KG" to USD,
        "TJ" to USD,

        "ZA" to USD,
        "GH" to USD,
        "EG" to USD,
        "IQ" to USD,
        "IR" to USD,
        "ID" to USD,
        "DZ" to USD,
        "UZ" to USD,
        "MY" to USD,
        "MM" to USD,
        "BR" to USD,
        "TZ" to USD,
        "LK" to USD,
        "TM" to USD,
        "UA" to USD,
        "PK" to USD,
    )

    fun resolve(locale: Locale): String? {
        var locale = locale
        if (locale.country.isEmpty()) {
            try {
                locale = ULocale.addLikelySubtags(ULocale.forLocale(locale)).toLocale()
            } catch(e: Exception) { }
        }

        val currency = CurrencyCorrectionMap[locale.country] ?: try {
            Currency.getInstance(locale).getSymbol(locale)
        } catch(e: Exception) {
            null
        }

        return currency
    }
}