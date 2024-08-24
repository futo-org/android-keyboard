package org.futo.inputmethod.v2keyboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.futo.inputmethod.keyboard.KeyboardId
import org.futo.inputmethod.keyboard.internal.KeySpecParser
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.keyboard.internal.MoreKeySpec
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.StringUtils

typealias KeyJ = org.futo.inputmethod.keyboard.Key

/**
 * Width tokens for keys. Rather than explicitly specifying a width in percentage as is common in
 * other layout systems, we instead use width tokens, which eliminates the need to explicitly
 * calculate and specify width percentages for most cases.
 */
@Serializable
enum class KeyWidth {
    /**
     * Regular key width. Used for normal letters (QWERTY etc)
     *
     * ##### Width calculation
     * Simply put, the width of this is calculated by dividing the total keyboard width by the
     * maximum number of keys in a row. It is consistent across the entire keyboard.
     *
     * For example, if a keyboard has 3 rows, with 10, 9, and 7 keys respectively,
     * the regular key width will be 100% / 10 = 10% for the entire keyboard.
     * The rows with 9 and 7 keys will receive padding by default to keep them centered due
     * to the extra space.
     */
    Regular,

    /**
     * Functional key width. Used for functional keys (Shift, Backspace, Enter, Symbols, etc)
     *
     * ##### Width calculation
     * The width of this is at least the value specified in [Keyboard.minimumFunctionalKeyWidth]
     * or, for the bottom row, [Keyboard.minimumBottomRowFunctionalKeyWidth].
     *
     * The width may be larger than the minimum if the available space is there.
     * For example on the QWERTY layout, the ZXCV row has only 7 keys at a width of 10%, using up
     * only 70% of space. The remaining 30% of space is divided among the shift and backspace,
     * meaning the functional width is 15%.
     */
    FunctionalKey,

    /**
     * Grow width. Takes up all remaining space divided evenly among all grow keys in the row.
     * Mainly used for spacebar.
     *
     * Grow keys are not supported in split layouts, and their presence can complicate width
     * calculation for functional keys and others. Avoid use when possible.
     */
    Grow,

    /**
     * The Custom1 width as defined in [Keyboard.overrideWidths] (values are between 0.0 and 1.0)
     */
    Custom1,

    /**
     * The Custom2 width as defined in [Keyboard.overrideWidths] (values are between 0.0 and 1.0)
     */
    Custom2,

    /**
     * The Custom3 width as defined in [Keyboard.overrideWidths] (values are between 0.0 and 1.0)
     */
    Custom3,

    /**
     * The Custom4 width as defined in [Keyboard.overrideWidths] (values are between 0.0 and 1.0)
     */
    Custom4,
}

internal fun computeMoreKeysFlags(moreKeys: Array<String>, params: KeyboardParams): Int {
    // Get maximum column order number and set a relevant mode value.
    var moreKeysColumnAndFlags =
        (KeyJ.MORE_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER
                or params.mMaxMoreKeysKeyboardColumn)
    var value: Int
    if ((MoreKeySpec.getIntValue(
            moreKeys,
            KeyJ.MORE_KEYS_AUTO_COLUMN_ORDER,
            -1
        ).also {
            value = it
        }) > 0
    ) {
        // Override with fixed column order number and set a relevant mode value.
        moreKeysColumnAndFlags =
            (KeyJ.MORE_KEYS_MODE_FIXED_COLUMN_WITH_AUTO_ORDER
                    or (value and KeyJ.MORE_KEYS_COLUMN_NUMBER_MASK))
    }
    if ((MoreKeySpec.getIntValue(
            moreKeys,
            KeyJ.MORE_KEYS_FIXED_COLUMN_ORDER,
            -1
        ).also {
            value = it
        }) > 0
    ) {
        // Override with fixed column order number and set a relevant mode value.
        moreKeysColumnAndFlags =
            (KeyJ.MORE_KEYS_MODE_FIXED_COLUMN_WITH_FIXED_ORDER
                    or (value and KeyJ.MORE_KEYS_COLUMN_NUMBER_MASK))
    }
    if (MoreKeySpec.getBooleanValue(
            moreKeys,
            KeyJ.MORE_KEYS_HAS_LABELS
        )
    ) {
        moreKeysColumnAndFlags =
            moreKeysColumnAndFlags or KeyJ.MORE_KEYS_FLAGS_HAS_LABELS
    }
    if (MoreKeySpec.getBooleanValue(
            moreKeys,
            KeyJ.MORE_KEYS_NEEDS_DIVIDERS
        )
    ) {
        moreKeysColumnAndFlags =
            moreKeysColumnAndFlags or KeyJ.MORE_KEYS_FLAGS_NEEDS_DIVIDERS
    }
    if (MoreKeySpec.getBooleanValue(
            moreKeys,
            KeyJ.MORE_KEYS_NO_PANEL_AUTO_MORE_KEY
        )
    ) {
        moreKeysColumnAndFlags =
            moreKeysColumnAndFlags or KeyJ.MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY
    }
    return moreKeysColumnAndFlags
}

