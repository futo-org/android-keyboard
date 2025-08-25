package org.futo.inputmethod.v2keyboard

import android.view.inputmethod.EditorInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.futo.inputmethod.keyboard.KeyboardId
import org.futo.inputmethod.keyboard.internal.KeyboardIconsSet
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutKind
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.actions.AllActionKeys
import org.futo.inputmethod.latin.utils.InputTypeUtils

val FunctionalAttributes = KeyAttributes(
    width = KeyWidth.FunctionalKey,
    style = KeyVisualStyle.Functional,
    anchored = true,
    showPopup = false,
    moreKeyMode = MoreKeyMode.OnlyExplicit,
    labelFlags = LabelFlags(followKeyLetterRatio = false, followKeyLargeLetterRatio = false, followKeyLabelRatio = false)
)

private val ShiftMoreKeys = listOf("!noPanelAutoMoreKey!", " |!code/key_capslock")

val TemplateShiftKey = CaseSelector(
    normal = BaseKey(
        spec = "!icon/shift_key|!code/key_shift",
        moreKeys = ShiftMoreKeys,
        attributes = FunctionalAttributes
    ),

    shifted = BaseKey(
        spec = "!icon/shift_key_shifted|!code/key_shift",
        moreKeys = ShiftMoreKeys,
        attributes = FunctionalAttributes
    ),

    shiftLocked = BaseKey(
        spec = "!icon/shift_key_shifted|!code/key_shift",
        moreKeys = ShiftMoreKeys,
        attributes = FunctionalAttributes.copy(style = KeyVisualStyle.StickyOn)
    ),

    symbols = BaseKey(
        spec = "!text/keylabel_to_more_symbol|!code/key_shift",
        attributes = FunctionalAttributes
    ),

    symbolsShifted = BaseKey(
        spec = "!text/keylabel_to_symbol|!code/key_shift",
        attributes = FunctionalAttributes
    )
)

val TemplateDeleteKey = BaseKey(
    spec = "!icon/delete_key|!code/key_delete",
    attributes = FunctionalAttributes.copy(repeatableEnabled = true)
)

val TemplateSymbolsKey = BaseKey(
    spec = "!text/keylabel_to_symbol|!code/key_switch_alpha_symbol",
    attributes = FunctionalAttributes
)

val TemplateAlphabetKey = BaseKey(
    spec = "!text/keylabel_to_alpha|!code/key_switch_alpha_symbol",
    attributes = FunctionalAttributes
)

val TemplateNumberKey = BaseKey(
    spec = "!icon/numpad|!code/key_to_number_layout",
    attributes = KeyAttributes(
        showPopup = false
    )
)

val TemplateSpaceKey = BaseKey(
    spec = "!icon/space_key|!code/key_space",
    attributes = KeyAttributes(
        width = KeyWidth.Grow,
        style = KeyVisualStyle.Spacebar,
        showPopup = false,
        longPressEnabled = true,
        moreKeyMode = MoreKeyMode.OnlyExplicit
    )
)

val TemplateAlt0Key = BaseKey(
    spec = "0|!code/key_to_alt_0_layout",
    attributes = FunctionalAttributes
)

val TemplateAlt1Key = BaseKey(
    spec = "1|!code/key_to_alt_1_layout",
    attributes = FunctionalAttributes
)

val TemplateAlt2Key = BaseKey(
    spec = "2|!code/key_to_alt_2_layout",
    attributes = FunctionalAttributes
)

