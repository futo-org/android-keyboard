/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.text.TextUtils;
import android.util.SparseArray;
import android.widget.RelativeLayout;

import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.R;

public class PreviewPlacerView extends RelativeLayout {
    // TODO: Move these parameters to attributes of {@link KeyboardView}.
    private final static int GESTURE_DRAWING_COLOR = 0xff33b5e5;
    private static final int GESTURE_PREVIEW_TEXT_COLOR = Color.WHITE;
    private static final int GESTURE_PREVIEW_TEXT_SHADING_COLOR = 0xff33b5e5;
    private static final int GESTURE_PREVIEW_TEXT_SHADOW_COLOR = 0xff252525;
    private static final int GESTURE_PREVIEW_CONNECTOR_COLOR = Color.WHITE;

    private final Paint mGesturePaint;
    private final int mGesturePreviewTraileWidth;
    private final Paint mTextPaint;
    private final int mGesturePreviewTextOffset;
    private final int mGesturePreviewTextShadowBorder;
    private final int mGesturePreviewTextShadingBorder;
    private final int mGesturePreviewTextConnectorWidth;

    private int mXOrigin;
    private int mYOrigin;

    private final SparseArray<PointerTracker> mPointers = new SparseArray<PointerTracker>();

    private String mGesturePreviewText;
    private boolean mDrawsGesturePreviewTrail;
    private boolean mDrawsGestureFloatingPreviewText;

    public PreviewPlacerView(Context context) {
        super(context);
        setWillNotDraw(false);

        final Resources res = getResources();
        // TODO: Move these parameters to attributes of {@link KeyboardView}.
        mGesturePreviewTraileWidth = res.getDimensionPixelSize(
                R.dimen.gesture_preview_trail_width);
        final int textSize = res.getDimensionPixelSize(R.dimen.gesture_preview_text_size);
        mGesturePreviewTextOffset = res.getDimensionPixelSize(
                R.dimen.gesture_preview_text_offset);
        mGesturePreviewTextShadowBorder = res.getDimensionPixelOffset(
                R.dimen.gesture_preview_text_shadow_border);
        mGesturePreviewTextShadingBorder = res.getDimensionPixelOffset(
                R.dimen.gesture_preview_text_shading_border);
        mGesturePreviewTextConnectorWidth = res.getDimensionPixelOffset(
                R.dimen.gesture_preview_text_connector_width);

        mGesturePaint = new Paint();
        mGesturePaint.setAntiAlias(true);
        mGesturePaint.setStyle(Paint.Style.STROKE);
        mGesturePaint.setStrokeJoin(Paint.Join.ROUND);
        mGesturePaint.setColor(GESTURE_DRAWING_COLOR);
        mGesturePaint.setStrokeWidth(mGesturePreviewTraileWidth);

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setStrokeJoin(Paint.Join.ROUND);
        mTextPaint.setTextAlign(Align.CENTER);
        mTextPaint.setTextSize(textSize);
    }

    public void setOrigin(int x, int y) {
        mXOrigin = x;
        mYOrigin = y;
    }

    public void setGesturePreviewMode(boolean drawsGesturePreviewTrail,
            boolean drawsGestureFloatingPreviewText) {
        mDrawsGesturePreviewTrail = drawsGesturePreviewTrail;
        mDrawsGestureFloatingPreviewText = drawsGestureFloatingPreviewText;
    }

    public void invalidatePointer(PointerTracker tracker) {
        synchronized (mPointers) {
            mPointers.put(tracker.mPointerId, tracker);
            // TODO: Should narrow the invalidate region.
            invalidate();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        synchronized (mPointers) {
            canvas.translate(mXOrigin, mYOrigin);
            final int trackerCount = mPointers.size();
            boolean hasDrawnFloatingPreviewText = false;
            for (int index = 0; index < trackerCount; index++) {
                final PointerTracker tracker = mPointers.valueAt(index);
                if (mDrawsGesturePreviewTrail) {
                    tracker.drawGestureTrail(canvas, mGesturePaint);
                }
                // TODO: Figure out more cleaner way to draw gesture preview text.
                if (mDrawsGestureFloatingPreviewText && !hasDrawnFloatingPreviewText) {
                    drawGesturePreviewText(canvas, tracker, mGesturePreviewText);
                    hasDrawnFloatingPreviewText = true;
                }
            }
            canvas.translate(-mXOrigin, -mYOrigin);
        }
    }

    public void setGesturePreviewText(String gesturePreviewText) {
        mGesturePreviewText = gesturePreviewText;
        invalidate();
    }

    private void drawGesturePreviewText(Canvas canvas, PointerTracker tracker,
            String gesturePreviewText) {
        if (TextUtils.isEmpty(gesturePreviewText)) {
            return;
        }

        final Paint paint = mTextPaint;
        final int lastX = tracker.getLastX();
        final int lastY = tracker.getLastY();
        final int textSize = (int)paint.getTextSize();
        final int canvasWidth = canvas.getWidth();

        final int halfTextWidth = (int)paint.measureText(gesturePreviewText) / 2 + textSize;
        final int textX = Math.min(Math.max(lastX, halfTextWidth), canvasWidth - halfTextWidth);

        int textY = Math.max(-textSize, lastY - mGesturePreviewTextOffset);
        if (textY < 0) {
            // Paint black text shadow if preview extends above keyboard region.
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setColor(GESTURE_PREVIEW_TEXT_SHADOW_COLOR);
            paint.setStrokeWidth(mGesturePreviewTextShadowBorder);
            canvas.drawText(gesturePreviewText, textX, textY, paint);
        }

        // Paint the vertical line connecting the touch point to the preview text.
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(GESTURE_PREVIEW_CONNECTOR_COLOR);
        paint.setStrokeWidth(mGesturePreviewTextConnectorWidth);
        final int lineTopY = textY - textSize / 4;
        canvas.drawLine(lastX, lastY, lastX, lineTopY, paint);
        if (lastX != textX) {
            // Paint the horizontal line connection the touch point to the preview text.
            canvas.drawLine(lastX, lineTopY, textX, lineTopY, paint);
        }

        // Paint the shading for the text preview
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setColor(GESTURE_PREVIEW_TEXT_SHADING_COLOR);
        paint.setStrokeWidth(mGesturePreviewTextShadingBorder);
        canvas.drawText(gesturePreviewText, textX, textY, paint);

        // Paint the text preview
        paint.setColor(GESTURE_PREVIEW_TEXT_COLOR);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(gesturePreviewText, textX, textY, paint);
    }
}