internal fun filterMoreKeysFlags(moreKeys: List<String>): List<String> =
    moreKeys.filter {
        !it.startsWith(KeyJ.MORE_KEYS_AUTO_COLUMN_ORDER) &&
                !it.startsWith(KeyJ.MORE_KEYS_FIXED_COLUMN_ORDER) &&
                !it.startsWith(KeyJ.MORE_KEYS_HAS_LABELS) &&
                !it.startsWith(KeyJ.MORE_KEYS_NEEDS_DIVIDERS) &&
                !it.startsWith(KeyJ.MORE_KEYS_NO_PANEL_AUTO_MORE_KEY)
    }

/**
 * Specifies which morekeys can be automatically added to the key.
 */
@Serializable
enum class MoreKeyMode(
    val autoFromKeyspec: Boolean,
    val autoNumFromCoord: Boolean,
    val autoSymFromCoord: Boolean
) {
    /**
     * Automatically insert morekeys from keyspec shortcuts, as well as numbers, symbols and actions
     * (if not disabled by user). These count towards KeyCoordinate.
     */
    All(true, true, true),

    /**
     * Only automatically insert morekeys from keyspec shortcut.
     */
    OnlyFromKeyspec(true, false, false),

    /**
     * Do not automatically insert any morekeys.
     */
    OnlyExplicit(false, false, false)
}

private fun Int.and(other: Boolean): Int {
    return if(other) { this } else { 0 }
}

/**
 * Flags for the key label
 */
@Serializable
data class LabelFlags(
    val alignHintLabelToBottom: Boolean = false,
    val alignIconToBottom: Boolean = false,
    val alignLabelOffCenter: Boolean = false,
    val hasHintLabel: Boolean = false,
    val followKeyLabelRatio: Boolean = false,
    val followKeyLetterRatio: Boolean = false,
    val followKeyLargeLetterRatio: Boolean = false,
    val autoXScale: Boolean = false,
) {
    fun getValue(): Int =
        KeyJ.LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER.and(alignLabelOffCenter) or
        KeyJ.LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM.and(alignHintLabelToBottom) or
        KeyJ.LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM.and(alignIconToBottom) or
        KeyJ.LABEL_FLAGS_HAS_HINT_LABEL.and(hasHintLabel) or
        KeyJ.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO.and(followKeyLabelRatio) or
        KeyJ.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO.and(followKeyLetterRatio) or
        KeyJ.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO.and(followKeyLargeLetterRatio) or
        KeyJ.LABEL_FLAGS_AUTO_X_SCALE.and(autoXScale)
}

/**
 * Attributes for keys.
 *
 * Values are inherited in the following order:
 * `Key.attributes > Row.attributes > Keyboard.attributes > DefaultKeyAttributes`
 */
