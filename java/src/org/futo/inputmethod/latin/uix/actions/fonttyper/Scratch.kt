package org.futo.inputmethod.latin.uix.actions.fonttyper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import org.futo.inputmethod.latin.R

object ScratchRenderer: WordImageRenderer() {
    override val font: ((Context) -> Typeface)?
        get() = {
            Typeface.createFromAsset(it.assets, "fonts/Scratch.ttf")
        }

    override val name: Int
        get() = R.string.action_fonttyper_preset_title_scratch

    override val backgroundColor: Int
        get() = Color.BLACK

    override fun renderLine(
        context: Context,
        text: String
    ): LineRenderResult? {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = androidx.compose.ui.graphics.Color(0xFFFF9800).toArgb()
            textSize = 64f
            textAlign = Paint.Align.LEFT
            typeface = font!!(context)
        }

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val padding = 48
        val bitmap = createBitmap(bounds.width() + padding, bounds.height() + padding)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawText(text, 16f, bounds.height().toFloat() + 16f, paint)

        return LineRenderResult(
            bitmap = bitmap,
            lineHeight = 64,
            startXOffset = 5,
            startYOffset = bounds.height() + 5
        )
    }
}

@Preview
@Composable
private fun PreviewRenderer() {
    val context = LocalContext.current
    val renderer = ScratchRenderer
    val image = remember {
        renderer.render(context, context.getString(renderer.name))!!.asImageBitmap()
    }
    Image(image, contentDescription = null)
}