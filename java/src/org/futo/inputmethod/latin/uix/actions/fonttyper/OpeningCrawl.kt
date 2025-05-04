package org.futo.inputmethod.latin.uix.actions.fonttyper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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


object OpeningCrawlRenderer : WordImageRenderer() {
    override val name: Int
        get() = R.string.action_fonttyper_preset_title_opening_crawl

    override fun renderLine(
        context: Context,
        text: String
    ): LineRenderResult? {
        return renderMultiLine(context, text)?.let {
            LineRenderResult(it, 0, 0, 0)
        }
    }

    override val backgroundColor: Int
        get() = Color.BLACK

    override fun renderMultiLine(context: Context, text: String): Bitmap? {
        val textColor = 0xFFFFCC00.toInt()
        val lines = text.lines()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            color = textColor
        }

        val lineHeight = 64.0f * 1.2f
        val lineYOffsets = lines.mapIndexed { i, v -> (i * lineHeight).roundToInt() }
        val bounds = Rect()
        val lineBounds = lines.map {
            Rect().apply { paint.getTextBounds(it, 0, it.length, this) }
        }
        bounds.right = lineBounds.maxOf { it.right }
        bounds.bottom = lineBounds.mapIndexed { i, v -> v.bottom + lineYOffsets[i] }.max()

        val bitmap = createBitmap(bounds.width() + 128, bounds.height() + 80)
        val canvas = Canvas(bitmap)

        val camera: Camera = Camera()
        val m = Matrix()
        camera.save()
        camera.rotateX(45.0f)
        camera.getMatrix(m)
        camera.restore()
        m.preTranslate(-bitmap.width/2.0f, -bounds.height().toFloat())
        m.postTranslate(bitmap.width/2.0f, bounds.height().toFloat())
        canvas.concat(m)

        lines.forEachIndexed { i, line ->
            val xOffs = bitmap.width / 2.0f
            val yOffs = lineYOffsets[i] + lineHeight

            canvas.drawText(line, xOffs, yOffs, paint)
        }

        return bitmap
    }
}

@Preview
@Composable
private fun PreviewRenderer() {
    val context = LocalContext.current
    val renderer = OpeningCrawlRenderer
    val image = remember {
        renderer.render(context, "Opening Crawl\n\nThe galaxy is in grave danger!")!!.asImageBitmap()
    }
    Image(image, contentDescription = null)
}