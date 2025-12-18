package org.futo.inputmethod.latin.uix.theme

import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.futo.inputmethod.latin.uix.actions.BugInfo
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.v2keyboard.KeyVisualStyle
import java.io.File

// Returns an empty set if the key is malformed
internal fun decodeKeyedBitmapKey(key: String): Set<KeyQualifier> {
    val result = mutableSetOf<KeyQualifier>()
    var tokens = key.split(" ").toMutableList()
    while(tokens.isNotEmpty()) {
        val next = tokens.removeAt(0).lowercase()
        try {
            val qualifier = when (next) {
                "normal" -> KeyQualifier.VisualStyle(KeyVisualStyle.Normal)
                "nobackground" -> KeyQualifier.VisualStyle(KeyVisualStyle.NoBackground)
                "functional" -> KeyQualifier.VisualStyle(KeyVisualStyle.Functional)
                "stickyoff" -> KeyQualifier.VisualStyle(KeyVisualStyle.StickyOff)
                "stickyon" -> KeyQualifier.VisualStyle(KeyVisualStyle.StickyOn)
                "action" -> KeyQualifier.VisualStyle(KeyVisualStyle.Action)
                "spacebar" -> KeyQualifier.VisualStyle(KeyVisualStyle.Spacebar)
                "morekey" -> KeyQualifier.VisualStyle(KeyVisualStyle.MoreKey)

                "code" -> KeyQualifier.Code(tokens.removeAt(0).toInt())
                "label" -> KeyQualifier.Label(tokens.removeAt(0))
                "icon" -> KeyQualifier.Icon(tokens.removeAt(0))
                "outputtext" -> KeyQualifier.OutputText(tokens.removeAt(0))
                "layout" -> KeyQualifier.Layout(tokens.removeAt(0))
                "pressed" -> KeyQualifier.Pressed
                "morekeysbox" -> KeyQualifier.MoreKeysKeyboardBackground
                "popup" -> KeyQualifier.Popup

                "row" -> KeyQualifier.RowColSelector(RowColSelection.RowEq(tokens.removeAt(0).toInt()))
                "col" -> KeyQualifier.RowColSelector(RowColSelection.ColEq(tokens.removeAt(0).toInt()))
                "rowmod" -> KeyQualifier.RowColSelector(RowColSelection.RowModN(tokens.removeAt(0).toInt(), tokens.removeAt(0).toInt()))
                "colmod" -> KeyQualifier.RowColSelector(RowColSelection.ColModN(tokens.removeAt(0).toInt(), tokens.removeAt(0).toInt()))

                "ratio" -> KeyQualifier.AspectRatio(KeyAspectRatio.valueOf(tokens.removeAt(0)))

                else -> {
                    BugViewerState.pushBug(BugInfo(
                        "your custom theme",
                        "Qualifier [$key] has an invalid entry: $next"
                    ))
                    return emptySet()
                }
            }

            result.add(qualifier)
        }catch(e: Exception) {
            BugViewerState.pushBug(BugInfo(
                "your custom theme",
                "Qualifier [$key] has run into an exception for $next: $e"
            ))
            return emptySet()
        }
    }

    return result
}

internal fun<T> decodeKeyedBitmaps(ctx: ThemeDecodingContext, defs: Map<String, String>, transform: (ImageBitmap) -> T?): KeyedBitmaps<T> {
    val cache = mutableMapOf<String, T?>()
    val result = mutableListOf<KeyedBitmap<T>>()
    defs.forEach {
        val qualifiers = decodeKeyedBitmapKey(it.key)
        if(qualifiers.isNotEmpty()) {
            val bitmap = cache.getOrPut(it.value) {
                decodeOptionalImage(ctx, it.value)?.let(transform)
            }
            bitmap?.let { result.add(KeyedBitmap(qualifiers, it)) }
        }
    }

    return KeyedBitmaps(result)
}

internal fun decodeOptionalImage(ctx: ThemeDecodingContext, src: String?): ImageBitmap? {
    if(src == null) return null
    val hash = ctx.getFileHashOrBase64(src)
    if(hash == null) return null
    var bitmap = ZipThemes.bitmapCache[hash]
    if(bitmap == null) {
        bitmap = ctx.getFileBytesOrBase64(src)?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
        }
        if(bitmap == null) return null
        ZipThemes.bitmapCache[hash] = bitmap
    }

    return bitmap
}

internal fun decodeOptionalFont(ctx: ThemeDecodingContext, src: String?): Typeface? {
    if(src == null) return null
    val hash = ctx.getFileHashOrBase64(src)
    if(hash == null) return null

    val temp = File(ctx.context.cacheDir, "theme_font_$hash.ttf")
    if(!temp.exists()) {
        ctx.getFileBytesOrBase64(src)?.let { bytes ->
            temp.outputStream().use { out -> out.write(bytes) }
        }
    }

    return if(temp.exists()) Typeface.createFromFile(temp) else null
}