@Serializable
data class KeyAttributes(
    /**
     * Key width token
     */
    val width: KeyWidth? = null,

    /**
     * Visual style (background) for the key
     */
    val style: KeyVisualStyle? = null,

    /**
     * Whether or not to anchor the key to the edges.
     *
     * When a row is not wide enough to fill 100%, padding is added to the edges of the row.
     * If there are anchored keys in the row, the padding will be added after the anchored
     * keys, keeping the anchored keys at the edge.
     */
    val anchored: Boolean? = null,

    /**
     * Whether or not to show the popup indicator when the key is pressed.
     * This is usually desirable for letters on normal layouts (QWERTY letters), but undesirable
     * for functional keys (Shift, Backspace), or certain layouts (phone layout)
     */
    val showPopup: Boolean? = null,

    /**
     * Which moreKeys to add automatically
     */
    val moreKeyMode: MoreKeyMode? = null,

    /**
     * Whether or not to use keyspec shortcuts.
     * For example, `$` gets automatically converted to `!text/keyspec_currency`.
     *
     * The full list of keyspec shortcuts is defined in `KeySpecShortcuts`.
     */
    val useKeySpecShortcut: Boolean? = null,

    /**
     * Whether or not longpress is enabled for the key
     */
    val longPressEnabled: Boolean? = null,

    /**
     * Label flags for how the key's label (and its hint) should be presented
     */
    val labelFlags: LabelFlags? = null,

    /**
     * Whether or not the key is repeatable, intended for backspace
     */
    val repeatableEnabled: Boolean? = null,

    /**
     * Whether or not the key is automatically shiftable. If true, it automatically becomes
     * uppercased when the layout is shifted. If this is not desired, this can be set to false.
     * Shift behavior can be customized by using a [CaseSelector].
     */
    val shiftable: Boolean? = null,
) {
    fun getEffectiveAttributes(row: Row, keyboard: Keyboard): KeyAttributes {
        val attrs = listOf(this, row.attributes, keyboard.attributes, DefaultKeyAttributes)

        val effectiveWidth = resolve(attrs) { it.width }

        val defaultMoreKeyMode = if(row.isLetterRow && effectiveWidth == KeyWidth.Regular) {
            MoreKeyMode.All
        } else {
            MoreKeyMode.OnlyFromKeyspec
        }

        return KeyAttributes(
            width               = resolve(attrs) { it.width              },
            style               = resolve(attrs) { it.style              },
            anchored            = resolve(attrs) { it.anchored           },
            showPopup           = resolve(attrs) { it.showPopup          },
            moreKeyMode         = resolve(attrs) { it.moreKeyMode        } ?: defaultMoreKeyMode,
            useKeySpecShortcut  = resolve(attrs) { it.useKeySpecShortcut },
            longPressEnabled    = resolve(attrs) { it.longPressEnabled   },
            labelFlags          = resolve(attrs) { it.labelFlags         },
            repeatableEnabled   = resolve(attrs) { it.repeatableEnabled  },
            shiftable           = resolve(attrs) { it.shiftable          },
        )
    }
}

private fun<T, O> resolve(attributes: List<O>, getter: (O) -> T?): T? =
    attributes.firstNotNullOfOrNull(getter)


val DefaultKeyAttributes = KeyAttributes(
    width               = KeyWidth.Regular,
    style               = KeyVisualStyle.Normal,
    anchored            = false,
    showPopup           = true,
    moreKeyMode         = null, // Default value is calculated in getEffectiveAttributes based on other attribute values
    useKeySpecShortcut  = true,
    longPressEnabled    = false,
    labelFlags          = LabelFlags(),
    repeatableEnabled   = false,
    shiftable           = true,
)


object MoreKeysListSerializer: SpacedListSerializer<String>(String.serializer(), {
    MoreKeySpec.splitKeySpecs(it)?.toList() ?: listOf()
})

/**
 * The base key
 */
