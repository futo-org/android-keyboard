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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.widget.RelativeLayout;

import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.keyboard.internal.GesturePreviewTrail.Params;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

public class PreviewPlacerView extends RelativeLayout {
    private final int mGestureFloatingPreviewTextColor;
    private final int mGestureFloatingPreviewTextOffset;
    private final int mGestureFloatingPreviewColor;
    private final float mGestureFloatingPreviewHorizontalPadding;
    private final float mGestureFloatingPreviewVerticalPadding;
    private final float mGestureFloatingPreviewRoundRadius;
    /* package */ final int mGestureFloatingPreviewTextLingerTimeout;

    private int mXOrigin;
    private int mYOrigin;

    private final SparseArray<GesturePreviewTrail> mGesturePreviewTrails =
            CollectionUtils.newSparseArray();
    private final Params mGesturePreviewTrailParams;
    private final Paint mGesturePaint;
    private boolean mDrawsGesturePreviewTrail;
    private Bitmap mOffscreenBuffer;
    private final Canvas mOffscreenCanvas = new Canvas();
    private final Rect mOffscreenDirtyRect = new Rect();
    private final Rect mGesturePreviewTrailBoundsRect = new Rect(); // per trail

    private final Paint mTextPaint;
    private String mGestureFloatingPreviewText;
    private final int mGestureFloatingPreviewTextHeight;
    // {@link RectF} is needed for {@link Canvas#drawRoundRect(RectF, float, float, Paint)}.
    private final RectF mGestureFloatingPreviewRectangle = new RectF();
    private int mLastPointerX;
    private int mLastPointerY;
    private static final char[] TEXT_HEIGHT_REFERENCE_CHAR = { 'M' };
    private boolean mDrawsGestureFloatingPreviewText;

    private final DrawingHandler mDrawingHandler;

    private static class DrawingHandler extends StaticInnerHandlerWrapper<PreviewPlacerView> {
        private static final int MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 0;
        private static final int MSG_UPDATE_GESTURE_PREVIEW_TRAIL = 1;

        private final Params mGesturePreviewTrailParams;

        public DrawingHandler(final PreviewPlacerView outerInstance,
                final Params gesturePreviewTrailParams) {
            super(outerInstance);
            mGesturePreviewTrailParams = gesturePreviewTrailParams;
        }

        @Override
        public void handleMessage(final Message msg) {
            final PreviewPlacerView placerView = getOuterInstance();
            if (placerView == null) return;
            switch (msg.what) {
            case MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT:
                placerView.setGestureFloatingPreviewText(null);
                break;
            case MSG_UPDATE_GESTURE_PREVIEW_TRAIL:
                placerView.invalidate();
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

        private void cancelUpdateGestureTrailPreview() {
            removeMessages(MSG_UPDATE_GESTURE_PREVIEW_TRAIL);
        }

        public void postUpdateGestureTrailPreview() {
            cancelUpdateGestureTrailPreview();
            sendMessageDelayed(obtainMessage(MSG_UPDATE_GESTURE_PREVIEW_TRAIL),
                    mGesturePreviewTrailParams.mUpdateInterval);
        }

        public void cancelAllMessages() {
            cancelDismissGestureFloatingPreviewText();
            cancelUpdateGestureTrailPreview();
        }
    }

    public PreviewPlacerView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public PreviewPlacerView(final Context context, final AttributeSet attrs, final int defStyle) {
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
        mGestureFloatingPreviewColor = keyboardViewAttr.getColor(
                R.styleable.KeyboardView_gestureFloatingPreviewColor, 0);
        mGestureFloatingPreviewHorizontalPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_gestureFloatingPreviewHorizontalPadding, 0.0f);
        mGestureFloatingPreviewVerticalPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_gestureFloatingPreviewVerticalPadding, 0.0f);
        mGestureFloatingPreviewRoundRadius = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_gestureFloatingPreviewRoundRadius, 0.0f);
        mGestureFloatingPreviewTextLingerTimeout = keyboardViewAttr.getInt(
                R.styleable.KeyboardView_gestureFloatingPreviewTextLingerTimeout, 0);
        mGesturePreviewTrailParams = new Params(keyboardViewAttr);
        keyboardViewAttr.recycle();

        mDrawingHandler = new DrawingHandler(this, mGesturePreviewTrailParams);

        final Paint gesturePaint = new Paint();
        gesturePaint.setAntiAlias(true);
        gesturePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        mGesturePaint = gesturePaint;

