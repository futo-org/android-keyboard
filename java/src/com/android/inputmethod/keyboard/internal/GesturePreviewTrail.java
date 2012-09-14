/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard.internal;

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResizableIntArray;

final class GesturePreviewTrail {
    private static final int DEFAULT_CAPACITY = GestureStrokeWithPreviewPoints.PREVIEW_CAPACITY;

    private final ResizableIntArray mXCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mYCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mEventTimes = new ResizableIntArray(DEFAULT_CAPACITY);
    private int mCurrentStrokeId = -1;
    // The wall time of the zero value in {@link #mEventTimes}
    private long mCurrentTimeBase;
    private int mTrailStartIndex;

    static final class Params {
        public final int mTrailColor;
        public final float mTrailStartWidth;
        public final float mTrailEndWidth;
        public final int mFadeoutStartDelay;
        public final int mFadeoutDuration;
        public final int mUpdateInterval;

        public final int mTrailLingerDuration;

        public Params(final TypedArray keyboardViewAttr) {
            mTrailColor = keyboardViewAttr.getColor(
                    R.styleable.KeyboardView_gesturePreviewTrailColor, 0);
            mTrailStartWidth = keyboardViewAttr.getDimension(
                    R.styleable.KeyboardView_gesturePreviewTrailStartWidth, 0.0f);
            mTrailEndWidth = keyboardViewAttr.getDimension(
                    R.styleable.KeyboardView_gesturePreviewTrailEndWidth, 0.0f);
            mFadeoutStartDelay = keyboardViewAttr.getInt(
                    R.styleable.KeyboardView_gesturePreviewTrailFadeoutStartDelay, 0);
            mFadeoutDuration = keyboardViewAttr.getInt(
                    R.styleable.KeyboardView_gesturePreviewTrailFadeoutDuration, 0);
            mTrailLingerDuration = mFadeoutStartDelay + mFadeoutDuration;
            mUpdateInterval = keyboardViewAttr.getInt(
                    R.styleable.KeyboardView_gesturePreviewTrailUpdateInterval, 0);
        }
    }

    // Use this value as imaginary zero because x-coordinates may be zero.
    private static final int DOWN_EVENT_MARKER = -128;

    private static int markAsDownEvent(final int xCoord) {
        return DOWN_EVENT_MARKER - xCoord;
    }

    private static boolean isDownEventXCoord(final int xCoordOrMark) {
        return xCoordOrMark <= DOWN_EVENT_MARKER;
    }

    private static int getXCoordValue(final int xCoordOrMark) {
        return isDownEventXCoord(xCoordOrMark)
                ? DOWN_EVENT_MARKER - xCoordOrMark : xCoordOrMark;
    }

    public void addStroke(final GestureStrokeWithPreviewPoints stroke, final long downTime) {
        final int trailSize = mEventTimes.getLength();
        stroke.appendPreviewStroke(mEventTimes, mXCoordinates, mYCoordinates);
        if (mEventTimes.getLength() == trailSize) {
            return;
        }
        final int[] eventTimes = mEventTimes.getPrimitiveArray();
        final int strokeId = stroke.getGestureStrokeId();
        if (strokeId != mCurrentStrokeId) {
            final int elapsedTime = (int)(downTime - mCurrentTimeBase);
            for (int i = mTrailStartIndex; i < trailSize; i++) {
                // Decay the previous strokes' event times.
                eventTimes[i] -= elapsedTime;
            }
            final int[] xCoords = mXCoordinates.getPrimitiveArray();
            final int downIndex = trailSize;
            xCoords[downIndex] = markAsDownEvent(xCoords[downIndex]);
            mCurrentTimeBase = downTime - eventTimes[downIndex];
            mCurrentStrokeId = strokeId;
        }
    }

    private static int getAlpha(final int elapsedTime, final Params params) {
        if (elapsedTime < params.mFadeoutStartDelay) {
            return Constants.Color.ALPHA_OPAQUE;
        }
        final int decreasingAlpha = Constants.Color.ALPHA_OPAQUE
                * (elapsedTime - params.mFadeoutStartDelay)
                / params.mFadeoutDuration;
        return Constants.Color.ALPHA_OPAQUE - decreasingAlpha;
    }

