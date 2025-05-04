package org.futo.inputmethod.latin.uix.actions.fonttyper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withScale
import androidx.core.graphics.withSkew
import androidx.core.graphics.withTranslation
import org.futo.inputmethod.latin.R
import kotlin.math.roundToInt

object RainbowRenderer: WordImageRenderer() {
    override val name: Int
        get() = R.string.action_fonttyper_preset_title_rainbow

    override fun renderLine(
        context: Context,
        text: String
    ): LineRenderResult? {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }


        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val bitmap = createBitmap(bounds.width() + 128, bounds.height()*3/2+ 64)
        val canvas = Canvas(bitmap)


        val xOffs = (bounds.width().toFloat() + 128f) / 2.0f
        val yOffs = (bounds.height().toFloat() + 64f) / 2.0f + 10f


        canvas.withScale(1.0f, 1.5f) {
            canvas.withTranslation(xOffs, yOffs) {
                withSkew(2.0f, 0.0f) {
                    withScale(1.0f, 0.5f) {
                        drawText(text, 0.0f, 0.0f, paint.apply {
                            color = 0x55000000.toInt()
                        })
                    }
                }
            }

            canvas.drawText(text, xOffs, yOffs, paint.apply {
                color = Color.WHITE
                shader = LinearGradient(
                    64f, 0f,
                    bounds.width().toFloat() + 64f, 0f,
                    intArrayOf(
                        Color.MAGENTA,
                        Color.RED,
                        Color.YELLOW,
                        Color.GREEN,
                        Color.BLUE,
                        Color.MAGENTA),
                    null,
                    Shader.TileMode.CLAMP
                )
            })
        }

        return LineRenderResult(
            bitmap = bitmap,
            lineHeight = (64 * 1.5f).roundToInt(),
            startXOffset = 0,//xOffs.roundToInt(),
            startYOffset = yOffs.roundToInt()
        )
    }

}

@Preview(showBackground = true)
@Composable
private fun PreviewRenderer() {
    val context = LocalContext.current
    val renderer = RainbowRenderer
    val image = remember {
        renderer.render(context, context.getString(renderer.name))!!.asImageBitmap()
    }
    Image(image, contentDescription = null)
}