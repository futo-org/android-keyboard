package org.futo.inputmethod.latin.uix.theme

import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.NinePatchDrawable
import androidx.compose.ui.graphics.ImageBitmap

data class KeyBackground(
    val foregroundColor: Int?,
    val background: NinePatchDrawable
)

data class KeyIcon(
    val drawable: BitmapDrawable
)

data class AdvancedThemeOptions(
    val backgroundShader: String? = null,
    val backgroundImage: ImageBitmap? = null,
    val backgroundImageVisibleArea: Rect? = null,
    val thumbnailImage: ImageBitmap? = null,
    val thumbnailScale: Float = 1.0f,
    val keyRoundness: Float = 1.0f,
    val keyBorders: Boolean? = null,
    val keyBackgrounds: KeyedBitmaps<KeyBackground>? = null,
    val keyIcons: KeyedBitmaps<KeyIcon>? = null,
    val font: Typeface? = null,
)