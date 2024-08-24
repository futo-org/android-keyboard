package org.futo.inputmethod.v2keyboard

import org.futo.inputmethod.latin.common.Constants

fun getDefaultMoreKeysForKey(code: Int, relevantSpecShortcut: List<String>?): String {
    if(code == Constants.CODE_ENTER) {
        return "!text/keyspec_emoji_action_key"
    } else if(code >= 'a'.code && code <= 'z'.code) {
        return "!text/morekeys_${code.toChar()},!text/morekeys_misc_${code.toChar()}"
    } else if (relevantSpecShortcut != null) {
        return relevantSpecShortcut.subList(1, relevantSpecShortcut.size).joinToString(",")
    } else {
        return ""
    }
}

val QwertySymbols = listOf(
    "qwertyuiop".toList(),
    "asdfghjkl".toList(),
    "zxcvbnm".toList()
)

fun getSymsForCoordinate(keyCoordinate: KeyCoordinate): String {
    if(keyCoordinate.element != KeyboardElement.Alphabet) return ""

    val row = QwertySymbols.getOrNull(keyCoordinate.regularRow)
    val letter = row?.getOrNull(keyCoordinate.regularColumn)
    return if(letter != null) {
        "!text/qwertysyms_$letter,!text/actions_$letter"
    } else {
        ""
    }
}

fun getNumForCoordinate(keyCoordinate: KeyCoordinate): String {
    if(keyCoordinate.element != KeyboardElement.Alphabet) return ""

    if(keyCoordinate.regularRow == 0 && keyCoordinate.regularColumn <= 9) {
        if(keyCoordinate.regularColumn == 9) {
            return "!text/keyspec_symbols_0"
        } else {
            return "!text/keyspec_symbols_${keyCoordinate.regularColumn + 1}"
        }
    }
    return ""
}