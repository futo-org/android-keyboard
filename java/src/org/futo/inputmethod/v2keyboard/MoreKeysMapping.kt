package org.futo.inputmethod.v2keyboard

import org.futo.inputmethod.keyboard.internal.isAlphabet
import org.futo.inputmethod.latin.common.Constants

fun getDefaultMoreKeysForKey(code: Int, relevantSpecShortcut: List<String>?): String {
    if(code == Constants.CODE_ENTER) {
        return "!text/keyspec_emoji_action_key"
    } else if (relevantSpecShortcut != null) {
        return relevantSpecShortcut.subList(1, relevantSpecShortcut.size).joinToString(",")
    } else {
        return ""
    }
}


fun getSpecialFromRow(keyCoordinate: KeyCoordinate, row: Row): String {
    if(row.isBottomRow && keyCoordinate.element.kind.isAlphabet) {
        val numCols = keyCoordinate.measurement.numColumnsByRow.getOrNull(keyCoordinate.regularRow) ?: -10
        if(keyCoordinate.regularColumn == 0) {
            return "!text/morekeys_bottomrow_comma"
        }else if(keyCoordinate.regularColumn == numCols - 1) {
            return "!text/morekeys_period"
        }
    }
    return ""
}