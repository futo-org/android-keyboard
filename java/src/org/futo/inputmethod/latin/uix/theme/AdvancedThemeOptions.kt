package org.futo.inputmethod.latin.uix.theme

import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp

data class KeyBackground(
    val foregroundColor: Int?,
    val padding: Rect,
    val background: Drawable
)

data class KeyIcon(
    val drawable: Drawable
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
    val themeName: String? = null,
    val themeAuthor: String? = null,

    // Custom padding settings to control the empty space around the left/right and top/bottom of each key.
    val keyPaddingHorizontal: Dp? = null,
    val keyPaddingVertical: Dp? = null,
    // A switch to toggle thicker, bold text for the keyboard keys.
    val forceBold: Boolean = false,
)