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

@Serializable
enum class KeyWidth {
    Regular,
    FunctionalKey,
    Grow,
    Custom1,
    Custom2,
    Custom3,
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

@Serializable
enum class MoreKeyMode(
    val autoFromKeyspec: Boolean,
    val autoNumFromCoord: Boolean,
    val autoSymFromCoord: Boolean
) {
    All(true, true, true),
    OnlyFromKeyspec(true, false, false),
    OnlyExplicit(false, false, false)
}

private fun Int.and(other: Boolean): Int {
    return if(other) { this } else { 0 }
}


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

@Serializable
data class KeyAttributes(
    val width: KeyWidth? = null,
    val style: KeyVisualStyle? = null,
    val anchored: Boolean? = null,
    val showPopup: Boolean? = null,
    val moreKeyMode: MoreKeyMode? = null,
    val useKeySpecShortcut: Boolean? = null,
    val longPressEnabled: Boolean? = null,
    val labelFlags: LabelFlags? = null,
    val repeatableEnabled: Boolean? = null,
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

@Serializable
@SerialName("base")
data class BaseKey(
    // AOSP Keyboard key spec
    val spec: String,

    // Attributes
    val attributes: KeyAttributes = KeyAttributes(),

    val moreKeys: @Serializable(with = MoreKeysListSerializer::class) List<String> = listOf(),

    // If these values are set, they override spec values
    val outputText: String? = null,
    val label: String? = null,
    val code: Int? = null,
    val icon: String? = null,

    // If set, will override default hint from moreKeys
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

        val label = label ?: expandedSpec?.let { KeySpecParser.getLabel(it) } ?: ""
        val icon = icon ?: expandedSpec?.let { KeySpecParser.getIconId(it) } ?: ""
        val code = code ?: KeySpecParser.getCode(expandedSpec)
        val outputText = outputText ?: KeySpecParser.getOutputText(expandedSpec)

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

@Serializable
@SerialName("case")
data class CaseSelector(
    val normal: Key,
    val shifted: Key = normal,
    val automaticShifted: Key = shifted,
    val manualShifted: Key = shifted,
    val shiftLocked: Key = shifted,
    val shiftLockShifted: Key = shiftLocked,
    val symbols: Key = normal,
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
            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> automaticShifted
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> manualShifted
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED -> shiftLocked
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> shiftLockShifted
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



@Serializable
enum class KeyVisualStyle {
    Normal,
    NoBackground,
    Functional,
    StickyOff,
    StickyOn,
    Action,
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