        final Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Align.CENTER);
        textPaint.setTextSize(gestureFloatingPreviewTextSize);
        mTextPaint = textPaint;
        final Rect textRect = new Rect();
        textPaint.getTextBounds(TEXT_HEIGHT_REFERENCE_CHAR, 0, 1, textRect);
        mGestureFloatingPreviewTextHeight = textRect.height();

        final Paint layerPaint = new Paint();
        layerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        setLayerType(LAYER_TYPE_HARDWARE, layerPaint);
    }

    public void setOrigin(final int x, final int y) {
        mXOrigin = x;
        mYOrigin = y;
    }

    public void setGesturePreviewMode(final boolean drawsGesturePreviewTrail,
            final boolean drawsGestureFloatingPreviewText) {
        mDrawsGesturePreviewTrail = drawsGesturePreviewTrail;
        mDrawsGestureFloatingPreviewText = drawsGestureFloatingPreviewText;
    }

    public void invalidatePointer(final PointerTracker tracker, final boolean isOldestTracker) {
        GesturePreviewTrail trail;
        synchronized (mGesturePreviewTrails) {
            trail = mGesturePreviewTrails.get(tracker.mPointerId);
            if (trail == null) {
                trail = new GesturePreviewTrail();
                mGesturePreviewTrails.put(tracker.mPointerId, trail);
            }
        }
        trail.addStroke(tracker.getGestureStrokeWithPreviewTrail(), tracker.getDownTime());

        if (isOldestTracker) {
            mLastPointerX = tracker.getLastX();
            mLastPointerY = tracker.getLastY();
        }
        // TODO: Should narrow the invalidate region.
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mOffscreenBuffer != null) {
            mOffscreenBuffer.recycle();
            mOffscreenBuffer = null;
        }
    }

    @Override
    public void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        canvas.translate(mXOrigin, mYOrigin);
        if (mDrawsGesturePreviewTrail) {
            if (mOffscreenBuffer == null) {
                mOffscreenBuffer = Bitmap.createBitmap(
                        getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                mOffscreenCanvas.setBitmap(mOffscreenBuffer);
            }
            if (!mOffscreenDirtyRect.isEmpty()) {
                // Clear previous dirty rectangle.
                mGesturePaint.setColor(Color.TRANSPARENT);
                mGesturePaint.setStyle(Paint.Style.FILL);
                mOffscreenCanvas.drawRect(mOffscreenDirtyRect, mGesturePaint);
                mOffscreenDirtyRect.setEmpty();
            }
            boolean needsUpdatingGesturePreviewTrail = false;
            synchronized (mGesturePreviewTrails) {
                // Trails count == fingers count that have ever been active.
                final int trailsCount = mGesturePreviewTrails.size();
                for (int index = 0; index < trailsCount; index++) {
                    final GesturePreviewTrail trail = mGesturePreviewTrails.valueAt(index);
                    needsUpdatingGesturePreviewTrail |=
                            trail.drawGestureTrail(mOffscreenCanvas, mGesturePaint,
                                    mGesturePreviewTrailBoundsRect, mGesturePreviewTrailParams);
                    // {@link #mGesturePreviewTrailBoundsRect} has bounding box of the trail.
                    mOffscreenDirtyRect.union(mGesturePreviewTrailBoundsRect);
                }
            }
            if (!mOffscreenDirtyRect.isEmpty()) {
                canvas.drawBitmap(mOffscreenBuffer, mOffscreenDirtyRect, mOffscreenDirtyRect,
                        mGesturePaint);
                // Note: Defer clearing the dirty rectangle here because we will get cleared
                // rectangle on the canvas.
            }
            if (needsUpdatingGesturePreviewTrail) {
                mDrawingHandler.postUpdateGestureTrailPreview();
            }
        }
        if (mDrawsGestureFloatingPreviewText) {
            drawGestureFloatingPreviewText(canvas, mGestureFloatingPreviewText);
        }
        canvas.translate(-mXOrigin, -mYOrigin);
    }

    public void setGestureFloatingPreviewText(final String gestureFloatingPreviewText) {
        mGestureFloatingPreviewText = gestureFloatingPreviewText;
        invalidate();
    }

    public void dismissGestureFloatingPreviewText() {
        mDrawingHandler.dismissGestureFloatingPreviewText();
    }

    public void cancelAllMessages() {
        mDrawingHandler.cancelAllMessages();
    }

    private void drawGestureFloatingPreviewText(final Canvas canvas,
            final String gestureFloatingPreviewText) {
        if (TextUtils.isEmpty(gestureFloatingPreviewText)) {
            return;
        }

        final Paint paint = mTextPaint;
        final RectF rectangle = mGestureFloatingPreviewRectangle;
        // TODO: Figure out how we should deal with the floating preview text with multiple moving
        // fingers.

        // Paint the round rectangle background.
        final int textHeight = mGestureFloatingPreviewTextHeight;
        final float textWidth = paint.measureText(gestureFloatingPreviewText);
        final float hPad = mGestureFloatingPreviewHorizontalPadding;
        final float vPad = mGestureFloatingPreviewVerticalPadding;
        final float rectWidth = textWidth + hPad * 2.0f;
        final float rectHeight = textHeight + vPad * 2.0f;
        final int canvasWidth = canvas.getWidth();
        final float rectX = Math.min(Math.max(mLastPointerX - rectWidth / 2.0f, 0.0f),
                canvasWidth - rectWidth);
        final float rectY = mLastPointerY - mGestureFloatingPreviewTextOffset - rectHeight;
        rectangle.set(rectX, rectY, rectX + rectWidth, rectY + rectHeight);
        final float round = mGestureFloatingPreviewRoundRadius;
        paint.setColor(mGestureFloatingPreviewColor);
        canvas.drawRoundRect(rectangle, round, round, paint);
        // Paint the text preview
        paint.setColor(mGestureFloatingPreviewTextColor);
        final float textX = rectX + hPad + textWidth / 2.0f;
        final float textY = rectY + vPad + textHeight;
        canvas.drawText(gestureFloatingPreviewText, textX, textY, paint);
    }
}
