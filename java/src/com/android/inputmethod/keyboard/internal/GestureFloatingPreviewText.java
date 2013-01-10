/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;

import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.CoordinateUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResizableIntArray;
import com.android.inputmethod.latin.SuggestedWords;

/**
 * The class for single gesture preview text. The class for multiple gesture preview text will be
 * derived from it.
 */
public class GestureFloatingPreviewText extends AbstractDrawingPreview {
    private static final class GesturePreviewTextParams {
        public final int mGesturePreviewTextSize;
        public final int mGesturePreviewTextColor;
        public final int mGesturePreviewTextOffset;
        public final int mGesturePreviewTextHeight;
        public final int mGesturePreviewColor;
        public final float mGesturePreviewHorizontalPadding;
        public final float mGesturePreviewVerticalPadding;
        public final float mGesturePreviewRoundRadius;
        public final Paint mTextPaint;

        private static final char[] TEXT_HEIGHT_REFERENCE_CHAR = { 'M' };

        public GesturePreviewTextParams(final TypedArray keyboardViewAttr) {
            mGesturePreviewTextSize = keyboardViewAttr.getDimensionPixelSize(
                    R.styleable.KeyboardView_gestureFloatingPreviewTextSize, 0);
            mGesturePreviewTextColor = keyboardViewAttr.getColor(
                    R.styleable.KeyboardView_gestureFloatingPreviewTextColor, 0);
            mGesturePreviewTextOffset = keyboardViewAttr.getDimensionPixelOffset(
                    R.styleable.KeyboardView_gestureFloatingPreviewTextOffset, 0);
            mGesturePreviewColor = keyboardViewAttr.getColor(
                    R.styleable.KeyboardView_gestureFloatingPreviewColor, 0);
            mGesturePreviewHorizontalPadding = keyboardViewAttr.getDimension(
                    R.styleable.KeyboardView_gestureFloatingPreviewHorizontalPadding, 0.0f);
            mGesturePreviewVerticalPadding = keyboardViewAttr.getDimension(
                    R.styleable.KeyboardView_gestureFloatingPreviewVerticalPadding, 0.0f);
            mGesturePreviewRoundRadius = keyboardViewAttr.getDimension(
                    R.styleable.KeyboardView_gestureFloatingPreviewRoundRadius, 0.0f);

            final Paint textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Align.CENTER);
            textPaint.setTextSize(mGesturePreviewTextSize);
            mTextPaint = textPaint;
            final Rect textRect = new Rect();
            textPaint.getTextBounds(TEXT_HEIGHT_REFERENCE_CHAR, 0, 1, textRect);
            mGesturePreviewTextHeight = textRect.height();
        }
    }

    protected final GesturePreviewTextParams mParams;
    protected int mPreviewWordNum;
    protected final RectF mGesturePreviewRectangle = new RectF();
    protected int mHighlightedWordIndex;

    private static final int PREVIEW_TEXT_ARRAY_CAPACITY = 10;
    // These variables store the positions of preview words. In multi-preview mode, the gesture
    // floating preview at most shows PREVIEW_TEXT_ARRAY_CAPACITY words.
    protected final ResizableIntArray mPreviewTextXArray = new ResizableIntArray(
            PREVIEW_TEXT_ARRAY_CAPACITY);
    protected final ResizableIntArray mPreviewTextYArray = new ResizableIntArray(
            PREVIEW_TEXT_ARRAY_CAPACITY);

    protected SuggestedWords mSuggestedWords = SuggestedWords.EMPTY;
    protected final Context mContext;
    public final int[] mLastPointerCoords = CoordinateUtils.newInstance();

    public GestureFloatingPreviewText(final TypedArray typedArray, final Context context) {
        mParams = new GesturePreviewTextParams(typedArray);
        mHighlightedWordIndex = 0;
        mContext = context;
    }

    public void setSuggetedWords(final SuggestedWords suggestedWords) {
        mSuggestedWords = suggestedWords;
        updatePreviewPosition();
    }

    protected void drawText(final Canvas canvas, final String text, final float textX,
            final float textY, final int color) {
        final Paint paint = mParams.mTextPaint;
        paint.setColor(color);
        canvas.drawText(text, textX, textY, paint);
    }

    @Override
    public void setPreviewPosition(final PointerTracker pt) {
        pt.getLastCoordinates(mLastPointerCoords);
        updatePreviewPosition();
    }

    /**
     * Draws gesture preview text
     * @param canvas The canvas where preview text is drawn.
     */
    @Override
    public void onDraw(final Canvas canvas) {
        if (!isPreviewEnabled() || mSuggestedWords.isEmpty()
                || TextUtils.isEmpty(mSuggestedWords.getWord(0))) {
            return;
        }
        final Paint paint = mParams.mTextPaint;
        paint.setColor(mParams.mGesturePreviewColor);
        final float round = mParams.mGesturePreviewRoundRadius;
        canvas.drawRoundRect(mGesturePreviewRectangle, round, round, paint);
        final String text = mSuggestedWords.getWord(0);
        final int textX = mPreviewTextXArray.get(0);
        final int textY = mPreviewTextYArray.get(0);
        drawText(canvas, text, textX, textY, mParams.mGesturePreviewTextColor);
    }

    /**
     * Updates gesture preview text position based on mLastPointerCoords.
     */
    protected void updatePreviewPosition() {
        if (mSuggestedWords.isEmpty() || TextUtils.isEmpty(mSuggestedWords.getWord(0))) {
            return;
        }
        final String text = mSuggestedWords.getWord(0);

        final Paint paint = mParams.mTextPaint;
        final RectF rectangle = mGesturePreviewRectangle;

        final int textHeight = mParams.mGesturePreviewTextHeight;
        final float textWidth = paint.measureText(text);
        final float hPad = mParams.mGesturePreviewHorizontalPadding;
        final float vPad = mParams.mGesturePreviewVerticalPadding;
        final float rectWidth = textWidth + hPad * 2.0f;
        final float rectHeight = textHeight + vPad * 2.0f;

        final int displayWidth = mContext.getResources().getDisplayMetrics().widthPixels;
        final float rectX = Math.min(
                Math.max(CoordinateUtils.x(mLastPointerCoords) - rectWidth / 2.0f, 0.0f),
                displayWidth - rectWidth);
        final float rectY = CoordinateUtils.y(mLastPointerCoords)
                - mParams.mGesturePreviewTextOffset - rectHeight;
        rectangle.set(rectX, rectY, rectX + rectWidth, rectY + rectHeight);

        final int textX = (int)(rectX + hPad + textWidth / 2.0f);
        final int textY = (int)(rectY + vPad) + textHeight;
        mPreviewTextXArray.add(0, textX);
        mPreviewTextYArray.add(0, textY);
    }
}
