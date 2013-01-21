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
import com.android.inputmethod.keyboard.internal.GesturePreviewTrail.Params;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

/**
 * Draw gesture trail preview graphics during gesture.
 */
public final class GestureTrailsPreview extends AbstractDrawingPreview {
    private final SparseArray<GesturePreviewTrail> mGesturePreviewTrails =
            CollectionUtils.newSparseArray();
    private final Params mGesturePreviewTrailParams;
    private final Paint mGesturePaint;
    private int mOffscreenWidth;
    private int mOffscreenHeight;
    private int mOffscreenOffsetY;
    private Bitmap mOffscreenBuffer;
    private final Canvas mOffscreenCanvas = new Canvas();
    private final Rect mOffscreenSrcRect = new Rect();
    private final Rect mDirtyRect = new Rect();
    private final Rect mGesturePreviewTrailBoundsRect = new Rect(); // per trail

    private final DrawingHandler mDrawingHandler;

    private static final class DrawingHandler
            extends StaticInnerHandlerWrapper<GestureTrailsPreview> {
        private static final int MSG_UPDATE_GESTURE_PREVIEW_TRAIL = 0;

        private final Params mGesturePreviewTrailParams;

        public DrawingHandler(final GestureTrailsPreview outerInstance,
                final Params gesturePreviewTrailParams) {
            super(outerInstance);
            mGesturePreviewTrailParams = gesturePreviewTrailParams;
        }

        @Override
        public void handleMessage(final Message msg) {
            final GestureTrailsPreview preview = getOuterInstance();
            if (preview == null) return;
            switch (msg.what) {
            case MSG_UPDATE_GESTURE_PREVIEW_TRAIL:
                preview.getDrawingView().invalidate();
                break;
            }
        }

        public void postUpdateGestureTrailPreview() {
            removeMessages(MSG_UPDATE_GESTURE_PREVIEW_TRAIL);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_GESTURE_PREVIEW_TRAIL),
                    mGesturePreviewTrailParams.mUpdateInterval);
        }
    }

    public GestureTrailsPreview(final View drawingView, final TypedArray mainKeyboardViewAttr) {
        super(drawingView);
        mGesturePreviewTrailParams = new Params(mainKeyboardViewAttr);
        mDrawingHandler = new DrawingHandler(this, mGesturePreviewTrailParams);
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

    /**
     * Set the position of the preview.
     * @param tracker The new location of the preview is based on the points in PointerTracker.
     */
    @Override
    public void setPreviewPosition(final PointerTracker tracker) {
        if (!isPreviewEnabled()) {
            return;
        }
        GesturePreviewTrail trail;
        synchronized (mGesturePreviewTrails) {
            trail = mGesturePreviewTrails.get(tracker.mPointerId);
            if (trail == null) {
                trail = new GesturePreviewTrail();
                mGesturePreviewTrails.put(tracker.mPointerId, trail);
            }
        }
        trail.addStroke(tracker.getGestureStrokeWithPreviewPoints(), tracker.getDownTime());

        // TODO: Should narrow the invalidate region.
        getDrawingView().invalidate();
    }
}
