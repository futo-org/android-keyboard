package org.futo.inputmethod.latin.uix.actions.clipboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import kotlin.math.min

object ClipboardUtil {
    fun thumbnailForName(name: String): String
            = "$name.thumb.jpg"

    fun thumbnailFor(imageFile: File): File
            = File(imageFile.parent, thumbnailForName(imageFile.name))

    fun generateThumbnail(imageFile: File): File? {
        val thumbFile = thumbnailFor(imageFile)
        if (thumbFile.exists()) return thumbFile

        var bitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)

            val (w, h) = options.outWidth to options.outHeight
            if (w <= 0 || h <= 0) return null

            val cropSize = min(w, h)
            val cropX = (w - cropSize) / 2
            val cropY = (h - cropSize) / 2

            val maxSide = 384
            var sample = 1
            while (cropSize / sample > maxSide) sample *= 2

            options.inJustDecodeBounds = false
            options.inSampleSize = sample

            bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options) ?: return null

            val scaledCropX = cropX / sample
            val scaledCropY = cropY / sample
            val scaledCropSize = cropSize / sample

            croppedBitmap = if (scaledCropSize == bitmap.width && scaledCropSize == bitmap.height) {
                // Already square, no crop needed
                bitmap
            } else {
                Bitmap.createBitmap(
                    bitmap,
                    scaledCropX.coerceIn(0, bitmap.width - scaledCropSize),
                    scaledCropY.coerceIn(0, bitmap.height - scaledCropSize),
                    scaledCropSize.coerceAtMost(bitmap.width),
                    scaledCropSize.coerceAtMost(bitmap.height)
                )
            }

            val finalBmp = if (croppedBitmap.width != maxSide || croppedBitmap.height != maxSide) {
                croppedBitmap.scale(maxSide, maxSide)
            } else {
                croppedBitmap
            }

            FileOutputStream(thumbFile).use { out ->
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            return thumbFile
        } catch (e: Exception) {
            thumbFile.delete()
            return null
        } finally {
            if (croppedBitmap !== bitmap) {
                croppedBitmap?.recycle()
            }
            bitmap?.recycle()
        }
    }

    fun generateCheckerboardBitmap(
        width: Int = 256,
        height: Int = 256,
        squares: Int = 8
    ): ImageBitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val squareSize = width / squares

        val lightPaint = Paint().apply { color = android.graphics.Color.LTGRAY }
        val darkPaint = Paint().apply { color = android.graphics.Color.DKGRAY }

        for (row in 0 until squares) {
            for (col in 0 until squares) {
                val paint = if ((row + col) % 2 == 0) lightPaint else darkPaint
                canvas.drawRect(
                    col * squareSize.toFloat(),
                    row * squareSize.toFloat(),
                    (col + 1) * squareSize.toFloat(),
                    (row + 1) * squareSize.toFloat(),
                    paint
                )
            }
        }

        return bitmap.asImageBitmap()
    }

    fun generateTestPatternBitmap(
        width: Int = 256,
        height: Int = 256,
        gridSize: Int = 64,
        gradientStart: Int = android.graphics.Color.MAGENTA,
        gradientEnd: Int = android.graphics.Color.CYAN,
        gridColor: Int = android.graphics.Color.WHITE
    ): ImageBitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        // 1. Gradient background
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                gradientStart, gradientEnd,
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Grid lines
        val gridPaint = Paint().apply {
            color = gridColor
            alpha = 120 // Semi-transparent
            strokeWidth = 2f
            isAntiAlias = true
        }

        // Vertical lines
        for (x in 0..width step gridSize) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
        }

        // Horizontal lines
        for (y in 0..height step gridSize) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
        }

        // 3. Test pattern markers
        // Center crosshair
        val markerPaint = Paint().apply {
            color = android.graphics.Color.YELLOW
            strokeWidth = 4f
            alpha = 200
        }
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawLine(cx - 40, cy, cx + 40, cy, markerPaint)
        canvas.drawLine(cx, cy - 40, cx, cy + 40, markerPaint)

        // Corner markers
        val cornerSize = 40f
        canvas.drawLine(0f, 0f, cornerSize, 0f, markerPaint)
        canvas.drawLine(0f, 0f, 0f, cornerSize, markerPaint)
        canvas.drawLine(width.toFloat(), 0f, width - cornerSize, 0f, markerPaint)
        canvas.drawLine(width.toFloat(), 0f, width.toFloat(), cornerSize, markerPaint)

        // Border
        val borderPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(4f, 4f, width - 4f, height - 4f, borderPaint)

        return bitmap.asImageBitmap()
    }
}