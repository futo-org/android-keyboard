package org.futo.inputmethod.latin.uix.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.NinePatch
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.NinePatchDrawable
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import org.futo.inputmethod.latin.uix.theme.KeyBackground
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

// Source - https://stackoverflow.com/a/37785598
// Posted by Diljeet, modified by community. See post 'Timeline' for change history
// Retrieved 2025-11-20, License - CC BY-SA 3.0
class NinePatchBuilder {
    var width: Int
    var height: Int
    var bitmap: Bitmap? = null
    var resources: Resources? = null
    private val xRegions = ArrayList<Int>()
    private val yRegions = ArrayList<Int>()

    val padding = Rect(0, 0, 0, 0)

    constructor(resources: Resources?, bitmap: Bitmap) {
        width = bitmap.getWidth()
        height = bitmap.getHeight()
        this.bitmap = bitmap
        this.resources = resources
    }

    constructor(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun addXRegionPoints(x1: Int, x2: Int): NinePatchBuilder {
        var x1 = x1
        var x2 = x2

        if(x1 == x2) {
            if(x1 < width / 2) x2 += 1 else x1 -= 1
        }

        xRegions.add(x1)
        xRegions.add(x2)
        return this
    }

    fun addYRegionPoints(y1: Int, y2: Int): NinePatchBuilder {
        var y1 = y1
        var y2 = y2

        if(y1 == y2) {
            if(y1 < height / 2) y2 += 1 else y1 -= 1
        }

        yRegions.add(y1)
        yRegions.add(y2)
        return this
    }

    fun setXPadding(startX: Int, endX: Int) {
        padding.left = startX
        padding.right = width - endX
    }

    fun setYPadding(startY: Int, endY: Int) {
        padding.top = startY
        padding.bottom = height - endY
    }

    fun buildChunk(): ByteArray? {
        //Log.d("NinePatchBuilder", "Building: ${xRegions.joinToString { it.toString() }} : ${yRegions.joinToString { it.toString() }}")
        if (xRegions.isEmpty()) {
            xRegions.add(0)
            xRegions.add(width)
        }
        if (yRegions.isEmpty()) {
            yRegions.add(0)
            yRegions.add(height)
        }
        val NO_COLOR = 1 //0x00000001;
        val COLOR_SIZE = 9 //could change, may be 2 or 6 or 15 - but has no effect on output
        val arraySize = 1 + 2 + 4 + 1 + xRegions.size + yRegions.size + COLOR_SIZE
        val byteBuffer = ByteBuffer.allocate(arraySize * 4).order(ByteOrder.nativeOrder())
        byteBuffer.put(1.toByte()) //was translated
        byteBuffer.put(xRegions.size.toByte()) //divisions x
        byteBuffer.put(yRegions.size.toByte()) //divisions y
        byteBuffer.put(COLOR_SIZE.toByte()) //color size

        //skip
        byteBuffer.putInt(0)
        byteBuffer.putInt(0)

        //padding -- left right top bottom
        Log.d("NinePatchBuilder", "Placing padding ${padding.left} ${padding.right} ${padding.top} ${padding.bottom}")
        byteBuffer.putInt(padding.left)
        byteBuffer.putInt(padding.right)
        byteBuffer.putInt(padding.top)
        byteBuffer.putInt(padding.bottom)

        //skip
        byteBuffer.putInt(0)

        for (rx in xRegions) byteBuffer.putInt(rx) // regions left right left right ...

        for (ry in yRegions) byteBuffer.putInt(ry) // regions top bottom top bottom ...


        for (i in 0..<COLOR_SIZE) byteBuffer.putInt(NO_COLOR)

        return byteBuffer.array()
    }

    fun buildNinePatch(): NinePatch? {
        val chunk = buildChunk()
        if (bitmap != null) return NinePatch(bitmap, chunk, null)
        return null
    }

    fun build(): NinePatchDrawable? {
        val ninePatch = buildNinePatch()
        if (ninePatch != null) return NinePatchDrawable(resources, ninePatch)
        return null
    }
}

fun tintBitmap(bitmap: Bitmap, color: Int): Bitmap {
    // white tint won't have any change on bitmap
    if(color == Color.WHITE) return bitmap

    val tintedBitmap = createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)

    val canvas = Canvas(tintedBitmap)
    val paint = Paint()
    paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)

    return tintedBitmap
}

fun createNinePatchDrawable(
    bitmap: Bitmap, scale: Float,
    res: Resources,
    foregroundColor: Int?,
    backgroundTint: Int,
    xRegions: List<Pair<Int, Int>>,
    yRegions: List<Pair<Int, Int>>,
    padding: Rect = Rect(0, 0, 0, 0),
    gap: RectF = RectF(1.0f, 1.0f, 1.0f, 1.0f),
    removeMargin: Int = 0,
): KeyBackground? {
    val w = bitmap.width
    val h = bitmap.height
    val m = removeMargin
    val m2 = removeMargin * 2

    fun sc(v: Int) = (v * scale).roundToInt()

    val tintedBitmap = tintBitmap(bitmap, backgroundTint)

    val content = Bitmap.createBitmap(tintedBitmap, m, m, w - m2, h - m2).scale(sc(w - m2), sc(h - m2))

    val builder = NinePatchBuilder(res, content)

    xRegions.forEach { builder.addXRegionPoints(it.first, it.second) }
    yRegions.forEach { builder.addYRegionPoints(it.first, it.second) }
    builder.setXPadding(padding.left, padding.right)
    builder.setYPadding(padding.top, padding.bottom)

    return builder.build()?.let {
        KeyBackground(
            padding = builder.padding,
            gap = gap,
            foregroundColor = foregroundColor,
            background = it
        )
    }
}