package org.futo.inputmethod.latin.uix.theme


import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.v2keyboard.KeyVisualStyle

sealed class RowColSelection {
    data class RowEq(val n: Int) : RowColSelection()
    data class ColEq(val n: Int) : RowColSelection()
    data class RowModN(val a: Int, val n: Int) : RowColSelection()
    data class ColModN(val a: Int, val n: Int) : RowColSelection()
}

private fun RowColSelection.match(keyboard: Keyboard, key: Key): Boolean = when(this) {
    is RowColSelection.ColEq -> key.column == n.let {
        if(it >= 0) it else {
            keyboard.sortedKeys.filter { it.row == key.row } .maxOf { it.column } + it + 1
        }
    }
    is RowColSelection.RowEq -> key.row == n.let {
        if(it >= 0) it else {
            keyboard.sortedKeys.maxOf { it.row } + it + 1
        }
    }
    is RowColSelection.ColModN -> (key.column+a) % n == 0
    is RowColSelection.RowModN -> (key.row+a) % n == 0
}

enum class KeyAspectRatio(val range: ClosedFloatingPointRange<Float>) {
    Tallest  (2.20f..Float.POSITIVE_INFINITY),
    Tall     (1.35f..2.20f),
    Squarish (0.85f..1.35f),
    Wide     (0.50f..0.85f),
    ExtraWide(0.33f..0.50f),
    Widest   (0.00f..0.33f)
}

sealed class KeyQualifier {
    data class VisualStyle(val visualStyle: KeyVisualStyle) : KeyQualifier()
    data class Layout(val name: String) : KeyQualifier()
    data class Label(val label: String) : KeyQualifier()
    data class Icon(val icon: String) : KeyQualifier()
    data class Code(val code: Int) : KeyQualifier()
    data class OutputText(val outputText: String) : KeyQualifier()
    data class RowColSelector(val selection: RowColSelection) : KeyQualifier()
    data object Pressed : KeyQualifier()
    data object MoreKeysKeyboardBackground : KeyQualifier()
    data object Popup : KeyQualifier()

    data class AspectRatio(val target: KeyAspectRatio) : KeyQualifier()
}

fun matchesKey(qualifiers: Set<KeyQualifier>, layout: String, keyboard: Keyboard, key: Key, popup: Boolean = false) =
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
        is KeyQualifier.RowColSelector -> it.selection.match(keyboard, key)
        is KeyQualifier.AspectRatio -> it.target.range.contains(key.height.toFloat() / key.width.toFloat())
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