@Serializable
@SerialName("base")
data class BaseKey(
    /**
     * AOSP key spec. It can contain a custom label, code, icon, output text.
     *
     * Each key specification is one of the following:
     * - Label optionally followed by keyOutputText (keyLabel|keyOutputText).
     * - Label optionally followed by code point (keyLabel|!code/code_name).
     * - Icon followed by keyOutputText (!icon/icon_name|keyOutputText).
     * - Icon followed by code point (!icon/icon_name|!code/code_name).
     *
     * Label and keyOutputText are one of the following:
     * - Literal string.
     * - Label reference represented by (!text/label_name), see {@link KeyboardTextsSet}.
     * - String resource reference represented by (!text/resource_name), see {@link KeyboardTextsSet}.
     *
     * Icon is represented by (!icon/icon_name), see {@link KeyboardIconsSet}.
     *
     * Code is one of the following:
     * - Code point presented by hexadecimal string prefixed with "0x"
     * - Code reference represented by (!code/code_name), see {@link KeyboardCodesSet}.
     *
     * Special character, comma ',' backslash '\', and bar '|' can be escaped by '\' character.
     * Note that the '\' is also parsed by XML parser and {@link MoreKeySpec#splitKeySpecs(String)}
     * as well.
     */
    val spec: String,

    /**
     * Attributes for this key. Values defined here supersede any other values. Values which are
     * not defined are inherited from the row, keyboard, or default attributes.
     */
    val attributes: KeyAttributes = KeyAttributes(),

    /**
     * More keys for this key. In YAML, it can be defined as a list or a comma-separated string.
     *
     * The values here are key specs.
     */
    val moreKeys: @Serializable(with = MoreKeysListSerializer::class) List<String> = listOf(),

    /**
     * If set, overrides a default hint from the value of moreKeys.
     *
     * TODO: Currently does not override
     */
    val hint: String? = null,
) : AbstractKey {
    override fun computeData(params: KeyboardParams, row: Row, keyboard: Keyboard, coordinate: KeyCoordinate): ComputedKeyData {
        val attributes = attributes.getEffectiveAttributes(row, keyboard)
        val shifted = (attributes.shiftable == true) && when(params.mId.mElementId) {
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> true
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> true
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> true
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED -> true
            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> true
            else -> false
        }

        val relevantSpecShortcut = if(attributes.useKeySpecShortcut != false || attributes.moreKeyMode?.autoFromKeyspec != false) {
            KeySpecShortcuts[spec]
        } else {
            null
        }

        val expandedSpec: String? = params.mTextsSet.resolveTextReference(
            if(attributes.useKeySpecShortcut != false) { relevantSpecShortcut?.get(0) } else { null }
             ?: spec
        )

        val label = expandedSpec?.let { KeySpecParser.getLabel(it) } ?: ""
        val icon = expandedSpec?.let { KeySpecParser.getIconId(it) } ?: ""
        val code = KeySpecParser.getCode(expandedSpec)
        val outputText = KeySpecParser.getOutputText(expandedSpec)

        val moreKeyMode = attributes.moreKeyMode!!
        
        val autoMoreKeys = listOfNotNull(
            if (moreKeyMode.autoFromKeyspec) {
                getDefaultMoreKeysForKey(code, relevantSpecShortcut)
            } else { null },

            if (moreKeyMode.autoNumFromCoord) {
                getNumForCoordinate(coordinate)
            } else { null },

            if (moreKeyMode.autoSymFromCoord) {
                getSymsForCoordinate(coordinate)
            } else { null }
        ).joinToString(",")

        val joinedMoreKeys = params.mId.mLongPressKeySettings.joinMoreKeys(moreKeys)

        val moreKeys = params.mId.mLongPressKeySettings.reorderMoreKeys("$joinedMoreKeys,$autoMoreKeys").let {
            params.mTextsSet.resolveTextReference(it)
        }.let {
            MoreKeySpec.splitKeySpecs(it)?.toList() ?: listOf()
        }

        val moreKeySpecs = filterMoreKeysFlags(moreKeys).map {
            MoreKeySpec(it, shifted, params.mId.locale)
        }

        val moreKeyFlags = computeMoreKeysFlags(moreKeys.toTypedArray(), params)

        return ComputedKeyData(
            label = if(shifted) {
                StringUtils.toTitleCaseOfKeyLabel(label, params.mId.locale) ?: label
            } else {
                label
            },
            code = if(shifted) {
                StringUtils.toTitleCaseOfKeyCode(code, params.mId.locale)
            } else {
                code
            },
            outputText = outputText,
            width = attributes.width!!,
            icon = icon,
            style = attributes.style!!,
            anchored = attributes.anchored!!,
            showPopup = attributes.showPopup!!,
            moreKeys = moreKeySpecs,
            longPressEnabled = (attributes.longPressEnabled ?: false) || moreKeys.isNotEmpty(),
            repeatable = attributes.repeatableEnabled ?: false,
            moreKeyFlags = moreKeyFlags,
            countsToKeyCoordinate = moreKeyMode.autoNumFromCoord && moreKeyMode.autoSymFromCoord,
            hint = hint ?: "",
            labelFlags = attributes.labelFlags?.getValue() ?: 0
        )
    }
}

/**
 * Case selector key. Allows specifying a different type of key depending on when the layout is
 * shifted or not.
 */
