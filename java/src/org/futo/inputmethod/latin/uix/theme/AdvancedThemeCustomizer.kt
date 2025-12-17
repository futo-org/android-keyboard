package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.internal.KeyDrawParams
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.KeyboardColorScheme


data class KeyDrawingConfiguration(
    val background: Drawable?,
    val backgroundPadding: Rect?,
    val icon: Drawable?,
    val hintIcon: Drawable?,
    val label: String?,
    val hintLabel: String?,
    val textColor: Int,
    val hintColor: Int,
    val textSize: Float,
    val hintSize: Float,
)

data class CachedKeyedMatcher<T>(
    val cacheId: Int,
    val full: List<T>,
    val matcher: (Keyboard, Key, T) -> Boolean
) {
    fun find(keyboard: Keyboard, key: Key): T? {
        val idx = key.themeCache.getOrPut(cacheId) {
            full.indexOfFirst { matcher(keyboard, key, it) }
        }
        return if(idx == -1) null else full[idx]
    }
}

internal fun<T> keyedBitmapMatcher(popup: Boolean = false) = { keyboard: Keyboard, key: Key, entry: KeyedBitmap<T> ->
    matchesKey(entry.qualifiers, keyboard.mId.mKeyboardLayoutSetName, keyboard, key, popup)
}

private var globalId = 0
class AdvancedThemeMatcher(
    val context: Context,
    val drawableProvider: DynamicThemeProvider,
    val scheme: KeyboardColorScheme
) {
    val theme = scheme.extended.advancedThemeOptions

    val backgroundList = theme.keyBackgrounds?.v ?: emptyList()
    val iconList = theme.keyIcons?.v ?: emptyList()

    val id = (globalId++)*10
    val backgrounds = CachedKeyedMatcher(cacheId = id+0, full = backgroundList, matcher = keyedBitmapMatcher<KeyBackground>())
    val icons = CachedKeyedMatcher(cacheId = id+1, full = iconList, matcher = keyedBitmapMatcher<KeyIcon>())
    val popupBackgrounds = CachedKeyedMatcher(cacheId = id+2, full = backgroundList, matcher = keyedBitmapMatcher<KeyBackground>(true))
    val hintIcons = CachedKeyedMatcher(cacheId = id+3, full = iconList, matcher = { keyboard: Keyboard, key: Key, entry: KeyedBitmap<KeyIcon> ->
        val hintLabel = key.effectiveHintLabel
        val hintIcon = key.effectiveHintIcon
        matchesHint(entry.qualifiers, keyboard.mId.mKeyboardLayoutSetName, hintLabel, hintIcon)
    })

    fun findIcon(matcher: CachedKeyedMatcher<KeyedBitmap<KeyIcon>>, keyboard: Keyboard, key: Key): KeyIcon? {
        if(matcher.full.isEmpty()) return null

        val bitmap = matcher.find(keyboard, key)
        if(bitmap == null) return null

        return bitmap.bitmap
    }

    fun findBackground(matcher: CachedKeyedMatcher<KeyedBitmap<KeyBackground>>, keyboard: Keyboard, key: Key): KeyBackground? {
        if(matcher.full.isEmpty()) return null

        val bitmap = matcher.find(keyboard, key)
        if(bitmap == null) return null

        return bitmap.bitmap
    }

    fun matchMoreKeysKeyboardBackground(layout: String): Drawable?
        = theme.keyBackgrounds?.v?.firstOrNull {
            matchesMoreKeysKeyboardBackground(it.qualifiers, layout)
        }?.bitmap?.background

    fun matchKeyPopup(keyboard: Keyboard?, key: Key): KeyBackground? {
        if(popupBackgrounds.full.isEmpty()) return null
        if(keyboard == null) return null
        return popupBackgrounds.find(keyboard, key)?.bitmap
    }

    val identityRect = Rect(0,0,0,0)

    fun matchKeyDrawingConfiguration(keyboard: Keyboard?, params: KeyDrawParams, key: Key): KeyDrawingConfiguration {
        if(keyboard == null) return KeyDrawingConfiguration(
            background = null,
            backgroundPadding = identityRect,
            icon = null,
            hintIcon = null,
            label = key.labelOverride ?: key.label,
            hintLabel = key.effectiveHintLabel,
            textColor = key.selectTextColor(drawableProvider, params),
            hintColor = key.selectHintTextColor(drawableProvider, params),
            textSize = key.selectTextSize(params).toFloat(),
            hintSize = key.selectHintTextSize(drawableProvider, params).toFloat()
        )

        val foundBackground = findBackground(backgrounds, keyboard, key)
        val backgroundPadding = foundBackground?.padding ?: identityRect
        val background = foundBackground?.background ?: key.selectBackground(drawableProvider)
        val textColor = foundBackground?.foregroundColor ?: key.selectTextColor(drawableProvider, params)

        val hintColor = foundBackground?.foregroundColor?.let {
            Color(it).copy(alpha = 0.8f).toArgb()
        } ?: key.selectHintTextColor(drawableProvider, params)

        val icon = findIcon(icons, keyboard, key)?.drawable ?: key.getIconOverride(keyboard.mIconsSet, params.mAnimAlpha)
        val hintIcon = findIcon(hintIcons, keyboard, key)?.drawable

        var label: String? = key.labelOverride ?: key.label
        var hintLabel: String? = if(hintIcon == null) key.effectiveHintLabel else null

        val textSize = key.selectTextSize(params).toFloat()
        val hintSize = key.selectHintTextSize(drawableProvider, params).toFloat()

        return KeyDrawingConfiguration(
            background = background,
            backgroundPadding = backgroundPadding,
            icon = icon,
            hintIcon = hintIcon ?: key.getHintIcon(keyboard.mIconsSet, params.mAnimAlpha),
            label = label,
            hintLabel = hintLabel,
            textColor = textColor,
            hintColor = hintColor,
            textSize = textSize,
            hintSize = hintSize,
        )
    }
}