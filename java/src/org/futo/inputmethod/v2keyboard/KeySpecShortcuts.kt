package org.futo.inputmethod.v2keyboard

val KeySpecShortcuts = mapOf(
    "q" to listOf("keyspec_q"),
    "," to listOf("keyspec_comma", "morekeys_comma"),
    "." to listOf("keyspec_period", "morekeys_period"),
    "1" to listOf("keyspec_symbols_1", "additional_morekeys_symbols_1", "morekeys_symbols_1"),
    "2" to listOf("keyspec_symbols_2", "additional_morekeys_symbols_2", "morekeys_symbols_2"),
    "3" to listOf("keyspec_symbols_3", "additional_morekeys_symbols_3", "morekeys_symbols_3"),
    "4" to listOf("keyspec_symbols_4", "additional_morekeys_symbols_4", "morekeys_symbols_4"),
    "5" to listOf("keyspec_symbols_5", "additional_morekeys_symbols_5", "morekeys_symbols_5"),
    "6" to listOf("keyspec_symbols_6", "additional_morekeys_symbols_6", "morekeys_symbols_6"),
    "7" to listOf("keyspec_symbols_7", "additional_morekeys_symbols_7", "morekeys_symbols_7"),
    "8" to listOf("keyspec_symbols_8", "additional_morekeys_symbols_8", "morekeys_symbols_8"),
    "9" to listOf("keyspec_symbols_9", "additional_morekeys_symbols_9", "morekeys_symbols_9"),
    "0" to listOf("keyspec_symbols_0", "additional_morekeys_symbols_0", "morekeys_symbols_0"),
    ";" to listOf("keyspec_symbols_semicolon", "morekeys_symbols_semicolon"),
    "!" to listOf("!", "morekeys_exclamation"),
    "+" to listOf("+", "morekeys_plus"),
    "?" to listOf("keyspec_symbols_question", "morekeys_question"),
    "\"" to listOf("\"", "morekeys_double_quote"),
    "\'" to listOf("\'", "morekeys_single_quote"),
    "$" to listOf("keyspec_currency", "morekeys_currency_generic"),
    "%" to listOf("keyspec_symbols_percent", "morekeys_symbols_percent"),
    "(" to listOf("keyspec_left_parenthesis", "morekeys_left_parenthesis"),
    ")" to listOf("keyspec_right_parenthesis", "morekeys_right_parenthesis"),
    "<" to listOf("keyspec_less_than", "morekeys_less_than"),
    ">" to listOf("keyspec_greater_than", "morekeys_greater_than"),
    "[" to listOf("keyspec_left_square_bracket"),
    "]" to listOf("keyspec_right_square_bracket"),
    "{" to listOf("keyspec_left_curly_bracket"),
    "}" to listOf("keyspec_right_curly_bracket"),

    // U+2260: "≠" NOT EQUAL TO
    // U+2248: "≈" ALMOST EQUAL TO
    // U+221E: "∞" INFINITY
    "=" to listOf("=", "\u2260", "\u2248", "\u221E"),

    // Cyrillic
    "щ" to listOf("keyspec_east_slavic_row1_9"),
    "ы" to listOf("keyspec_east_slavic_row2_2", "morekeys_east_slavic_row2_2"),
    "э" to listOf("keyspec_east_slavic_row2_11", "morekeys_east_slaiv_row2_11"),
    "и" to listOf("keyspec_east_slavic_row3_5"),
    "\u044c" to listOf("\u044c", "morekeys_cyrillic_soft_sign"),
    "\u0443" to listOf("\u0443", "morekeys_cyrillic_u"),
    "\u043a" to listOf("\u043a", "morekeys_cyrillic_ka"),
    "\u043d" to listOf("\u043d", "morekeys_cyrillic_en"),
    "\u0433" to listOf("\u0433", "morekeys_cyrillic_ghe"),
    "\u0430" to listOf("\u0430", "morekeys_cyrillic_a"),
    "\u043e" to listOf("\u043e", "morekeys_cyrillic_o"),
    "\u0438" to listOf("\u0438", "morekeys_cyrillic_i"),
    "\u0435" to listOf("\u0435", "morekeys_cyrillic_ie"),
).mapValues {
    it.value.map { v ->
        if(v.startsWith("keyspec") || v.startsWith("morekeys") || v.startsWith("additional_")) {
            "!text/$v"
        } else {
            v
        }
    }
}