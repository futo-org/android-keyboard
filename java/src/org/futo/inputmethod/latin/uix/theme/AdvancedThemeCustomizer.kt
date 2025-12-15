package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.get
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
    val textColor: Int,
    val hintColor: Int,
    val textSize: Float,
    val hintSize: Float,
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

internal fun<A, B> Pair<A, B?>.requireSecond(): Pair<A, B>? = second?.let { first to it }

// Require this color not be invisible
internal fun Int?.argbNotInvisible(): Int? = this?.let {
    if((it shr 24) and 0xff == 0) null else it
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

    val backgroundDrawables = backgroundList.associate {
        it to lazy {
            val bitmap = it.image.asAndroidBitmap()
            val fgColor = bitmap[0, 0]

            fgColor to bitmap.toNinePatchDrawable(context.resources)
        }
    }

    val foregroundDrawables = iconList.associate {
        it to lazy {
            val bitmap = it.image.asAndroidBitmap()

            bitmap.toDrawable(context.resources)
        }
    }

    fun findForeground(matcher: CachedKeyedMatcher<KeyedBitmap>, keyboard: Keyboard, key: Key): Drawable? {
        val bitmap = matcher.find(keyboard, key)
        if(bitmap == null) return null

        return foregroundDrawables[bitmap]?.value
    }

    fun findBackground(matcher: CachedKeyedMatcher<KeyedBitmap>, keyboard: Keyboard, key: Key): Pair<Int, Drawable>? {
        val bitmap = matcher.find(keyboard, key)
        if(bitmap == null) return null

        return backgroundDrawables[bitmap]?.value?.requireSecond()
    }

    fun matchKeyDrawingConfiguration(keyboard: Keyboard?, params: KeyDrawParams, key: Key): KeyDrawingConfiguration {
        if(keyboard == null) return KeyDrawingConfiguration(
            background = null,
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
        val background = foundBackground?.second ?: key.selectBackground(drawableProvider)
        val textColor = foundBackground?.first.argbNotInvisible() ?: key.selectTextColor(drawableProvider, params)

        val hintColor = foundBackground?.first.argbNotInvisible()?.let {
            Color(it).copy(alpha = 0.8f).toArgb()
        } ?: key.selectHintTextColor(drawableProvider, params)

        val icon = findForeground(icons, keyboard, key) ?: key.getIconOverride(keyboard.mIconsSet, params.mAnimAlpha)
        val hintIcon = findForeground(hintIcons, keyboard, key)

        var label: String? = key.labelOverride ?: key.label
        var hintLabel: String? = if(hintIcon == null) key.effectiveHintLabel else null

        val textSize = key.selectTextSize(params).toFloat()
        val hintSize = key.selectHintTextSize(drawableProvider, params).toFloat()

        return KeyDrawingConfiguration(
            background = background,
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