@Serializable
@SerialName("enter")
data class EnterKey(
    val attributes: KeyAttributes = KeyAttributes(width = KeyWidth.FunctionalKey)
) : AbstractKey {
    override fun countsToKeyCoordinate(params: KeyboardParams, row: Row, keyboard: Keyboard): Boolean = false
    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData {
        val attributes = attributes.getEffectiveAttributes(row, keyboard)

        val isShifted = params.mId.mElement.kind == KeyboardLayoutKind.Symbols
        val hasOptionToMultiLine =
            params.mId.isMultiLine && params.mId.imeAction() != EditorInfo.IME_ACTION_NONE
                    && !params.mId.passwordInput()
        val useShiftEnter = isShifted && hasOptionToMultiLine

        // Icon, etc depend on editorInfo.
        val icon = if (useShiftEnter)
            KeyboardIconsSet.NAME_ENTER_KEY
        else when (params.mId.imeAction()) {
            EditorInfo.IME_ACTION_UNSPECIFIED -> KeyboardIconsSet.NAME_ENTER_KEY
            EditorInfo.IME_ACTION_NONE -> KeyboardIconsSet.NAME_ENTER_KEY
            EditorInfo.IME_ACTION_GO -> KeyboardIconsSet.NAME_GO_KEY
            EditorInfo.IME_ACTION_SEARCH -> KeyboardIconsSet.NAME_SEARCH_KEY
            EditorInfo.IME_ACTION_SEND -> KeyboardIconsSet.NAME_SEND_KEY
            EditorInfo.IME_ACTION_NEXT -> KeyboardIconsSet.NAME_NEXT_KEY
            EditorInfo.IME_ACTION_DONE -> KeyboardIconsSet.NAME_DONE_KEY
            EditorInfo.IME_ACTION_PREVIOUS -> KeyboardIconsSet.NAME_PREVIOUS_KEY
            InputTypeUtils.IME_ACTION_CUSTOM_LABEL -> KeyboardIconsSet.NAME_ENTER_KEY

            else -> KeyboardIconsSet.NAME_ENTER_KEY
        }

        val code = if (useShiftEnter)
            Constants.CODE_SHIFT_ENTER
        else
            Constants.CODE_ENTER

        val moreKeys = when {
            // When IME action is overriding normal enter, show the option for shift+enter
            hasOptionToMultiLine && !useShiftEnter ->
                "!text/keyspec_emoji_action_key_shift_enter"

            // Show navigation next/previous if necessary
            params.mId.navigateNext() || params.mId.navigatePrevious() ->
                "!text/keyspec_emoji_action_key_navigation"

            // Standard enter key actions
            else ->
                "!text/keyspec_emoji_action_key"
        }.let {
            MoreKeysBuilder(
                code = code,
                mode = attributes.moreKeyMode!!,
                coordinate = coordinate,
                row = row,
                keyboard = keyboard,
                params = params
            ).insertMoreKeys(it).build(false)
        }

        return ComputedKeyData(
            label                 = "",
            code                  = code,
            outputText            = null,
            width                 = attributes.width ?: KeyWidth.FunctionalKey,
            icon                  = icon,
            style                 = KeyVisualStyle.Action,
            anchored              = true,
            showPopup             = false,
            moreKeys              = moreKeys.specs,
            longPressEnabled      = true,
            repeatable            = false,
            moreKeyFlags          = moreKeys.flags,
            countsToKeyCoordinate = false,
            hint                  = " ",
            labelFlags            = 0
        )
    }
}

@Serializable
@SerialName("action")
data class ActionKey(
    val attributes: KeyAttributes = KeyAttributes()
) : AbstractKey {
    override fun countsToKeyCoordinate(params: KeyboardParams, row: Row, keyboard: Keyboard): Boolean = false
    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? {
        if(!params.mId.mBottomEmojiKeyEnabled) {
            return null
        }

        val attributes = attributes.getEffectiveAttributes(row, keyboard)

        val actionId = params.mId.mBottomActionKeyId
        val actionName = AllActionKeys[actionId]

        return ComputedKeyData(
            label                 = "",
            code                  = Constants.CODE_ACTION_0 + actionId,
            outputText            = null,
            width                 = attributes.width ?: KeyWidth.Regular,
            icon                  = "action_$actionName",
            style                 = attributes.style ?: KeyVisualStyle.Functional,
            anchored              = true,
            showPopup             = false,
            moreKeys              = listOf(),
            longPressEnabled      = true,
            repeatable            = false,
            moreKeyFlags          = 0,
            countsToKeyCoordinate = false,
            hint                  = "",
            labelFlags            = 0
        )
    }
}

