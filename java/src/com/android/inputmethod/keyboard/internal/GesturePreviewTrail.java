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
import android.os.SystemClock;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResizableIntArray;

/*
 * @attr ref R.styleable#MainKeyboardView_gesturePreviewTrailFadeoutStartDelay
 * @attr ref R.styleable#MainKeyboardView_gesturePreviewTrailFadeoutDuration
 * @attr ref R.styleable#MainKeyboardView_gesturePreviewTrailUpdateInterval
 * @attr ref R.styleable#MainKeyboardView_gesturePreviewTrailColor
 * @attr ref R.styleable#MainKeyboardView_gesturePreviewTrailWidth
 */
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

        public Params(final TypedArray mainKeyboardViewAttr) {
            mTrailColor = mainKeyboardViewAttr.getColor(
                    R.styleable.MainKeyboardView_gesturePreviewTrailColor, 0);
            mTrailStartWidth = mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_gesturePreviewTrailStartWidth, 0.0f);
            mTrailEndWidth = mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_gesturePreviewTrailEndWidth, 0.0f);
            mFadeoutStartDelay = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gesturePreviewTrailFadeoutStartDelay, 0);
            mFadeoutDuration = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gesturePreviewTrailFadeoutDuration, 0);
            mTrailLingerDuration = mFadeoutStartDelay + mFadeoutDuration;
            mUpdateInterval = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gesturePreviewTrailUpdateInterval, 0);
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

    /**
     * Calculate the alpha of a gesture trail.
     * A gesture trail starts from fully opaque. After mFadeStartDelay has been passed, the alpha
     * of a trail reduces in proportion to the elapsed time. Then after mFadeDuration has been
     * passed, a trail becomes fully transparent.
     *
     * @param elapsedTime the elapsed time since a trail has been made.
     * @param params gesture trail display parameters
     * @return the width of a gesture trail
     */
    private static int getAlpha(final int elapsedTime, final Params params) {
        if (elapsedTime < params.mFadeoutStartDelay) {
            return Constants.Color.ALPHA_OPAQUE;
        }
        final int decreasingAlpha = Constants.Color.ALPHA_OPAQUE
                * (elapsedTime - params.mFadeoutStartDelay)
                / params.mFadeoutDuration;
        return Constants.Color.ALPHA_OPAQUE - decreasingAlpha;
    }

    /**
     * Calculate the width of a gesture trail.
     * A gesture trail starts from the width of mTrailStartWidth and reduces its width in proportion
     * to the elapsed time. After mTrailEndWidth has been passed, the width becomes mTraiLEndWidth.
     *
     * @param elapsedTime the elapsed time since a trail has been made.
     * @param params gesture trail display parameters
     * @return the width of a gesture trail
     */
    private static float getWidth(final int elapsedTime, final Params params) {
        final float deltaWidth = params.mTrailStartWidth - params.mTrailEndWidth;
        return params.mTrailStartWidth - (deltaWidth * elapsedTime) / params.mTrailLingerDuration;
    }

    private final RoundedLine mRoundedLine = new RoundedLine();
    private final Rect mRoundedLineBounds = new Rect();

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
        // Initialize bounds rectangle.
        outBoundsRect.setEmpty();
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
            final RoundedLine roundedLine = mRoundedLine;
            int p1x = getXCoordValue(xCoords[startIndex]);
            int p1y = yCoords[startIndex];
            final int lastTime = sinceDown - eventTimes[startIndex];
            float r1 = getWidth(lastTime, params) / 2.0f;
            for (int i = startIndex + 1; i < trailSize; i++) {
                final int elapsedTime = sinceDown - eventTimes[i];
                final int p2x = getXCoordValue(xCoords[i]);
                final int p2y = yCoords[i];
                final float r2 = getWidth(elapsedTime, params) / 2.0f;
                // Draw trail line only when the current point isn't a down point.
                if (!isDownEventXCoord(xCoords[i])) {
                    final Path path = roundedLine.makePath(p1x, p1y, r1, p2x, p2y, r2);
                    if (path != null) {
                        final int alpha = getAlpha(elapsedTime, params);
                        paint.setAlpha(alpha);
                        canvas.drawPath(path, paint);
                        // Take union for the bounds.
                        roundedLine.getBounds(mRoundedLineBounds);
                        outBoundsRect.union(mRoundedLineBounds);
                    }
                }
                p1x = p2x;
                p1y = p2y;
                r1 = r2;
            }
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
