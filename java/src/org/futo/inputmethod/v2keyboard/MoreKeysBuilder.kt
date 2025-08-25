package org.futo.inputmethod.v2keyboard

import org.futo.inputmethod.keyboard.KeyConsts
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.keyboard.internal.MoreKeySpec
import org.futo.inputmethod.keyboard.internal.isAlphabet
import org.futo.inputmethod.latin.settings.LongPressKey

typealias MoreKeys = List<String>

val QwertySymbols = listOf(
    "qwertyuiop".toList(),
    "asdfghjkl".toList() + listOf("r2_e1"),
    "zxcvbnm".toList()
)

private fun getNumForCoordinate(keyCoordinate: KeyCoordinate): String {
    if(!keyCoordinate.element.kind.isAlphabet) return ""

    if(keyCoordinate.regularRow == 0) {
        val colOffset = (keyCoordinate.measurement.numColumnsByRow[keyCoordinate.regularRow] - 10) / 2
        val centeredCol = keyCoordinate.regularColumn - colOffset

        if(centeredCol == 9) {
            return "!text/keyspec_symbols_0,!text/additional_morekeys_symbols_0"
        } else if(centeredCol in 0 until 9) {
            return "!text/keyspec_symbols_${centeredCol + 1},!text/additional_morekeys_symbols_${centeredCol + 1}"
        } else {
            return ""
        }
    }
    return ""
}


private fun symsForCoord(keyCoordinate: KeyCoordinate): String {
    if(!keyCoordinate.element.kind.isAlphabet) return ""

    val row = QwertySymbols.getOrNull(keyCoordinate.regularRow) ?: return ""

    val colOffset = (keyCoordinate.measurement.numColumnsByRow[keyCoordinate.regularRow] - row.size) / 2
    val centeredCol = keyCoordinate.regularColumn - colOffset.coerceAtLeast(0)
    if(centeredCol < 0) return ""

    val letter = row.getOrNull(centeredCol)

    return if(letter != null) {
        "!text/qwertysyms_$letter"
    } else {
        ""
    }
}

private fun actionForCoord(keyCoordinate: KeyCoordinate): String {
    if(!keyCoordinate.element.kind.isAlphabet) return ""

    val row = QwertySymbols.getOrNull(keyCoordinate.regularRow)
    val letter = row?.getOrNull(keyCoordinate.regularColumn)
    return if(letter != null) {
        "!text/actions_$letter"
    } else {
        ""
    }
}

data class BuiltMoreKeys(
    val specs: List<MoreKeySpec>,
    val flags: Int
)

data class MoreKeysBuilder(
    val code: Int,
    val mode: MoreKeyMode,
    val coordinate: KeyCoordinate,
    val row: Row,
    val keyboard: Keyboard,
    val params: KeyboardParams,
    val moreKeys: MoreKeys = listOf()
) {
    private fun insertMoreKeys(resolvedMoreKeys: MoreKeys): MoreKeysBuilder {
        if(resolvedMoreKeys.isEmpty()) return this

        val idxOfMarker = moreKeys.indexOf("%")
        val newMoreKeys = if(idxOfMarker == -1) {
            moreKeys + resolvedMoreKeys
        } else {
            moreKeys.subList(0, idxOfMarker) + resolvedMoreKeys + moreKeys.subList(idxOfMarker, moreKeys.size)
        }

        return this.copy(moreKeys = newMoreKeys)
    }

    @Suppress("HardCodedStringLiteral")
    fun insertMoreKeys(moreKeysToInsert: String): MoreKeysBuilder {
        // Special case: when the period key already has moreKeys, force a different column order
        val forcedColumnOrder = when {
            moreKeysToInsert == "!text/morekeys_period" && moreKeys.isNotEmpty() ->
                "!autoColumnOrder!6"

            else -> null
        }

        val splitSpecs = MoreKeySpec.splitKeySpecs(moreKeysToInsert)?.flatMap {
            var whatToInsert = params.mTextsSet.resolveTextReference(it)
            if(whatToInsert == "," && (it == "!text/keyspec_comma" || it == "!text/keyspec_tablet_comma")) {
                whatToInsert = "\\,"
            }
            MoreKeySpec.splitKeySpecs(whatToInsert)?.toList() ?: emptyList()
        }

        // Apply forced column order
        val resolved = splitSpecs?.map {
            if(forcedColumnOrder != null && (it.startsWith(KeyConsts.MORE_KEYS_AUTO_COLUMN_ORDER) || it.startsWith(KeyConsts.MORE_KEYS_FIXED_COLUMN_ORDER))) {
                forcedColumnOrder
            } else {
                it
            }
        }

        return resolved?.let { insertMoreKeys(it) } ?: this
    }

    private val isNumberRowActive = keyboard.numberRowMode.isActive(params.mId.mNumberRow)

    private fun canAddMoreKey(key: LongPressKey): Boolean =
        when(key) {
            LongPressKey.QuickActions -> mode.autoSymFromCoord && row.isLetterRow

            // Numbers added to top row requires the number row being inactive
            LongPressKey.Numbers -> (mode.autoNumFromCoord && !isNumberRowActive) && row.isLetterRow

            // Symbols for top row requires number row being active (it replaces the number long-press keys)
            LongPressKey.Symbols -> (mode.autoSymFromCoord && (coordinate.regularRow > 0 || isNumberRowActive)) && row.isLetterRow

            // Language keys require a-z code
            LongPressKey.LanguageKeys -> mode.autoFromLanguageKey && code > 0 && (row.isLetterRow || row.isBottomRow)
            LongPressKey.MiscLetters  -> mode.autoFromLanguageKey && code > 0 && row.isLetterRow
        }

    private fun moreKey(key: LongPressKey): String {
        val codeCharOrUnicode = when {
            // Standard alphabet - use the letter itself
            code >= 'a'.code && code <= 'z'.code -> code.toChar()

            code <= 0 -> ""

            // Anything else - unicode
            else -> "code_u" + code.toString(16).padStart(4, '0')
        }

        return when (key) {
            LongPressKey.Numbers -> getNumForCoordinate(coordinate)
            LongPressKey.Symbols -> symsForCoord(coordinate)
            LongPressKey.QuickActions -> actionForCoord(coordinate)
            LongPressKey.LanguageKeys -> "!text/morekeys_$codeCharOrUnicode"
            LongPressKey.MiscLetters -> "!text/morekeys_misc_$codeCharOrUnicode"
        }
    }

    fun insertMoreKeys(key: LongPressKey): MoreKeysBuilder {
        if(!canAddMoreKey(key)) return this
        return insertMoreKeys(moreKey(key))
    }

    fun build(shifted: Boolean): BuiltMoreKeys {
        return BuiltMoreKeys(
            specs = filterMoreKeysFlags(moreKeys).filter { it != "%" }.map {
                MoreKeySpec(it, shifted, params.mId.locale)
            },
            flags = computeMoreKeysFlags(moreKeys.toTypedArray(), params)
        )
    }
}


