package org.futo.inputmethod.latin.uix

import android.content.Context
import android.util.TypedValue
import androidx.compose.material3.ColorScheme
import java.net.URLDecoder
import java.net.URLEncoder

// Not exhaustive
fun ColorScheme.differsFrom(other: ColorScheme): Boolean {
    return this.background != other.background
            || this.surface != other.surface
            || this.primary != other.primary
            || this.secondary != other.secondary
            || this.primaryContainer != other.primaryContainer
            || this.secondaryContainer != other.secondaryContainer
            || this.onSurface != other.onSurface
            || this.onBackground != other.onBackground
            || this.onPrimary != other.onPrimary
}

fun Context.fromDp(v: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}

fun String.urlEncode(): String {
    return URLEncoder.encode(this, "utf-8")
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, "utf-8")
}