    private static float getWidth(final int elapsedTime, final Params params) {
        return Math.max((params.mTrailLingerDuration - elapsedTime)
                * (params.mTrailStartWidth - params.mTrailEndWidth)
                / params.mTrailLingerDuration, 0.0f);
    }

    static final class WorkingSet {
        // Input
        // Previous point (P1) coordinates and trail radius.
        public float p1x, p1y;
        public float r1;
        // Current point (P2) coordinates and trail radius.
        public float p2x, p2y;
        public float r2;

        // Output
        // Closing point of arc at P1.
        public float p1ax, p1ay;
        // Opening point of arc at P1.
        public float p1bx, p1by;
        // Opening point of arc at P2.
        public float p2ax, p2ay;
        // Closing point of arc at P2.
        public float p2bx, p2by;
        // Start angle of the trail arcs.
        public float aa;
        // Sweep angle of the trail arc at P1.
        public float a1;
        public RectF arc1 = new RectF();
        // Sweep angle of the trail arc at P2.
        public float a2;
        public RectF arc2 = new RectF();
    }

    private static final float RIGHT_ANGLE = (float)(Math.PI / 2.0d);
    private static final float RADIAN_TO_DEGREE = (float)(180.0d / Math.PI);

    private static boolean calculatePathPoints(final WorkingSet w) {
        final float dx = w.p2x - w.p1x;
        final float dy = w.p2y - w.p1y;
        // Distance of the points.
        final double l = Math.hypot(dx, dy);
        if (Double.compare(0.0d, l) == 0) {
            return false;
        }
        // Angle of the line p1-p2
        final float a = (float)Math.atan2(dy, dx);
        // Difference of trail cap radius.
        final float dr = w.r2 - w.r1;
        // Variation of angle at trail cap.
        final float ar = (float)Math.asin(dr / l);
        // The start angle of trail cap arc at P1.
        final float aa = a - (RIGHT_ANGLE + ar);
        // The end angle of trail cap arc at P2.
        final float ab = a + (RIGHT_ANGLE + ar);
        final float cosa = (float)Math.cos(aa);
        final float sina = (float)Math.sin(aa);
        final float cosb = (float)Math.cos(ab);
        final float sinb = (float)Math.sin(ab);
        w.p1ax = w.p1x + w.r1 * cosa;
        w.p1ay = w.p1y + w.r1 * sina;
        w.p1bx = w.p1x + w.r1 * cosb;
        w.p1by = w.p1y + w.r1 * sinb;
        w.p2ax = w.p2x + w.r2 * cosa;
        w.p2ay = w.p2y + w.r2 * sina;
        w.p2bx = w.p2x + w.r2 * cosb;
        w.p2by = w.p2y + w.r2 * sinb;
        w.aa = aa * RADIAN_TO_DEGREE;
        final float ar2degree = ar * 2.0f * RADIAN_TO_DEGREE;
        w.a1 = -180.0f + ar2degree;
        w.a2 = 180.0f + ar2degree;
        w.arc1.set(w.p1x, w.p1y, w.p1x, w.p1y);
        w.arc1.inset(-w.r1, -w.r1);
        w.arc2.set(w.p2x, w.p2y, w.p2x, w.p2y);
        w.arc2.inset(-w.r2, -w.r2);
        return true;
    }

    private static void createPath(final Path path, final WorkingSet w) {
        path.rewind();
        // Trail cap at P1.
        path.moveTo(w.p1x, w.p1y);
        path.arcTo(w.arc1, w.aa, w.a1);
        // Trail cap at P2.
        path.moveTo(w.p2x, w.p2y);
        path.arcTo(w.arc2, w.aa, w.a2);
        // Two trapezoids connecting P1 and P2.
        path.moveTo(w.p1ax, w.p1ay);
        path.lineTo(w.p1x, w.p1y);
        path.lineTo(w.p1bx, w.p1by);
        path.lineTo(w.p2bx, w.p2by);
        path.lineTo(w.p2x, w.p2y);
        path.lineTo(w.p2ax, w.p2ay);
        path.close();
    }

