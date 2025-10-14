// Note: Mainly lifted from androidx.emoji2.emojipicker
/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.futo.inputmethod.latin.uix.actions.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import org.futo.inputmethod.latin.R


class EmojiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) :
    View(context, attrs) {

    companion object {
        private const val EMOJI_DRAW_TEXT_SIZE_DP = 42
    }

    init {
        background = AppCompatResources.getDrawable(context, R.drawable.ripple_emoji_view)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    internal var willDrawVariantIndicator: Boolean = true
    private var textScale: Float = 1.0f

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            EMOJI_DRAW_TEXT_SIZE_DP.toFloat(),
            context.resources.displayMetrics
        )
    }

    fun setTextColor(color: Int) {
        textPaint.color = color
    }

    fun setTextSizeMultiplier(v: Float) {
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            EMOJI_DRAW_TEXT_SIZE_DP.toFloat() * v,
            context.resources.displayMetrics
        )
    }

    private val offscreenCanvasBitmap: Bitmap = with(textPaint.fontMetricsInt) {
        val size = bottom - top
        createBitmap(size, size)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec) - context.resources.getDimensionPixelSize(R.dimen.emoji_picker_emoji_view_padding),
            MeasureSpec.getSize(heightMeasureSpec) - context.resources.getDimensionPixelSize(R.dimen.emoji_picker_emoji_view_padding)
        )
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.run {
            val bmpW = offscreenCanvasBitmap.width.toFloat()
            val bmpH = offscreenCanvasBitmap.height.toFloat()

            val sX = width.toFloat() / bmpW
            val sY = height.toFloat() / bmpH

            val scale = minOf(sX, sY)

            val scaledW = (bmpW * scale / textScale).coerceAtMost(width.toFloat())
            val scaledH = (bmpH * scale).coerceAtMost(height.toFloat())

            val left = (width  - scaledW) / 2f
            val top  = (height - scaledH) / 2f

            drawBitmap(offscreenCanvasBitmap, null, RectF(left, top, left + scaledW, top + scaledH), null)
        }
    }

    var emoji: EmojiItem? = null
        set(value) {
            field = value

            offscreenCanvasBitmap.eraseColor(Color.TRANSPARENT)
            post {
                if (value != null) {
                    if (value == this.emoji) {
                        drawEmoji(
                            value.emoji,
                            drawVariantIndicator = willDrawVariantIndicator && value.skinTones
                        )
                        contentDescription = value.emoji
                    }
                    invalidate()
                } else {
                    //offscreenCanvasBitmap.eraseColor(Color.TRANSPARENT)
                }
            }
        }

    private fun drawEmoji(emoji: CharSequence, drawVariantIndicator: Boolean) {
        //offscreenCanvasBitmap.eraseColor(Color.TRANSPARENT)
        offscreenCanvasBitmap.applyCanvas {
            if (emoji is Spanned) {
                createStaticLayout(emoji, width).draw(this)
            } else {
                var textWidth = textPaint.measureText(emoji, 0, emoji.length)
                if(textWidth > width) {
                    scale(width / textWidth, 1.0f)
                    textScale = width / textWidth
                    textWidth = width.toFloat()
                } else {
                    scale(1.0f, 1.0f)
                    textScale = 1.0f
                }

                val fm = textPaint.fontMetrics
                val textHeight = fm.descent - fm.ascent
                val textCenterY = height / 2f
                val baselineY = textCenterY - textHeight / 2f - fm.ascent

                drawText(
                    emoji,
                    /* start = */ 0,
                    /* end = */ emoji.length,
                    /* x = */ (width - textWidth) / 2,
                    /* y = */ baselineY,
                    textPaint,
                )
            }

            if (drawVariantIndicator) {
                AppCompatResources.getDrawable(context, R.drawable.variant_availability_indicator)?.apply {
                    val canvasWidth = this@applyCanvas.width
                    val canvasHeight = this@applyCanvas.height
                    val indicatorWidth =
                        context.resources.getDimensionPixelSize(
                            R.dimen.variant_availability_indicator_width
                        )
                    val indicatorHeight =
                        context.resources.getDimensionPixelSize(
                            R.dimen.variant_availability_indicator_height
                        )
                    bounds = Rect(
                        canvasWidth - indicatorWidth,
                        canvasHeight - indicatorHeight,
                        canvasWidth,
                        canvasHeight
                    )
                }!!.draw(this)
            }

        }
    }

    @RequiresApi(23)
    internal object Api23Impl {
        fun createStaticLayout(emoji: Spanned, textPaint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(
                emoji, 0, emoji.length, textPaint, width
            ).apply {
                setAlignment(Layout.Alignment.ALIGN_CENTER)
                setLineSpacing(/* spacingAdd = */ 0f, /* spacingMult = */ 1f)
                setIncludePad(false)
            }.build()
    }

    private fun createStaticLayout(emoji: Spanned, width: Int): StaticLayout {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Api23Impl.createStaticLayout(emoji, textPaint, width)
        } else {
            @Suppress("DEPRECATION")
            return StaticLayout(
                emoji,
                textPaint,
                width,
                Layout.Alignment.ALIGN_CENTER,
                /* spacingmult = */ 1f,
                /* spacingadd = */ 0f,
                /* includepad = */ false,
            )
        }
    }
}