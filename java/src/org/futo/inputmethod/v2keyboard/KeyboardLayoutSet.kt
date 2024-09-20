package org.futo.inputmethod.v2keyboard

import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.KeyboardId
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutElement
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutKind
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutPage
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.latin.settings.LongPressKeySettings
import org.futo.inputmethod.latin.uix.KeyboardHeightMultiplierSetting
import org.futo.inputmethod.latin.uix.actions.BugInfo
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.utils.InputTypeUtils
import org.futo.inputmethod.latin.utils.ResourceUtils
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
enum class Script(val id: Int, val iso4letterCode: String) {
    Unknown(-1, ""),
    Arabic(0, "arab"),
    Armenian(1, "armn"),
    Bengali(2, "beng"),
    Cyrillic(3, "cyrl"),
    Devanagari(4, "deva"),
    Georgian(5, "geor"),
    Greek(6, "grek"),
    Hebrew(7, "hebr"),
    Kannada(8, "knda"),
    Khmer(9, "khmr"),
    Lao(10, "laoo"),
    Latin(11, "latn"),
    Malayalam(12, "mlym"),
    Myanmar(13, "mymr"),
    Sinhala(14, "sinh"),
    Tamil(15, "taml"),
    Telugu(16, "telu"),
    Thai(17, "thai"),
}

fun Locale.getKeyboardScript(): Script =
    script.lowercase().let { code ->
        Script.entries.firstOrNull { it.iso4letterCode == code }
            ?: Script.Unknown
    }



private fun EditorInfo.getPrivateImeOptions(): Map<String, String> {
    val options = mutableMapOf<String, String>()
    val imeOptions = privateImeOptions ?: return options

    try {
        imeOptions.split(",").forEach { option ->
            if (option.contains('=') && option.split('=').size == 2) {
                val (key, value) = option.split("=")
                options[key.trim()] = value.trim()
            }
        }
    } catch(e: Exception) {
        e.printStackTrace()
    }

    return options
}

private fun EditorInfo.getPrimaryLayoutOverride(): String? =
    getPrivateImeOptions()["org.futo.inputmethod.latin.ForceLayout"]


data class KeyboardLayoutSetV2Params(
    val width: Int,
    val height: Int?,
    val keyboardLayoutSet: String,
    val locale: Locale,
    val editorInfo: EditorInfo?,
    val numberRow: Boolean,
    val gap: Float = 4.0f,
    val useSplitLayout: Boolean,
    val bottomActionKey: Int?,
    val longPressKeySettings: LongPressKeySettings? = null
)