@Serializable
@SerialName("contextual")
data class ContextualKey(
    val attributes: KeyAttributes = KeyAttributes(),
    val fallbackKey: Key? = null
) : AbstractKey {
    val keys = mapOf(
        KeyboardId.MODE_EMAIL    to BaseKey(spec = "@", attributes = attributes),
        KeyboardId.MODE_URL      to BaseKey(spec = "/", attributes = attributes),
        KeyboardId.MODE_DATETIME to BaseKey(spec = "/", moreKeys = listOf(":"), hint = ":", attributes = attributes),
        KeyboardId.MODE_DATE     to BaseKey(spec = "/", attributes = attributes),
        KeyboardId.MODE_TIME     to BaseKey(spec = ":", attributes = attributes),
    )

    private fun selectKey(params: KeyboardParams, keyboard: Keyboard): Key? {
        val key = keys[params.mId.mMode] ?: fallbackKey

        return key
    }

    override fun countsToKeyCoordinate(params: KeyboardParams, row: Row, keyboard: Keyboard): Boolean {
        return selectKey(params, keyboard)?.countsToKeyCoordinate(params, row, keyboard) ?: false
    }

    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? {
        return selectKey(params, keyboard)?.computeData(params, row, keyboard, coordinate)
    }
}

@Serializable
@SerialName("optionalzwnj")
data class OptionalZWNJKey(
    val attributes: KeyAttributes = KeyAttributes(),
    val fallbackKey: Key? = null
) : AbstractKey {
    private fun selectKey(params: KeyboardParams, keyboard: Keyboard): Key? {
        if(keyboard.useZWNJKey) return TemplateZWNJKey

        return fallbackKey
    }

    override fun countsToKeyCoordinate(params: KeyboardParams, row: Row, keyboard: Keyboard): Boolean {
        return selectKey(params, keyboard)?.countsToKeyCoordinate(params, row, keyboard) ?: false
    }

    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? {
        return selectKey(params, keyboard)?.computeData(params, row, keyboard, coordinate)
    }
}

val TemplateEnterKey = EnterKey()
val TemplateActionKey = ActionKey()
val TemplateContextualKey = ContextualKey()
val TemplateGapKey = GapKey()
val TemplateZWNJKey = BaseKey(
    spec = "!icon/zwnj_key|\u200C",
    moreKeys = listOf("!icon/zwj_key|\u200D"),
    attributes = KeyAttributes(
        showPopup = false,
        moreKeyMode = MoreKeyMode.OnlyExplicit
    )
)
val TemplateOptionalZWNJKey = OptionalZWNJKey()

data class TemplateCurrencyKey(val currency: String) : AbstractKey {
    override fun countsToKeyCoordinate(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard
    ): Boolean = true

    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? {
        val localeSymbol = params.mTextsSet.getText("keyspec_currency")
        val symbol = when {
            localeSymbol != currency -> currency
            else -> "$"
        }

        return BaseKey(
            spec = symbol,
            attributes = KeyAttributes(useKeySpecShortcut = false)
        ).computeData(params, row, keyboard, coordinate)
    }
}

data class PeriodKey(
    val default: AbstractKey = BaseKey("."),
    val alternative: AbstractKey = BaseKey(".",
        moreKeys = listOf(
            "!text/keyspec_symbols_question",
            "!text/keyspec_comma",
            "!"),
        attributes = KeyAttributes(moreKeyMode = MoreKeyMode.OnlyExplicit, fastMoreKeys = true)
    )
) : AbstractKey {
    override fun countsToKeyCoordinate(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard
    ): Boolean = default.countsToKeyCoordinate(params, row, keyboard)

    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? = when(params.mId.mAlternativePeriodKey) {
        false -> default
        true -> alternative
    }.computeData(params, row, keyboard, coordinate)
}

val TemplatePeriodKey = PeriodKey()

val TemplateKeys = mapOf(
    "shift" to TemplateShiftKey,
    "delete" to TemplateDeleteKey,
    "space" to TemplateSpaceKey,
    "enter" to TemplateEnterKey,
    "symbols" to TemplateSymbolsKey,
    "alphabet" to TemplateAlphabetKey,
    "action" to TemplateActionKey,
    "number" to TemplateNumberKey,
    "contextual" to TemplateContextualKey,
    "zwnj" to TemplateZWNJKey,
    "optionalzwnj" to TemplateOptionalZWNJKey,
    "gap" to TemplateGapKey,
    "alt0" to TemplateAlt0Key,
    "alt1" to TemplateAlt1Key,
    "alt2" to TemplateAlt2Key,
    "period" to TemplatePeriodKey,

    "currency1" to TemplateCurrencyKey("£"),
    "currency2" to TemplateCurrencyKey("¢"),
    "currency3" to TemplateCurrencyKey("€"),
    "currency4" to TemplateCurrencyKey("¥"),
)
