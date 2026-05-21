package org.futo.inputmethod.latin.uix.theme

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.view.View
import android.view.Window
import androidx.annotation.ColorInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.actions.compatEmojiTypeface
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import kotlin.math.sqrt

fun applyWindowColors(window: Window, @ColorInt color: Int, statusBar: Boolean) {
    if(statusBar) {
        window.statusBarColor = color
    }
    window.navigationBarColor = color

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val view = window.decorView
        val uiFlags = view.systemUiVisibility

        val luminance = sqrt(
            0.299 * android.graphics.Color.red(color) / 255.0
                    + 0.587 * android.graphics.Color.green(color) / 255.0
                    + 0.114 * android.graphics.Color.blue(color) / 255.0
        )

        if (luminance > 0.5 && color != android.graphics.Color.TRANSPARENT) {
            view.systemUiVisibility = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            view.systemUiVisibility = uiFlags and (View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv())
        }
    }
}

@Composable
fun StatusBarColorSetter() {
    val backgroundColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    LaunchedEffect(backgroundColor) {
        applyWindowColors((context as Activity).window, backgroundColor.toArgb(), statusBar = false)
    }
}

val LocalCompatEmojiFamily = compositionLocalOf<FontFamily?> { null }
val LocalCompatEmojiTypeface = compositionLocalOf<Typeface?> { null }

private var systemPaint = TextPaint().apply {
    typeface = Typeface.DEFAULT
    textSize = 64f
}
private var compatPaint = TextPaint().apply {
    textSize = 64f
}
private var compatCache = mutableMapOf<String, Int>()
private fun putCompatCache(emoji: String, compatTypeface: Typeface?): Int {
    compatPaint.typeface = compatTypeface

    return when {
        systemPaint.hasGlyph(emoji) -> 0
        compatPaint.hasGlyph(emoji) -> 1
        else -> 2
    }
}
fun emojiNeedsCompat(emoji: String, compatTypeface: Typeface?): Boolean =
    compatCache.getOrPut(emoji) { putCompatCache(emoji, compatTypeface) } == 1
fun emojiShouldShow(emoji: String, compatTypeface: Typeface?): Boolean =
    compatCache.getOrPut(emoji) { putCompatCache(emoji, compatTypeface) } != 2

@Composable
fun UixThemeWrapper(colorScheme: KeyboardColorScheme, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val emojiTypeface = remember { context.compatEmojiTypeface }
    val emojiFamily = remember(emojiTypeface) { emojiTypeface?.let { FontFamily(it) } }
    CompositionLocalProvider(
        LocalKeyboardScheme provides colorScheme,
        LocalCompatEmojiFamily provides emojiFamily,
        LocalCompatEmojiTypeface provides emojiTypeface
    ) {
        MaterialTheme(
            colorScheme = colorScheme.base,
            content = content,
        )
    }
}

fun ThemeOption?.ensureAvailable(context: Context): ThemeOption? {
    return if(this == null) {
        null
    } else {
        if(!this.available(context)) {
            null
        } else {
            this
        }
    }
}

@Composable
fun UixThemeAuto(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val themeIdx = useDataStoreValue(THEME_KEY)

    val theme: ThemeOption = getThemeOption(context, themeIdx).orDefault(context)

    val colors = remember(theme.key) { theme.obtainColors(context) }

    UixThemeWrapper(colorScheme = colors, content)
}