package org.futo.inputmethod.v2keyboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.futo.inputmethod.keyboard.KeyConsts
import org.futo.inputmethod.keyboard.KeyboardId
import org.futo.inputmethod.keyboard.internal.KeySpecParser
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.keyboard.internal.MoreKeySpec
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.StringUtils
import org.futo.inputmethod.latin.settings.LongPressKeySettings

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
     * maximum number of keys in a row. It is consistent across the entire keyboard except in some cases.
     *
     * For example, if a keyboard has 3 rows, with 10, 9, and 7 keys respectively,
     * the regular key width will be 100% / 10 = 10% for the entire keyboard.
     * The rows with 9 and 7 keys will receive padding by default to keep them centered due
     * to the extra space.
     *
     * There are 3 cases where regular width will be inconsistent:
     * 1. In the bottom row
     * 2. In the split layout, to maintain middle alignment
     * 3. In a row with functional keys (shift or delete), in order to maintain a minimum functional key width
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

/**
 * Specifies which morekeys can be automatically added to the key.
 */
@Serializable
enum class MoreKeyMode(
    val autoFromKeyspec: Boolean,
    val autoNumFromCoord: Boolean,
    val autoSymFromCoord: Boolean,
    val autoFromLanguageKey: Boolean,
) {
    /**
     * Automatically insert morekeys from keyspec shortcuts, as well as numbers, symbols and actions
     * (if not disabled by user). These count towards KeyCoordinate.
     */
    All(true, true, true, true),

    /**
     * Only automatically insert morekeys from keyspec shortcut or language-related accents
     */
    OnlyFromLetter(true, false, false, true),

    /**
     * Do not automatically insert any morekeys.
     */
    OnlyExplicit(false, false, false, false),
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
    val followKeyHintLabelRatio: Boolean = false,
    val followKeyLargeLetterRatio: Boolean = false,
    val autoXScale: Boolean = false,
) {
    fun getValue(): Int =
        KeyConsts.LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER.and(alignLabelOffCenter) or
        KeyConsts.LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM.and(alignHintLabelToBottom) or
        KeyConsts.LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM.and(alignIconToBottom) or
        KeyConsts.LABEL_FLAGS_HAS_HINT_LABEL.and(hasHintLabel) or
        KeyConsts.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO.and(followKeyLabelRatio) or
        KeyConsts.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO.and(followKeyLetterRatio) or
        KeyConsts.LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO.and(followKeyHintLabelRatio) or
        KeyConsts.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO.and(followKeyLargeLetterRatio) or
        KeyConsts.LABEL_FLAGS_AUTO_X_SCALE.and(autoXScale)
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

    /**
     * Whether or not the key can be "flicked" to access morekeys instantly, without needing to
     * wait for long press timeout. This does not behave like actual flick keys in terms of allowing
     * flicking in all directions, rather it just triggers the morekey popup. Specifying this will
     * implicitly add the key as the first element in morekeys (may be broken with fixedColumnOrder)
     */
    val fastMoreKeys: Boolean? = null
) {
    fun getEffectiveAttributes(row: Row, keyboard: Keyboard, extraAttrs: List<KeyAttributes> = emptyList()): KeyAttributes {
        val attrs = if(row.isBottomRow) {
            listOf(this) + extraAttrs + listOf(row.attributes, DefaultKeyAttributes)
        } else {
            listOf(this) + extraAttrs + listOf(row.attributes, keyboard.attributes, DefaultKeyAttributes)
        }

        val effectiveWidth = resolve(attrs) { it.width }

        val defaultMoreKeyMode = if((row.isLetterRow || row.isBottomRow) && effectiveWidth == KeyWidth.Regular) {
            MoreKeyMode.All
        } else {
            MoreKeyMode.OnlyFromLetter
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
            fastMoreKeys        = resolve(attrs) { it.fastMoreKeys       }
        )
    }

    operator fun plus(other: KeyAttributes): KeyAttributes {
        val attrs = listOf(this, other)
        return KeyAttributes(
            width               = resolve(attrs) { it.width              },
            style               = resolve(attrs) { it.style              },
            anchored            = resolve(attrs) { it.anchored           },
            showPopup           = resolve(attrs) { it.showPopup          },
            moreKeyMode         = resolve(attrs) { it.moreKeyMode        },
            useKeySpecShortcut  = resolve(attrs) { it.useKeySpecShortcut },
            longPressEnabled    = resolve(attrs) { it.longPressEnabled   },
            labelFlags          = resolve(attrs) { it.labelFlags         },
            repeatableEnabled   = resolve(attrs) { it.repeatableEnabled  },
            shiftable           = resolve(attrs) { it.shiftable          },
            fastMoreKeys        = resolve(attrs) { it.fastMoreKeys       }
        )
    }
}

internal fun<T, O> resolve(attributes: List<O>, getter: (O) -> T?): T? =
    attributes.firstNotNullOfOrNull(getter)