private fun computeMoreKeysFlags(moreKeys: Array<String>, params: KeyboardParams): Int {
    // Get maximum column order number and set a relevant mode value.
    var moreKeysColumnAndFlags =
        (KeyConsts.MORE_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER
                or params.mMaxMoreKeysKeyboardColumn)
    var value: Int
    if ((MoreKeySpec.getIntValue(
            moreKeys,
            KeyConsts.MORE_KEYS_AUTO_COLUMN_ORDER,
            -1
        ).also {
            value = it
        }) > 0
    ) {
        // Override with fixed column order number and set a relevant mode value.
        moreKeysColumnAndFlags =
            (KeyConsts.MORE_KEYS_MODE_FIXED_COLUMN_WITH_AUTO_ORDER
                    or (value and KeyConsts.MORE_KEYS_COLUMN_NUMBER_MASK))
    }
    if ((MoreKeySpec.getIntValue(
            moreKeys,
            KeyConsts.MORE_KEYS_FIXED_COLUMN_ORDER,
            -1
        ).also {
            value = it
        }) > 0
    ) {
        // Override with fixed column order number and set a relevant mode value.
        moreKeysColumnAndFlags =
            (KeyConsts.MORE_KEYS_MODE_FIXED_COLUMN_WITH_FIXED_ORDER
                    or (value and KeyConsts.MORE_KEYS_COLUMN_NUMBER_MASK))
    }
    if (MoreKeySpec.getBooleanValue(
            moreKeys,
            KeyConsts.MORE_KEYS_HAS_LABELS
        )
    ) {
        moreKeysColumnAndFlags =
            moreKeysColumnAndFlags or KeyConsts.MORE_KEYS_FLAGS_HAS_LABELS
    }
    if (MoreKeySpec.getBooleanValue(
            moreKeys,
            KeyConsts.MORE_KEYS_NEEDS_DIVIDERS
        )
    ) {
        moreKeysColumnAndFlags =
            moreKeysColumnAndFlags or KeyConsts.MORE_KEYS_FLAGS_NEEDS_DIVIDERS
    }
    if (MoreKeySpec.getBooleanValue(
            moreKeys,
            KeyConsts.MORE_KEYS_NO_PANEL_AUTO_MORE_KEY
        )
    ) {
        moreKeysColumnAndFlags =
            moreKeysColumnAndFlags or KeyConsts.MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY
    }
    return moreKeysColumnAndFlags
}

private fun filterMoreKeysFlags(moreKeys: List<String>): List<String> =
    moreKeys.filter {
        !it.startsWith(KeyConsts.MORE_KEYS_AUTO_COLUMN_ORDER) &&
                !it.startsWith(KeyConsts.MORE_KEYS_FIXED_COLUMN_ORDER) &&
                !it.startsWith(KeyConsts.MORE_KEYS_HAS_LABELS) &&
                !it.startsWith(KeyConsts.MORE_KEYS_NEEDS_DIVIDERS) &&
                !it.startsWith(KeyConsts.MORE_KEYS_NO_PANEL_AUTO_MORE_KEY)
    }
