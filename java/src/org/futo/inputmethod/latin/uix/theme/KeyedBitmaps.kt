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
}

// TODO: Need to use this on KeyboardView
fun matchesKey(qualifiers: Set<KeyQualifier>, layout: String, key: Key) =
    qualifiers.all { when(it) {
        is KeyQualifier.Layout -> layout == it.name
        is KeyQualifier.Code -> key.code == it.code
        is KeyQualifier.Label -> key.label == it.label
        is KeyQualifier.Icon -> (key.iconOverride ?: key.iconId) == it.icon
        is KeyQualifier.OutputText -> key.outputText == it.outputText
        is KeyQualifier.VisualStyle -> key.visualStyle == it.visualStyle
    } }

data class KeyedBitmap(val qualifiers: Set<KeyQualifier>, val image: ImageBitmap)
data class KeyedBitmaps(val v: List<KeyedBitmap>)