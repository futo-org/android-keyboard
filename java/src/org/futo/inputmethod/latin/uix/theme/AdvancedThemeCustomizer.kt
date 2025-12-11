package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.drawable.toDrawable
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.internal.KeyDrawParams
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.utils.toNinePatchDrawable


data class KeyDrawingConfiguration(
    val background: Drawable?,
    val icon: Drawable?,
    val hintIcon: Drawable?,
    val label: String?,
    val hintLabel: String?,
)

data class CachedKeyedMatcher<T>(
    val full: List<T>,
    val cache: MutableMap<Int, List<T>> = mutableMapOf(),
    val matcher: (Keyboard, Key, T) -> Boolean
) {
    fun find(keyboard: Keyboard, key: Key): T? {
        val hash = key.hashCodeForQualifiers()
        return if(cache.containsKey(hash)) {
            cache[hash]?.firstOrNull { matcher(keyboard, key, it) }
        } else {
            full.firstOrNull { matcher(keyboard, key, it) }.also {
                cache.put(hash, listOfNotNull(it))
            }
        }
    }
}

private val KeyedBitmapMatcher = { keyboard: Keyboard, key: Key, entry: KeyedBitmap ->
    matchesKey(entry.qualifiers, keyboard.mId.mKeyboardLayoutSetName, key)
}

class AdvancedThemeMatcher(
    val context: Context,
    val drawableProvider: DynamicThemeProvider,
    val scheme: KeyboardColorScheme
) {
    val theme = scheme.extended.advancedThemeOptions

    val backgroundList = theme.keyBackgrounds?.v ?: emptyList()
    val iconList = theme.keyIcons?.v ?: emptyList()

    val backgrounds = CachedKeyedMatcher(full = backgroundList, matcher = KeyedBitmapMatcher)
    val icons = CachedKeyedMatcher(full = iconList, matcher = KeyedBitmapMatcher)

    val hintIcons = CachedKeyedMatcher(full = iconList, matcher = { keyboard: Keyboard, key: Key, entry: KeyedBitmap ->
        val hintLabel = key.effectiveHintLabel
        val hintIcon = key.effectiveHintIcon
        matchesHint(entry.qualifiers, keyboard.mId.mKeyboardLayoutSetName, hintLabel, hintIcon)
    })

    val drawables =
        backgroundList.associate {
            it to lazy {
                // TODO: We need to pick foreground color from image as well.
                //  Probably the top left pixel
                val bitmap = it.image.asAndroidBitmap()
                bitmap.toNinePatchDrawable(context.resources)
            }
        } + iconList.associate {
            it to lazy {
                val bitmap = it.image.asAndroidBitmap()
                bitmap.toDrawable(context.resources)
            }
        }

    fun findDrawable(matcher: CachedKeyedMatcher<KeyedBitmap>, keyboard: Keyboard, key: Key): Drawable? {
        val bitmap = matcher.find(keyboard, key)
        if(bitmap == null) return null

        return drawables[bitmap]?.value
    }

    fun matchKeyDrawingConfiguration(keyboard: Keyboard?, params: KeyDrawParams, key: Key): KeyDrawingConfiguration {
        if(keyboard == null) return KeyDrawingConfiguration(null, null, null, key.labelOverride ?: key.label, key.effectiveHintLabel)

        val background = findDrawable(backgrounds, keyboard, key) ?: key.selectBackground(drawableProvider)
        val icon = findDrawable(icons, keyboard, key) ?: key.getIconOverride(keyboard.mIconsSet, params.mAnimAlpha)
        val hintIcon = findDrawable(hintIcons, keyboard, key)

        var label: String? = key.labelOverride ?: key.label
        var hintLabel: String? = if(hintIcon == null) key.effectiveHintLabel else null

        return KeyDrawingConfiguration(
            background = background,
            icon = icon,
            hintIcon = hintIcon ?: key.getHintIcon(keyboard.mIconsSet, params.mAnimAlpha),
            label = label,
            hintLabel = hintLabel
        )
    }
}