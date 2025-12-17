package org.futo.inputmethod.latin.uix.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.NinePatch
import android.graphics.Rect
import android.graphics.drawable.NinePatchDrawable
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.get
import kotlin.math.roundToInt
import androidx.core.graphics.scale

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



/**
 * Reads the 1-pixel 9-patch border and returns a NinePatchDrawable.
 * The supplied bitmap must still contain the 1-pixel frame.
 * Input bitmap is assumed to be 640dp and gets scaled according to resources screen density
 */
fun Bitmap.toNinePatchDrawable(res: Resources): Pair<Rect, NinePatchDrawable>? {
    val w = width
    val h = height

    val scale = res.displayMetrics.densityDpi / 640f
    fun sc(v: Int) = (v * scale).roundToInt()

    val content = Bitmap.createBitmap(this, 1, 1, w - 2, h - 2).scale(sc(w - 2), sc(h - 2))

    val builder = NinePatchBuilder(res, content)

    val black = 0xFF000000.toInt()

    // top line -> horizontal stretch
    var stretchStart = -1
    for (x in 1 until w - 1) {
        val c = this[x, 0]
        val isBlack = c == black
        if (isBlack && stretchStart == -1) stretchStart = x - 1
        if (!isBlack && stretchStart != -1) {
            builder.addXRegionPoints(sc(stretchStart), sc(x - 1))
            stretchStart = -1
        }
    }
    if (stretchStart != -1) builder.addXRegionPoints(sc(stretchStart), sc(w - 2))

    // left line -> vertical stretch
    stretchStart = -1
    for (y in 1 until h - 1) {
        val c = this[0, y]
        val isBlack = c == black
        if (isBlack && stretchStart == -1) stretchStart = y - 1
        if (!isBlack && stretchStart != -1) {
            builder.addYRegionPoints(sc(stretchStart), sc(y - 1))
            stretchStart = -1
        }
    }
    if (stretchStart != -1) builder.addYRegionPoints(sc(stretchStart), sc(h - 2))

    var paddingStart = -1
    for (x in 1 until w) {
        val c = this[x, h - 1]
        val isBlack = c == black
        if (isBlack && paddingStart == -1) paddingStart = x - 1
        if (!isBlack && paddingStart != -1) {
            builder.setXPadding(sc(paddingStart), sc(x - 1))
            paddingStart = -1
            break
        }
    }
    if (paddingStart != -1) builder.setXPadding(sc(paddingStart), sc(w - 1))

    paddingStart = -1
    for (y in 1 until h) {
        val c = this[w - 1, y]
        val isBlack = c == black
        if (isBlack && paddingStart == -1) paddingStart = y - 1
        if (!isBlack && paddingStart != -1) {
            builder.setYPadding(sc(paddingStart), sc(y - 1))
            paddingStart = -1
            break
        }
    }
    if (paddingStart != -1) builder.setYPadding(sc(paddingStart), sc(h - 1))

    return builder.build()?.let { builder.padding to it }
}