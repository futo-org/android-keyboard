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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Message;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.widget.RelativeLayout;

import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.keyboard.internal.GesturePreviewTrail.Params;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.CoordinateUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;
import com.android.inputmethod.latin.SuggestedWords;

public final class PreviewPlacerView extends RelativeLayout {
    private final int[] mKeyboardViewOrigin = CoordinateUtils.newInstance();

    // TODO: Consolidate gesture preview trail with {@link KeyboardView}
    private final SparseArray<GesturePreviewTrail> mGesturePreviewTrails =
            CollectionUtils.newSparseArray();
    private final Params mGesturePreviewTrailParams;
    private final Paint mGesturePaint;
    private boolean mDrawsGesturePreviewTrail;
    private int mOffscreenWidth;
    private int mOffscreenHeight;
    private int mOffscreenOffsetY;
    private Bitmap mOffscreenBuffer;
    private final Canvas mOffscreenCanvas = new Canvas();
    private final Rect mOffscreenSrcRect = new Rect();
    private final Rect mDirtyRect = new Rect();
    private final Rect mGesturePreviewTrailBoundsRect = new Rect(); // per trail
    private final GestureFloatingPreviewText mGestureFloatingPreviewText;
    private boolean mShowSlidingKeyInputPreview;
    private final int[] mRubberBandFrom = CoordinateUtils.newInstance();
    private final int[] mRubberBandTo = CoordinateUtils.newInstance();

    private final DrawingHandler mDrawingHandler;

