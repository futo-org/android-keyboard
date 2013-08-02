/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Message;
import android.util.SparseArray;
import android.view.View;

import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.keyboard.internal.GestureTrail.Params;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.StaticInnerHandlerWrapper;

/**
 * Draw gesture trail preview graphics during gesture.
 */
public final class GestureTrailsPreview extends AbstractDrawingPreview {
    private final SparseArray<GestureTrail> mGestureTrails = CollectionUtils.newSparseArray();
    private final Params mGestureTrailParams;
    private final Paint mGesturePaint;
    private int mOffscreenWidth;
    private int mOffscreenHeight;
    private int mOffscreenOffsetY;
    private Bitmap mOffscreenBuffer;
    private final Canvas mOffscreenCanvas = new Canvas();
    private final Rect mOffscreenSrcRect = new Rect();
    private final Rect mDirtyRect = new Rect();
    private final Rect mGestureTrailBoundsRect = new Rect(); // per trail

    private final DrawingHandler mDrawingHandler;

    private static final class DrawingHandler
            extends StaticInnerHandlerWrapper<GestureTrailsPreview> {
        private static final int MSG_UPDATE_GESTURE_TRAIL = 0;

        private final Params mGestureTrailParams;

        public DrawingHandler(final GestureTrailsPreview outerInstance,
                final Params gestureTrailParams) {
            super(outerInstance);
            mGestureTrailParams = gestureTrailParams;
        }

        @Override
        public void handleMessage(final Message msg) {
            final GestureTrailsPreview preview = getOuterInstance();
            if (preview == null) return;
            switch (msg.what) {
            case MSG_UPDATE_GESTURE_TRAIL:
                preview.getDrawingView().invalidate();
                break;
            }
        }

        public void postUpdateGestureTrailPreview() {
            removeMessages(MSG_UPDATE_GESTURE_TRAIL);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_GESTURE_TRAIL),
                    mGestureTrailParams.mUpdateInterval);
        }
    }

    public GestureTrailsPreview(final View drawingView, final TypedArray mainKeyboardViewAttr) {
        super(drawingView);
        mGestureTrailParams = new Params(mainKeyboardViewAttr);
        mDrawingHandler = new DrawingHandler(this, mGestureTrailParams);
        final Paint gesturePaint = new Paint();
        gesturePaint.setAntiAlias(true);
        gesturePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        mGesturePaint = gesturePaint;
    }

    @Override
    public void setKeyboardGeometry(final int[] originCoords, final int width, final int height) {
        mOffscreenOffsetY = (int)(
                height * GestureStroke.EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO);
        mOffscreenWidth = width;
        mOffscreenHeight = mOffscreenOffsetY + height;
    }

    @Override
    public void onDetachFromWindow() {
        freeOffscreenBuffer();
    }

    public void deallocateMemory() {
        freeOffscreenBuffer();
    }

    private void freeOffscreenBuffer() {
        mOffscreenCanvas.setBitmap(null);
        mOffscreenCanvas.setMatrix(null);
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

    private boolean drawGestureTrails(final Canvas offscreenCanvas, final Paint paint,
            final Rect dirtyRect) {
        // Clear previous dirty rectangle.
        if (!dirtyRect.isEmpty()) {
            paint.setColor(Color.TRANSPARENT);
            paint.setStyle(Paint.Style.FILL);
            offscreenCanvas.drawRect(dirtyRect, paint);
        }
        dirtyRect.setEmpty();
        boolean needsUpdatingGestureTrail = false;
        // Draw gesture trails to offscreen buffer.
        synchronized (mGestureTrails) {
            // Trails count == fingers count that have ever been active.
            final int trailsCount = mGestureTrails.size();
            for (int index = 0; index < trailsCount; index++) {
                final GestureTrail trail = mGestureTrails.valueAt(index);
                needsUpdatingGestureTrail |= trail.drawGestureTrail(offscreenCanvas, paint,
                        mGestureTrailBoundsRect, mGestureTrailParams);
                // {@link #mGestureTrailBoundsRect} has bounding box of the trail.
                dirtyRect.union(mGestureTrailBoundsRect);
            }
        }
        return needsUpdatingGestureTrail;
    }

    /**
     * Draws the preview
     * @param canvas The canvas where the preview is drawn.
     */
    @Override
    public void drawPreview(final Canvas canvas) {
        if (!isPreviewEnabled()) {
            return;
        }
        mayAllocateOffscreenBuffer();
        // Draw gesture trails to offscreen buffer.
        final boolean needsUpdatingGestureTrail = drawGestureTrails(
                mOffscreenCanvas, mGesturePaint, mDirtyRect);
        if (needsUpdatingGestureTrail) {
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

    /**
     * Set the position of the preview.
     * @param tracker The new location of the preview is based on the points in PointerTracker.
     */
    @Override
    public void setPreviewPosition(final PointerTracker tracker) {
        if (!isPreviewEnabled()) {
            return;
        }
        GestureTrail trail;
        synchronized (mGestureTrails) {
            trail = mGestureTrails.get(tracker.mPointerId);
            if (trail == null) {
                trail = new GestureTrail();
                mGestureTrails.put(tracker.mPointerId, trail);
            }
        }
        trail.addStroke(tracker.getGestureStrokeWithPreviewPoints(), tracker.getDownTime());

        // TODO: Should narrow the invalidate region.
        getDrawingView().invalidate();
    }
}
