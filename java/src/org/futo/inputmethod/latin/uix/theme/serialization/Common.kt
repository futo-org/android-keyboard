package org.futo.inputmethod.latin.uix.theme.serialization

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeDecodingContext

enum class AlphaOrder {
    RGBA,
    ARGB
}

fun parseHexColorStringToARGBLong(string: String, hexOrder: AlphaOrder): Long {
    val str = string.removePrefix("#").uppercase()
    val len = str.length

    if(len != 3 && len != 6 && len != 8)
        throw IllegalArgumentException("Color string does not match")

    val a = when {
        len != 8 -> "FF"
        hexOrder == AlphaOrder.RGBA -> str.substring(6, 8)
        hexOrder == AlphaOrder.ARGB -> str.substring(0, 2)
        else -> throw IllegalStateException()
    }

    val rgb = when {
        len == 6 -> str
        len == 3 -> "${str[0]}${str[0]}${str[1]}${str[1]}${str[2]}${str[2]}"
        hexOrder == AlphaOrder.RGBA -> str.substring(0, 6)
        hexOrder == AlphaOrder.ARGB -> str.substring(2, 8)
        else -> throw IllegalStateException()
    }

    val (r, g, b) = listOf(rgb.substring(0, 2), rgb.substring(2, 4), rgb.substring(4, 6))

    val color = (a.toLong(16) shl 24) or
            (r.toLong(16) shl 16) or
            (g.toLong(16) shl 8) or
            b.toLong(16)

    return color and 0xFFFFFFFFL
}

fun argbLongToHexColorString(argb: Long, hexOrder: AlphaOrder): String {
    val a = (argb shr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8)  and 0xFF
    val b =  argb         and 0xFF

    val hex = buildString {
        if(hexOrder == AlphaOrder.ARGB && a.toInt() != 0xFF) {
            append("%02X".format(a))
        }

        append("%02X".format(r))
        append("%02X".format(g))
        append("%02X".format(b))

        if(hexOrder == AlphaOrder.RGBA && a.toInt() != 0xFF) {
            append("%02X".format(a))
        }
    }

    return "#$hex"
}

val Color.long get() = toArgb().toUInt().toLong()


interface SerializableTheme {
    fun toKeyboardScheme(ctx: ThemeDecodingContext): KeyboardColorScheme
    fun validate(): Boolean
    val id: String?
    val thumbnailImage: String?

    val name: String
    val author: String
}