    // TODO: Remove drawing handler.
    private static final class DrawingHandler extends StaticInnerHandlerWrapper<PreviewPlacerView> {
        private static final int MSG_UPDATE_GESTURE_PREVIEW_TRAIL = 0;

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
            case MSG_UPDATE_GESTURE_PREVIEW_TRAIL:
                placerView.invalidate();
                break;
            }
        }

        public void postUpdateGestureTrailPreview() {
            removeMessages(MSG_UPDATE_GESTURE_PREVIEW_TRAIL);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_GESTURE_PREVIEW_TRAIL),
                    mGesturePreviewTrailParams.mUpdateInterval);
        }
    }

    public PreviewPlacerView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public PreviewPlacerView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context);
        setWillNotDraw(false);

        final TypedArray mainKeyboardViewAttr = context.obtainStyledAttributes(
                attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        // TODO: mGestureFloatingPreviewText could be an instance of GestureFloatingPreviewText or
        // MultiGesturePreviewText, depending on the user's choice in the settings.
        mGestureFloatingPreviewText = new GestureFloatingPreviewText(mainKeyboardViewAttr, context);
        mGesturePreviewTrailParams = new Params(mainKeyboardViewAttr);
        mainKeyboardViewAttr.recycle();

        mDrawingHandler = new DrawingHandler(this, mGesturePreviewTrailParams);

        final Paint gesturePaint = new Paint();
        gesturePaint.setAntiAlias(true);
        gesturePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        mGesturePaint = gesturePaint;

        final Paint layerPaint = new Paint();
        layerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        setLayerType(LAYER_TYPE_HARDWARE, layerPaint);
    }

    public void setKeyboardViewGeometry(final int[] originCoords, final int w, final int h) {
        CoordinateUtils.copy(mKeyboardViewOrigin, originCoords);
        mOffscreenOffsetY = (int)(h * GestureStroke.EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO);
        mOffscreenWidth = w;
        mOffscreenHeight = mOffscreenOffsetY + h;
    }

    public void setGesturePreviewMode(final boolean drawsGesturePreviewTrail,
            final boolean drawsGestureFloatingPreviewText) {
        mDrawsGesturePreviewTrail = drawsGesturePreviewTrail;
        mGestureFloatingPreviewText.setPreviewEnabled(drawsGestureFloatingPreviewText);
    }

    public void invalidatePointer(final PointerTracker tracker) {
        final boolean needsToUpdateLastPointer =
                tracker.isOldestTrackerInQueue() && mGestureFloatingPreviewText.isPreviewEnabled();
        if (needsToUpdateLastPointer) {
            mGestureFloatingPreviewText.setPreviewPosition(tracker);
        }

        if (mDrawsGesturePreviewTrail) {
            GesturePreviewTrail trail;
            synchronized (mGesturePreviewTrails) {
                trail = mGesturePreviewTrails.get(tracker.mPointerId);
                if (trail == null) {
                    trail = new GesturePreviewTrail();
                    mGesturePreviewTrails.put(tracker.mPointerId, trail);
                }
            }
            trail.addStroke(tracker.getGestureStrokeWithPreviewPoints(), tracker.getDownTime());
        }

        // TODO: Should narrow the invalidate region.
        if (mDrawsGesturePreviewTrail || needsToUpdateLastPointer) {
            invalidate();
        }
    }

    public void showSlidingKeyInputPreview(final PointerTracker tracker) {
        if (!tracker.isInSlidingKeyInputFromModifier()) {
            mShowSlidingKeyInputPreview = false;
            return;
        }
        tracker.getDownCoordinates(mRubberBandFrom);
        tracker.getLastCoordinates(mRubberBandTo);
        mShowSlidingKeyInputPreview = true;
        invalidate();
    }

    public void dismissSlidingKeyInputPreview() {
        mShowSlidingKeyInputPreview = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        freeOffscreenBuffer();
    }

    private void freeOffscreenBuffer() {
        if (mOffscreenBuffer != null) {
            mOffscreenBuffer.recycle();
            mOffscreenBuffer = null;
        }
    }

    private void mayAllocateOffscreenBuffer() {
        if (mOffscreenBuffer != null && mOffscreenBuffer.getWidth() == mOffscreenWidth
                && mOffscreenBuffer.getHeight() == mOffscreenHeight) {
            return;
        }
        freeOffscreenBuffer();
        mOffscreenBuffer = Bitmap.createBitmap(
                mOffscreenWidth, mOffscreenHeight, Bitmap.Config.ARGB_8888);
        mOffscreenCanvas.setBitmap(mOffscreenBuffer);
        mOffscreenCanvas.translate(0, mOffscreenOffsetY);
    }

    @Override
    public void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        final int originX = CoordinateUtils.x(mKeyboardViewOrigin);
        final int originY = CoordinateUtils.y(mKeyboardViewOrigin);
        canvas.translate(originX, originY);
        if (mDrawsGesturePreviewTrail) {
            mayAllocateOffscreenBuffer();
            // Draw gesture trails to offscreen buffer.
            final boolean needsUpdatingGesturePreviewTrail = drawGestureTrails(
                    mOffscreenCanvas, mGesturePaint, mDirtyRect);
            if (needsUpdatingGesturePreviewTrail) {
                mDrawingHandler.postUpdateGestureTrailPreview();
            }
            // Transfer offscreen buffer to screen.
            if (!mDirtyRect.isEmpty()) {
                mOffscreenSrcRect.set(mDirtyRect);
                mOffscreenSrcRect.offset(0, mOffscreenOffsetY);
                canvas.drawBitmap(mOffscreenBuffer, mOffscreenSrcRect, mDirtyRect, null);
                // Note: Defer clearing the dirty rectangle here because we will get cleared
                // rectangle on the canvas.
            }
        }
        mGestureFloatingPreviewText.drawPreview(canvas);
        if (mShowSlidingKeyInputPreview) {
            drawSlidingKeyInputPreview(canvas);
        }
        canvas.translate(-originX, -originY);
    }

    private boolean drawGestureTrails(final Canvas offscreenCanvas, final Paint paint,
            final Rect dirtyRect) {
        // Clear previous dirty rectangle.
        if (!dirtyRect.isEmpty()) {
            paint.setColor(Color.TRANSPARENT);
            paint.setStyle(Paint.Style.FILL);
            offscreenCanvas.drawRect(dirtyRect, paint);
        }
        dirtyRect.setEmpty();
        boolean needsUpdatingGesturePreviewTrail = false;
        // Draw gesture trails to offscreen buffer.
        synchronized (mGesturePreviewTrails) {
            // Trails count == fingers count that have ever been active.
            final int trailsCount = mGesturePreviewTrails.size();
            for (int index = 0; index < trailsCount; index++) {
                final GesturePreviewTrail trail = mGesturePreviewTrails.valueAt(index);
                needsUpdatingGesturePreviewTrail |=
                        trail.drawGestureTrail(offscreenCanvas, paint,
                                mGesturePreviewTrailBoundsRect, mGesturePreviewTrailParams);
                // {@link #mGesturePreviewTrailBoundsRect} has bounding box of the trail.
                dirtyRect.union(mGesturePreviewTrailBoundsRect);
            }
        }
        return needsUpdatingGesturePreviewTrail;
    }

    public void setGestureFloatingPreviewText(final SuggestedWords suggestedWords) {
        if (!mGestureFloatingPreviewText.isPreviewEnabled()) return;
        mGestureFloatingPreviewText.setSuggetedWords(suggestedWords);
        invalidate();
    }

    private void drawSlidingKeyInputPreview(final Canvas canvas) {
        // TODO: Implement rubber band preview
    }
}
