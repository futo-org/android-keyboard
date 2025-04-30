package org.futo.inputmethod.latin.uix.actions.fonttyper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import org.futo.inputmethod.latin.R
import kotlin.math.roundToInt

object CandyRenderer: WordImageRenderer() {
    override val name: Int
        get() = R.string.action_fonttyper_preset_title_candy

    override fun renderLine(
        context: Context,
        text: String
    ): LineRenderResult? {
        val strokeDark = 0xfff56d7d.toInt()
        val strokeLight = 0xfff78e95.toInt()
        val textDark = 0xfff8e8dd.toInt()
        val textLight = 0xffffffff.toInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("cursive", Typeface.BOLD)
            strokeWidth = 12f
            style = Paint.Style.STROKE
        }


        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val bitmap = createBitmap(bounds.width() + 48, bounds.height() + 64)
        val canvas = Canvas(bitmap)


        val xOffs = (bounds.width().toFloat() + 48f) / 2.0f
        val yOffs = (bounds.height().toFloat() + 64f) / 2.0f + 10f

        paint.apply {
            color = strokeDark
            strokeWidth = 12f
            style = Paint.Style.STROKE
        }
        for(i in 0 until 8) {
            canvas.drawText(text, xOffs + i, yOffs + i, paint)
        }

        canvas.drawText(text, xOffs, yOffs, paint.apply { color = strokeLight })

        paint.apply {
            color = strokeDark
            strokeWidth = 0f
            style = Paint.Style.FILL
        }
        for(i in 0 until 8) {
            canvas.drawText(text, xOffs + i, yOffs + i, paint)
        }

        canvas.drawText(text, xOffs - 1, yOffs - 1, paint.apply { color = textLight })
        canvas.drawText(text, xOffs, yOffs, paint.apply { color = textDark })

        return LineRenderResult(
            bitmap = bitmap,
            lineHeight = 64,
            startXOffset = 0,
            startYOffset = yOffs.roundToInt()
        )
    }

}

@Preview
@Composable
private fun PreviewRenderer() {
    val context = LocalContext.current
    val renderer = CandyRenderer
    val image = remember {
        renderer.render(context, context.getString(renderer.name))!!.asImageBitmap()
    }
    Image(image, contentDescription = null)
}