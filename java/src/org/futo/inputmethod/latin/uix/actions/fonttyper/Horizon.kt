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
import org.futo.inputmethod.latin.R
import kotlin.math.roundToInt

object HorizonRenderer: WordImageRenderer() {
    override val name: Int
        get() = R.string.action_fonttyper_preset_title_horizon

    override fun renderLine(
        context: Context,
        text: String
    ): LineRenderResult? {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
        }


        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val bitmap = createBitmap(bounds.width() + 32, bounds.height() + 64)
        val canvas = Canvas(bitmap)


        val xOffs = (bounds.width().toFloat() + 32f) / 2.0f
        val yOffs = (bounds.height().toFloat() + 64f) / 2.0f + 10f

        for(i in 0 until 16) {
            val xScale = 1.0f - (i / 16f) * ((1.0f)-(bounds.width().toFloat() / (bounds.width().toFloat() + 16.0f)))
            canvas.withScale(xScale, 1.0f) {
                drawText(text, xOffs + i, yOffs + i/2.0f + 1, paint.apply {
                    color = 0xFF222222.toInt()
                })
            }
        }

        canvas.drawText(text, xOffs, yOffs, paint.apply {
            color = Color.WHITE
            shader = LinearGradient(
                0f, yOffs - bounds.height().toFloat()/1.5f,
                0f, yOffs,// + bounds.height().toFloat()/2.0f,
                intArrayOf(
                    0xFF7286A7.toInt(),
                    0xFF7286A7.toInt(),
                    0xFFFFFFFF.toInt(),
                    0xFF812F30.toInt(),
                    0xFFFFFFFF.toInt()
                ),
                floatArrayOf(
                    0.0f,
                    0.13f,
                    0.5f,
                    0.56f,
                    1.0f
                ),
                Shader.TileMode.CLAMP
            )
        })

        return LineRenderResult(
            bitmap,
            64,
            0,
            yOffs.roundToInt()
        )
    }

}

@Preview(showBackground = true)
@Composable
private fun PreviewRenderer() {
    val context = LocalContext.current
    val renderer = HorizonRenderer
    val image = remember {
        renderer.render(context, context.getString(renderer.name))!!.asImageBitmap()
    }
    Image(image, contentDescription = null)
}