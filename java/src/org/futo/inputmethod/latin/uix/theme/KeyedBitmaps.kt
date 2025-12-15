package org.futo.inputmethod.latin.uix.theme

import androidx.compose.ui.graphics.ImageBitmap
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.v2keyboard.KeyVisualStyle

sealed class KeyQualifier {
    data class VisualStyle(val visualStyle: KeyVisualStyle) : KeyQualifier()
    data class Layout(val name: String) : KeyQualifier()
    data class Label(val label: String) : KeyQualifier()
    data class Icon(val icon: String) : KeyQualifier()
    data class Code(val code: Int) : KeyQualifier()
    data class OutputText(val outputText: String) : KeyQualifier()
    data object Pressed : KeyQualifier()
    data object MoreKeysKeyboardBackground : KeyQualifier()
    data object Popup : KeyQualifier()
}

fun Key.hashCodeForQualifiers(): Int {
    var result = code.hashCode()

    // Whenever a new qualifier type is added, this must be updated
    result = 31 * result + label.hashCode()
    result = 31 * result + (iconOverride ?: iconId).hashCode()
    result = 31 * result + outputText.hashCode()
    result = 31 * result + visualStyle.hashCode()
    result = 31 * result + pressed.hashCode()

    // Used in AdvancedThemeCustomizer.kt
    result = 31 * result + effectiveHintLabel.hashCode()
    result = 31 * result + effectiveHintIcon.hashCode()

    return result
}

fun matchesKey(qualifiers: Set<KeyQualifier>, layout: String, key: Key, popup: Boolean = false) =
    (qualifiers.contains(KeyQualifier.Popup) == popup) && qualifiers.all { when(it) {
        is KeyQualifier.Layout -> layout == it.name
        is KeyQualifier.Code -> key.code == it.code
        is KeyQualifier.Label -> key.label == it.label
        is KeyQualifier.Icon -> (key.iconOverride ?: key.iconId) == it.icon
        is KeyQualifier.OutputText -> key.outputText == it.outputText
        is KeyQualifier.VisualStyle -> key.visualStyle == it.visualStyle
        is KeyQualifier.Pressed -> key.pressed
        is KeyQualifier.MoreKeysKeyboardBackground -> false
        is KeyQualifier.Popup -> popup
    } }

fun matchesHint(qualifiers: Set<KeyQualifier>, layout: String, hintLabel: String?, hintIcon: String?) =
    qualifiers.all { when(it) {
        is KeyQualifier.Layout -> layout == it.name
        is KeyQualifier.Label -> hintLabel == it.label
        is KeyQualifier.Icon -> hintIcon == it.icon
        else -> false
    } }

fun matchesMoreKeysKeyboardBackground(qualifiers: Set<KeyQualifier>, layout: String) =
    qualifiers.contains(KeyQualifier.MoreKeysKeyboardBackground) && qualifiers.all { when(it) {
        is KeyQualifier.Layout -> layout == it.name
        is KeyQualifier.MoreKeysKeyboardBackground -> true
        else -> false
    } }

data class KeyedBitmap<T>(val qualifiers: Set<KeyQualifier>, val bitmap: T)
data class KeyedBitmaps<T>(val v: List<KeyedBitmap<T>>)