class KeyboardLayoutSetV2 internal constructor(
    private val context: Context,
    private val params: KeyboardLayoutSetV2Params
) {
    val script = Script.Latin

    val editorInfo = params.editorInfo ?: EditorInfo()

    val privateParams = editorInfo.getPrivateImeOptions()
    val forcedLayout = privateParams["org.futo.inputmethod.latin.ForceLayout"]
    val forcedLocale = privateParams["org.futo.inputmethod.latin.ForceLocale"]?.let { Locale.forLanguageTag(it) }

    // Necessary for Java API
    fun getScriptId(): Int = script.id

    private val keyboardMode = getKeyboardMode(editorInfo)

    val layoutName = forcedLayout ?: params.keyboardLayoutSet
    val mainLayout = LayoutManager.getLayout(context, layoutName)
    val symbolsLayout = LayoutManager.getLayout(context, mainLayout.layoutSetOverrides.symbols)
    val symbolsShiftedLayout = LayoutManager.getLayout(context, mainLayout.layoutSetOverrides.symbolsShifted)
    val numberLayout = LayoutManager.getLayout(context, mainLayout.layoutSetOverrides.number)
    val numberShiftLayout = LayoutManager.getLayout(context, mainLayout.layoutSetOverrides.numberShifted)
    val phoneLayout = LayoutManager.getLayout(context, mainLayout.layoutSetOverrides.phone)
    val phoneSymbolsLayout = LayoutManager.getLayout(context, mainLayout.layoutSetOverrides.phoneShifted)
    val errorLayout = LayoutManager.getLayout(context, "error")

    val elements = mapOf(
        KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Alphabet,
            page = KeyboardLayoutPage.Base
        ) to mainLayout,

        KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Alphabet,
            page = KeyboardLayoutPage.Shifted
        ) to mainLayout,

        KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Symbols,
            page = KeyboardLayoutPage.Base
        ) to symbolsLayout,

        KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Symbols,
            page = KeyboardLayoutPage.Shifted
        ) to symbolsShiftedLayout,

        KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Phone,
            page = KeyboardLayoutPage.Base
        ) to phoneLayout,

        KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Phone,
            page = KeyboardLayoutPage.Shifted
        ) to phoneSymbolsLayout,

        KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Number,
            page = KeyboardLayoutPage.Base
        ) to numberLayout,

        KeyboardLayoutElement(
            kind = KeyboardLayoutKind.Number,
            page = KeyboardLayoutPage.Shifted
        ) to numberShiftLayout,
    )

    private fun getKeyboardLayoutForElement(element: KeyboardLayoutElement): org.futo.inputmethod.v2keyboard.Keyboard {
        return elements[element.normalize()] ?: run {
            // If this is an alt layout, try to get the matching alt
            element.page.altIdx?.let { altIdx ->
                val baseElement = element.copy(page = KeyboardLayoutPage.Base)
                val baseLayout = elements[baseElement]
                baseLayout?.altPages?.get(altIdx)
            }?.let {
                mainLayout.copy(rows = it)
            }
        } ?: run {
            // If all else fails, show the error layout
            BugViewerState.pushBug(BugInfo("KeyboardLayoutSet",
                "Keyboard $layoutName does not have element $element. Available elements: ${elements.keys}"))
            errorLayout
        }
    }

    private val isNumberRowActive: Boolean
        get() = when(mainLayout.numberRowMode) {
            NumberRowMode.UserConfigurable -> params.numberRow
            NumberRowMode.AlwaysEnabled    -> true
            NumberRowMode.AlwaysDisabled   -> false
        }

    private val singularRowHeight: Double
        get() = params.height?.let { it / 4.0 } ?: run {
            (ResourceUtils.getDefaultKeyboardHeight(context.resources) / 4.0) *
                    keyboardHeightMultiplier
        }

    private fun getRecommendedKeyboardHeight(): Int {
        val numRows = 4.0 +
                ((mainLayout.effectiveRows.size - 5) / 2.0).coerceAtLeast(0.0) +
                if(isNumberRowActive) { 0.5 } else { 0.0 }

        // Clamp if necessary (disabled for now)
        if(false && params.height == null) {
            return ResourceUtils.clampKeyboardHeight(context.resources, (singularRowHeight * numRows).roundToInt())
        } else {
            return (singularRowHeight * numRows).roundToInt()
        }
    }

    fun getKeyboard(element: KeyboardLayoutElement): Keyboard {
        val keyboardId = KeyboardId(
            params.keyboardLayoutSet,
            forcedLocale ?: params.locale,
            params.width,
            params.height ?: getRecommendedKeyboardHeight(),
            keyboardMode,
            element.elementId,
            editorInfo,
            false,
            params.bottomActionKey != null,
            params.bottomActionKey ?: -1,
            editorInfo.actionLabel?.toString() ?: "",
            false,
            false,
            isNumberRowActive,
            params.longPressKeySettings ?: LongPressKeySettings.load(context)
        )

        val layout = getKeyboardLayoutForElement(element)

        val keyboardParams = KeyboardParams().apply {
            mId = keyboardId
            mTextsSet.setLocale(keyboardId.locale, context)
        }

        val layoutParams = LayoutParams(
            gap = params.gap.dp,
            useSplitLayout = params.useSplitLayout,
            standardRowHeight = singularRowHeight,
            element = element
        )

        try {
            return layout.build(context, keyboardParams, layoutParams)
        } catch(e: Exception) {
            Log.e("KeyboardLayoutSet", "Failed to load element $element for keyboard layout set $layoutName. Message: ${e.message}")
            Log.e("KeyboardLayoutSet", "LayoutSet params: $params, keyboardId: $keyboardId")
            e.printStackTrace()

            BugViewerState.pushBug(BugInfo(
                name = "KeyboardLayoutSet",
                details =
"""
Element $element for layout $layoutName could not be loaded

Cause: ${e.message}

Params: $params

Stack trace: ${e.stackTrace.map { it.toString() }}
"""
            ))

            return errorLayout.build(context, keyboardParams, layoutParams)
        }
    }

    companion object {
        var keyboardHeightMultiplier: Float = 1.0f

        @JvmStatic
        fun onSystemLocaleChanged() {

        }

        @JvmStatic
        fun onKeyboardThemeChanged(context: Context) {
            keyboardHeightMultiplier = context.getSettingBlocking(KeyboardHeightMultiplierSetting)

            // This is where we would clear all caches if we had any
        }
    }
}

public fun getKeyboardMode(editorInfo: EditorInfo): Int {
    val inputType = editorInfo.inputType
    val variation = inputType and InputType.TYPE_MASK_VARIATION

    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER -> KeyboardId.MODE_NUMBER
        InputType.TYPE_CLASS_DATETIME -> when (variation) {
            InputType.TYPE_DATETIME_VARIATION_DATE -> KeyboardId.MODE_DATE
            InputType.TYPE_DATETIME_VARIATION_TIME -> KeyboardId.MODE_TIME
            else -> KeyboardId.MODE_DATETIME
        }

        InputType.TYPE_CLASS_PHONE -> KeyboardId.MODE_PHONE
        InputType.TYPE_CLASS_TEXT -> if (InputTypeUtils.isEmailVariation(variation)) {
            KeyboardId.MODE_EMAIL
        } else if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
            KeyboardId.MODE_URL
        } else if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
            KeyboardId.MODE_IM
        } else if (variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
            KeyboardId.MODE_TEXT
        } else {
            KeyboardId.MODE_TEXT
        }

        else -> KeyboardId.MODE_TEXT
    }
}