    private final WorkingSet mWorkingSet = new WorkingSet();
    private final Path mPath = new Path();

    /**
     * Draw gesture preview trail
     * @param canvas The canvas to draw the gesture preview trail
     * @param paint The paint object to be used to draw the gesture preview trail
     * @param outBoundsRect the bounding box of this gesture trail drawing
     * @param params The drawing parameters of gesture preview trail
     * @return true if some gesture preview trails remain to be drawn
     */
    public boolean drawGestureTrail(final Canvas canvas, final Paint paint,
            final Rect outBoundsRect, final Params params) {
        final int trailSize = mEventTimes.getLength();
        if (trailSize == 0) {
            return false;
        }

        final int[] eventTimes = mEventTimes.getPrimitiveArray();
        final int[] xCoords = mXCoordinates.getPrimitiveArray();
        final int[] yCoords = mYCoordinates.getPrimitiveArray();
        final int sinceDown = (int)(SystemClock.uptimeMillis() - mCurrentTimeBase);
        int startIndex;
        for (startIndex = mTrailStartIndex; startIndex < trailSize; startIndex++) {
            final int elapsedTime = sinceDown - eventTimes[startIndex];
            // Skip too old trail points.
            if (elapsedTime < params.mTrailLingerDuration) {
                break;
            }
        }
        mTrailStartIndex = startIndex;

        if (startIndex < trailSize) {
            paint.setColor(params.mTrailColor);
            paint.setStyle(Paint.Style.FILL);
            final Path path = mPath;
            final WorkingSet w = mWorkingSet;
            w.p1x = getXCoordValue(xCoords[startIndex]);
            w.p1y = yCoords[startIndex];
            int lastTime = sinceDown - eventTimes[startIndex];
            float maxWidth = getWidth(lastTime, params);
            w.r1 = maxWidth / 2.0f;
            // Initialize bounds rectangle.
            outBoundsRect.set((int)w.p1x, (int)w.p1y, (int)w.p1x, (int)w.p1y);
            for (int i = startIndex + 1; i < trailSize - 1; i++) {
                final int elapsedTime = sinceDown - eventTimes[i];
                w.p2x = getXCoordValue(xCoords[i]);
                w.p2y = yCoords[i];
                // Draw trail line only when the current point isn't a down point.
                if (!isDownEventXCoord(xCoords[i])) {
                    final int alpha = getAlpha(elapsedTime, params);
                    paint.setAlpha(alpha);
                    final float width = getWidth(elapsedTime, params);
                    w.r2 = width / 2.0f;
                    if (calculatePathPoints(w)) {
                        createPath(path, w);
                        canvas.drawPath(path, paint);
                        outBoundsRect.union((int)w.p2x, (int)w.p2y);
                    }
                    // Take union for the bounds.
                    maxWidth = Math.max(maxWidth, width);
                }
                w.p1x = w.p2x;
                w.p1y = w.p2y;
                w.r1 = w.r2;
                lastTime = elapsedTime;
            }
            // Take care of trail line width.
            final int inset = -((int)maxWidth + 1);
            outBoundsRect.inset(inset, inset);
        }

        final int newSize = trailSize - startIndex;
        if (newSize < startIndex) {
            mTrailStartIndex = 0;
            if (newSize > 0) {
                System.arraycopy(eventTimes, startIndex, eventTimes, 0, newSize);
                System.arraycopy(xCoords, startIndex, xCoords, 0, newSize);
                System.arraycopy(yCoords, startIndex, yCoords, 0, newSize);
            }
            mEventTimes.setLength(newSize);
            mXCoordinates.setLength(newSize);
            mYCoordinates.setLength(newSize);
        }
        return newSize > 0;
    }
}
