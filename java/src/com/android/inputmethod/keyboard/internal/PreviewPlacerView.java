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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.widget.RelativeLayout;

import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

public class PreviewPlacerView extends RelativeLayout {
    private final Paint mGesturePaint;
    private final Paint mTextPaint;
    private final int mGestureFloatingPreviewTextColor;
    private final int mGestureFloatingPreviewTextOffset;
    private final int mGestureFloatingPreviewTextShadowColor;
    private final int mGestureFloatingPreviewTextShadowBorder;
    private final int mGestureFloatingPreviewTextShadingColor;
    private final int mGestureFloatingPreviewTextShadingBorder;
    private final int mGestureFloatingPreviewTextConnectorColor;
    private final int mGestureFloatingPreviewTextConnectorWidth;
    /* package */ final int mGestureFloatingPreviewTextLingerTimeout;

    private int mXOrigin;
    private int mYOrigin;

    private final SparseArray<PointerTracker> mPointers = CollectionUtils.newSparseArray();

    private String mGestureFloatingPreviewText;
    private int mLastPointerX;
    private int mLastPointerY;

    private boolean mDrawsGesturePreviewTrail;
    private boolean mDrawsGestureFloatingPreviewText;

    private final DrawingHandler mDrawingHandler = new DrawingHandler(this);

    private static class DrawingHandler extends StaticInnerHandlerWrapper<PreviewPlacerView> {
        private static final int MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 0;

        public DrawingHandler(PreviewPlacerView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            final PreviewPlacerView placerView = getOuterInstance();
            if (placerView == null) return;
            switch (msg.what) {
            case MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT:
                placerView.setGestureFloatingPreviewText(null);
                break;
            }
        }

        private void cancelDismissGestureFloatingPreviewText() {
            removeMessages(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT);
        }

        public void dismissGestureFloatingPreviewText() {
            cancelDismissGestureFloatingPreviewText();
            final PreviewPlacerView placerView = getOuterInstance();
            sendMessageDelayed(
                    obtainMessage(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT),
                    placerView.mGestureFloatingPreviewTextLingerTimeout);
        }

        public void cancelAllMessages() {
            cancelDismissGestureFloatingPreviewText();
        }
    }

    public PreviewPlacerView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public PreviewPlacerView(Context context, AttributeSet attrs, int defStyle) {
        super(context);
        setWillNotDraw(false);

        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(
                attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        final int gestureFloatingPreviewTextSize = keyboardViewAttr.getDimensionPixelSize(
                R.styleable.KeyboardView_gestureFloatingPreviewTextSize, 0);
        mGestureFloatingPreviewTextColor = keyboardViewAttr.getColor(
                R.styleable.KeyboardView_gestureFloatingPreviewTextColor, 0);
        mGestureFloatingPreviewTextOffset = keyboardViewAttr.getDimensionPixelOffset(
                R.styleable.KeyboardView_gestureFloatingPreviewTextOffset, 0);
        mGestureFloatingPreviewTextShadowColor = keyboardViewAttr.getColor(
                R.styleable.KeyboardView_gestureFloatingPreviewTextShadowColor, 0);
        mGestureFloatingPreviewTextShadowBorder = keyboardViewAttr.getDimensionPixelSize(
                R.styleable.KeyboardView_gestureFloatingPreviewTextShadowBorder, 0);
        mGestureFloatingPreviewTextShadingColor = keyboardViewAttr.getColor(
                R.styleable.KeyboardView_gestureFloatingPreviewTextShadingColor, 0);
        mGestureFloatingPreviewTextShadingBorder = keyboardViewAttr.getDimensionPixelSize(
                R.styleable.KeyboardView_gestureFloatingPreviewTextShadingBorder, 0);
        mGestureFloatingPreviewTextConnectorColor = keyboardViewAttr.getColor(
                R.styleable.KeyboardView_gestureFloatingPreviewTextConnectorColor, 0);
        mGestureFloatingPreviewTextConnectorWidth = keyboardViewAttr.getDimensionPixelSize(
                R.styleable.KeyboardView_gestureFloatingPreviewTextConnectorWidth, 0);
        mGestureFloatingPreviewTextLingerTimeout = keyboardViewAttr.getInt(
                R.styleable.KeyboardView_gestureFloatingPreviewTextLingerTimeout, 0);
        final int gesturePreviewTrailColor = keyboardViewAttr.getColor(
                R.styleable.KeyboardView_gesturePreviewTrailColor, 0);
        final int gesturePreviewTrailWidth = keyboardViewAttr.getDimensionPixelSize(
                R.styleable.KeyboardView_gesturePreviewTrailWidth, 0);
        keyboardViewAttr.recycle();

        mGesturePaint = new Paint();
        mGesturePaint.setAntiAlias(true);
        mGesturePaint.setStyle(Paint.Style.STROKE);
        mGesturePaint.setStrokeJoin(Paint.Join.ROUND);
        mGesturePaint.setColor(gesturePreviewTrailColor);
        mGesturePaint.setStrokeWidth(gesturePreviewTrailWidth);

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setStrokeJoin(Paint.Join.ROUND);
        mTextPaint.setTextAlign(Align.CENTER);
        mTextPaint.setTextSize(gestureFloatingPreviewTextSize);
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
        }
        mLastPointerX = tracker.getLastX();
        mLastPointerY = tracker.getLastY();
        // TODO: Should narrow the invalidate region.
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.translate(mXOrigin, mYOrigin);
        if (mDrawsGesturePreviewTrail) {
            synchronized (mPointers) {
                final int trackerCount = mPointers.size();
                for (int index = 0; index < trackerCount; index++) {
                    final PointerTracker tracker = mPointers.valueAt(index);
                    tracker.drawGestureTrail(canvas, mGesturePaint);
                }
            }
        }
        if (mDrawsGestureFloatingPreviewText) {
            drawGestureFloatingPreviewText(canvas, mGestureFloatingPreviewText);
        }
        canvas.translate(-mXOrigin, -mYOrigin);
    }

