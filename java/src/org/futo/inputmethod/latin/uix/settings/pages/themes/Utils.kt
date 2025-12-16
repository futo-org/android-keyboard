package org.futo.inputmethod.latin.uix.settings.pages.themes

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

@Suppress("DEPRECATION")
fun applyBlur(context: Context, bitmap: Bitmap, blurLevel: Float): Bitmap {
    var radius = blurLevel * blurLevel * bitmap.width * 0.2f
    var scale = 1.0f
    if(radius > 25f) {
        scale = 25f/radius
        radius *= scale
    }

    val input = if (scale < 1.0f) {
        bitmap.scale(
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1))
    } else {
        bitmap.copy(bitmap.config!!, true)
    }

    val rs = RenderScript.create(context)
    val inputAlloc = Allocation.createFromBitmap(rs, input)
    val outputAlloc = Allocation.createTyped(rs, inputAlloc.type)
    val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

    blur.setRadius(radius.coerceIn(0.01f, 25f))
    blur.setInput(inputAlloc)
    blur.forEach(outputAlloc)
    outputAlloc.copyTo(input)

    val result = input

    rs.destroy()
    return result
}

@Composable
internal inline fun<T> rememberDelayedRecomputed(
    vararg keys: Any?,
    delay: Long = 100L,
    crossinline calculation: @DisallowComposableCalls (() -> T)
): MutableState<T> {
    val state = remember { mutableStateOf(calculation()) }
    LaunchedEffect(*keys) {
        delay(delay)
        state.value = withContext(Dispatchers.Default) { calculation() }
    }
    return state
}

@SuppressLint("RestrictedApi")
internal fun extractMainColor(bitmap: Bitmap): Hct2 {
    var scaledBitmap = bitmap
    if(scaledBitmap.width > 64 || scaledBitmap.height > 64) {
        val scale = minOf(64f / scaledBitmap.width, 64f / scaledBitmap.height, 1f)
        scaledBitmap = bitmap.scale(
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1))
    }

    val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
    scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)

    val colors = QuantizerCelebi.quantize(pixels, 3).toList().sortedBy { it.second }.map { it.first }
    //val scored = Score.score(colors, 3, Color(-0xbd7a0c).toArgb(), true)
    return Hct.fromInt(colors.lastOrNull() ?: 0).let {
        Hct2(it.hue.toFloat(), it.chroma.toFloat(), it.tone.toFloat())
    }
}