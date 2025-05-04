package org.futo.inputmethod.latin.uix.actions.fonttyper

import android.content.Context
import android.graphics.Canvas
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
import org.futo.inputmethod.latin.R

object SuperheroRenderer : WordImageRenderer() {
    override val name: Int
        get() = R.string.action_fonttyper_preset_title_superhero

    override fun renderLine(
        context: Context,
        text: String
    ): LineRenderResult? {
        val shadowColor = 0xFF802601.toInt()
        val bottomColor = 0xFFFA5F05.toInt()
        val topColor = 0xFFFEDD04.toInt()

        var textSizePx = 128f
        var extrusionDepth = 25
        if(text.length > 25) {
            textSizePx = 64f
            extrusionDepth = 12
        }

        val skewX = 0.0f//-0.3f
        val skewY = -0.15f
        val skewFactor = Math.abs(skewY)
        val padding = 60f

        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = Typeface.createFromAsset(context.assets, "fonts/Anton-Regular.ttf")
        }

        val textBounds = Rect()
        measurePaint.getTextBounds(text, 0, text.length, textBounds)
        val textWidth = textBounds.width().toFloat()
        val textHeight = textBounds.height().toFloat()
        val skewedHeight = textWidth * skewFactor

        val bmpWidth = (textWidth + padding * 2).toInt()
        val totalHeight = (textHeight + skewedHeight + padding * 2).toInt()
        val bitmap = createBitmap(bmpWidth, totalHeight)
        val canvas = Canvas(bitmap)

        val originX = padding
        val originY = padding + textHeight
        canvas.translate(originX, originY + skewedHeight.toFloat() / 1f)
        canvas.skew(skewX, skewY)

        val extrudePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = measurePaint.typeface
            style = Paint.Style.FILL
        }

        for (i in extrusionDepth downTo 1) {
            extrudePaint.shader = null
            extrudePaint.color = shadowColor
            canvas.drawText(text, i.toFloat(), i.toFloat(), extrudePaint)
        }

        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = measurePaint.typeface
            style = Paint.Style.FILL

            shader = LinearGradient(
                0f, -textHeight,
                0f, 0f,
                intArrayOf(topColor, topColor, bottomColor),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawText(text, 0f, 0f, topPaint)

        return LineRenderResult(
            bitmap = bitmap,
            lineHeight = textSizePx.toInt(),
            startXOffset = originX.toInt(),
            startYOffset = (originY + skewedHeight).toInt()
        )
    }
}

@Preview
@Composable
private fun PreviewRenderer() {
    val context = LocalContext.current
    val renderer = SuperheroRenderer
    val image = remember {
        renderer.render(context, context.getString(renderer.name))!!.asImageBitmap()
    }
    Image(image, contentDescription = null)
}