    public void setGestureFloatingPreviewText(String gestureFloatingPreviewText) {
        mGestureFloatingPreviewText = gestureFloatingPreviewText;
        invalidate();
    }

    public void dismissGestureFloatingPreviewText() {
        mDrawingHandler.dismissGestureFloatingPreviewText();
    }

    public void cancelAllMessages() {
        mDrawingHandler.cancelAllMessages();
    }

    private void drawGestureFloatingPreviewText(Canvas canvas, String gestureFloatingPreviewText) {
        if (TextUtils.isEmpty(gestureFloatingPreviewText)) {
            return;
        }

        final Paint paint = mTextPaint;
        // TODO: Figure out how we should deal with the floating preview text with multiple moving
        // fingers.
        final int lastX = mLastPointerX;
        final int lastY = mLastPointerY;
        final int textSize = (int)paint.getTextSize();
        final int canvasWidth = canvas.getWidth();

        final int halfTextWidth = (int)paint.measureText(gestureFloatingPreviewText) / 2 + textSize;
        final int textX = Math.min(Math.max(lastX, halfTextWidth), canvasWidth - halfTextWidth);

        int textY = Math.max(-textSize, lastY - mGestureFloatingPreviewTextOffset);
        if (textY < 0) {
            // Paint black text shadow if preview extends above keyboard region.
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setColor(mGestureFloatingPreviewTextShadowColor);
            paint.setStrokeWidth(mGestureFloatingPreviewTextShadowBorder);
            canvas.drawText(gestureFloatingPreviewText, textX, textY, paint);
        }

        // Paint the vertical line connecting the touch point to the preview text.
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(mGestureFloatingPreviewTextConnectorColor);
        paint.setStrokeWidth(mGestureFloatingPreviewTextConnectorWidth);
        final int lineTopY = textY - textSize / 4;
        canvas.drawLine(lastX, lastY, lastX, lineTopY, paint);
        if (lastX != textX) {
            // Paint the horizontal line connection the touch point to the preview text.
            canvas.drawLine(lastX, lineTopY, textX, lineTopY, paint);
        }

        // Paint the shading for the text preview
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setColor(mGestureFloatingPreviewTextShadingColor);
        paint.setStrokeWidth(mGestureFloatingPreviewTextShadingBorder);
        canvas.drawText(gestureFloatingPreviewText, textX, textY, paint);

        // Paint the text preview
        paint.setColor(mGestureFloatingPreviewTextColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(gestureFloatingPreviewText, textX, textY, paint);
    }
}
