package org.futo.inputmethod.latin.uix.theme

import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap

data class AdvancedThemeOptions(
    val backgroundShader: String? = null,
    val backgroundImage: ImageBitmap? = null,
    val backgroundImageVisibleArea: Rect? = null,
    val thumbnailImage: ImageBitmap? = null,
    val thumbnailScale: Float = 1.0f,
    val keyRoundness: Float = 1.0f,
    val keyBorders: Boolean? = null,
    val keyBackgrounds: KeyedBitmaps? = null,
    val keyIcons: KeyedBitmaps? = null,
    val font: Typeface? = null,
)