@Serializable
@SerialName("case")
data class CaseSelector(
    /**
     * Key to use normally
     */
    val normal: Key,

    /**
     * Key to use when shifted
     */
    val shifted: Key = normal,

    /**
     * Key to use when shift locked (caps lock), defaults to [shifted]
     */
    val shiftLocked: Key = shifted,

    /**
     * Key to use when in symbols layout, defaults to [normal]. Mainly used internally for
     * [TemplateShiftKey]
     */
    val symbols: Key = normal,

    /**
     * Key to use when in symbols layout, defaults to [normal]. Mainly used internally for
     * [TemplateShiftKey]
     */
    val symbolsShifted: Key = normal
) : AbstractKey {
    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? =
        when(params.mId.mElementId) {
            KeyboardId.ELEMENT_ALPHABET -> normal

            // KeyboardState.kt currently doesn't distinguish between these
            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED,
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> shifted

            // KeyboardState.kt currently doesn't distinguish between these
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED,
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> shiftLocked

            KeyboardId.ELEMENT_SYMBOLS -> symbols
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> symbolsShifted
            else -> normal
        }.computeData(params, row, keyboard, coordinate)
}

typealias Key = @Serializable(with = KeyPathSerializer::class) AbstractKey

object KeyPathSerializer : PathDependentModifier<AbstractKey>(
    KeyContextualSerializer, { path, v ->
        if(v is BaseKey) {
            v
        } else {
            v
        }
    }
)
object KeyContextualSerializer : ClassOrScalarsSerializer<AbstractKey>(
    AbstractKey.serializer(),
    { contents ->
        assert(contents.isNotEmpty()) { "Key requires at least 1 element" }
        if(contents[0].startsWith("$") && contents[0] != "$" && contents.size == 1) {
            TemplateKeys[contents[0].trimStart('$')]
                ?: throw IllegalStateException("Unknown template key $contents")
        } else {
            if(contents.size == 1) {
                BaseKey(contents[0])
            } else {
                val moreKeys = contents.subList(1, contents.size)

                BaseKey(contents[0], moreKeys = moreKeys)
            }
        }
    }
)


/**
 * Affects the background for the key. Depending on the user theme settings, backgrounds may be
 * different.
 */
@Serializable
enum class KeyVisualStyle {
    /**
     * Uses a normal key background, intended for all letters.
     */
    Normal,

    /**
     * Uses no key background, intended for number row numbers.
     */
    NoBackground,

    /**
     * Uses a slightly darker colored background, intended for functional keys (backspace, etc)
     */
    Functional,

    /**
     * Intended for Shift when it's not shiftlocked
     */
    StickyOff,

    /**
     * Intended for Shift to indicate it's shiftlocked. Uses a more bright background
     */
    StickyOn,

    /**
     * Uses a bright fully rounded background, normally used for the enter key
     */
    Action,

    /**
     * Depending on the key borders setting, this is either
     * the same as [Normal] (key borders enabled) or a
     * fully rounded rectangle (key borders disabled)
     */
    Spacebar
}

fun KeyVisualStyle.toBackgroundTypeInt(): Int = when(this) {
    KeyVisualStyle.Normal -> KeyJ.BACKGROUND_TYPE_NORMAL
    KeyVisualStyle.NoBackground -> KeyJ.BACKGROUND_TYPE_EMPTY
    KeyVisualStyle.Functional -> KeyJ.BACKGROUND_TYPE_FUNCTIONAL
    KeyVisualStyle.StickyOff -> KeyJ.BACKGROUND_TYPE_STICKY_OFF
    KeyVisualStyle.StickyOn -> KeyJ.BACKGROUND_TYPE_STICKY_ON
    KeyVisualStyle.Action -> KeyJ.BACKGROUND_TYPE_ACTION
    KeyVisualStyle.Spacebar -> KeyJ.BACKGROUND_TYPE_SPACEBAR
}


/**
 * An empty gap in place of a key
 */
@Serializable
@SerialName("gap")
class GapKey(val attributes: KeyAttributes = KeyAttributes()) : AbstractKey {
    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData {
        val attributes = attributes.getEffectiveAttributes(row, keyboard)

        val moreKeyMode = attributes.moreKeyMode!!

        return ComputedKeyData(
            label = "",
            code = Constants.CODE_UNSPECIFIED,
            outputText = null,
            width = attributes.width!!,
            icon = "",
            style = KeyVisualStyle.NoBackground,
            anchored = attributes.anchored!!,
            showPopup = false,
            moreKeys = listOf(),
            longPressEnabled = false,
            repeatable = false,
            moreKeyFlags = 0,
            countsToKeyCoordinate = moreKeyMode.autoNumFromCoord && moreKeyMode.autoSymFromCoord,
            hint = "",
            labelFlags = 0
        )
    }
}