val DefaultKeyAttributes = KeyAttributes(
    width               = KeyWidth.Regular,
    style               = KeyVisualStyle.Normal,
    anchored            = false,
    showPopup           = true,
    moreKeyMode         = null, // Default value is calculated in getEffectiveAttributes based on other attribute values
    useKeySpecShortcut  = true,
    longPressEnabled    = false,
    labelFlags          = LabelFlags(autoXScale = true),
    repeatableEnabled   = false,
    shiftable           = true,
    fastMoreKeys        = false
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
     */
    val hint: String? = null,

    val code: Int? = null
) : AbstractKey {
    override fun countsToKeyCoordinate(params: KeyboardParams, row: Row, keyboard: Keyboard): Boolean {
        val attributes = attributes.getEffectiveAttributes(row, keyboard)
        val moreKeyMode = attributes.moreKeyMode!!

        return moreKeyMode.autoNumFromCoord && moreKeyMode.autoSymFromCoord
    }

    fun computeDataWithExtraAttrs(params: KeyboardParams, row: Row, keyboard: Keyboard, coordinate: KeyCoordinate, extraAttrs: List<KeyAttributes>): ComputedKeyData {
        val attributes = attributes.getEffectiveAttributes(row, keyboard, extraAttrs)
        val shifted = (attributes.shiftable == true) && when(params.mId.mElementId) {
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> true
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> true
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> true
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED -> true
            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> true
            else -> false
        }

        val relevantSpecShortcut = if(attributes.useKeySpecShortcut != false || attributes.moreKeyMode?.autoFromKeyspec != false) {
            resolveSpecWithOptionalShortcut(spec, params.mTextsSet, coordinate)
        } else {
            null
        }

        var expandedSpec: String? = params.mTextsSet.resolveTextReference(
            if(attributes.useKeySpecShortcut != false) { relevantSpecShortcut?.get(0) } else { null }
             ?: spec
        )

        // If the spec is just a number and we just expanded to a shortcut but local numbers is off,
        // just use the spec
        if(spec.toIntOrNull() != null && relevantSpecShortcut != null && params.mId.mUseLocalNumbers == false) {
            expandedSpec = spec
        }

        val label = expandedSpec?.let { KeySpecParser.getLabel(it) } ?: ""
        val icon = expandedSpec?.let { KeySpecParser.getIconId(it) } ?: ""
        val code = code ?: KeySpecParser.getCode(expandedSpec)
        val outputText = KeySpecParser.getOutputText(expandedSpec)

        val moreKeyMode = attributes.moreKeyMode!!
        var moreKeysBuilder = MoreKeysBuilder(code = code, mode = moreKeyMode, coordinate = coordinate, row = row, keyboard = keyboard, params = params)

        // 1. Add layout-defined moreKeys
        moreKeysBuilder =
            moreKeysBuilder.insertMoreKeys(LongPressKeySettings.joinMoreKeys(moreKeys))

        // 2. Add moreKeys from keyspec
        if (moreKeyMode.autoFromKeyspec) {
            moreKeysBuilder =
                moreKeysBuilder.insertMoreKeys(getDefaultMoreKeysForKey(code, relevantSpecShortcut))
        }

        // 3. Add settings-defined moreKeys (numbers, symbols, actions, language, etc) in their order
        params.mId.mLongPressKeySettings.currentOrder.forEach {
            moreKeysBuilder = moreKeysBuilder.insertMoreKeys(it)
        }

        // 4. Add special (period and comma)
        if (moreKeyMode.autoSymFromCoord) {
            moreKeysBuilder =
                moreKeysBuilder.insertMoreKeys(getSpecialFromRow(coordinate, row))
        }

        var moreKeys = moreKeysBuilder.build(shifted)
        if(attributes.fastMoreKeys == true && moreKeys.specs.isNotEmpty()) {
            moreKeys = moreKeys.copy(
                specs = moreKeys.specs.toMutableList().apply {
                    add(0, MoreKeySpec(expandedSpec ?: spec, false, params.mId.locale, false))
                }
            )
        }

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
            moreKeys = moreKeys.specs,
            longPressEnabled = (attributes.longPressEnabled ?: false) || moreKeys.specs.isNotEmpty(),
            repeatable = attributes.repeatableEnabled ?: false,
            moreKeyFlags = moreKeys.flags,
            countsToKeyCoordinate = moreKeyMode.autoNumFromCoord && moreKeyMode.autoSymFromCoord,
            hint = hint ?: "",
            labelFlags = attributes.labelFlags?.getValue() ?: 0,
            fastLongPress = attributes.fastMoreKeys == true
        )
    }

    override fun computeData(params: KeyboardParams, row: Row, keyboard: Keyboard, coordinate: KeyCoordinate): ComputedKeyData
        = computeDataWithExtraAttrs(params, row, keyboard, coordinate, emptyList())
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
     * Key to use when shifted, excluding automatic shift
     */
    val shiftedManually: Key = shifted,

    /**
     * Key to use when shift locked (caps lock), defaults to [shiftedManually]
     */
    val shiftLocked: Key = shiftedManually,

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
    private fun selectKeyFromElement(elementId: Int): Key =
        when(elementId) {
            KeyboardId.ELEMENT_ALPHABET -> normal

            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> shifted
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> shiftedManually

            // KeyboardState.kt currently doesn't distinguish between these
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED,
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> shiftLocked

            KeyboardId.ELEMENT_SYMBOLS -> symbols
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> symbolsShifted
            else -> normal
        }

    override fun countsToKeyCoordinate(params: KeyboardParams, row: Row, keyboard: Keyboard): Boolean =
        selectKeyFromElement(params.mId.mElementId).countsToKeyCoordinate(params, row, keyboard)

    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? =
        selectKeyFromElement(params.mId.mElementId).computeData(params, row, keyboard, coordinate)
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
    Spacebar,

    /**
     * Visual style for moreKeys
     */
    MoreKey,
}

/**
 * An empty gap in place of a key
 */
@Serializable
@SerialName("gap")
class GapKey(val attributes: KeyAttributes = KeyAttributes()) : AbstractKey {
    override fun countsToKeyCoordinate(params: KeyboardParams, row: Row, keyboard: Keyboard): Boolean = false

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