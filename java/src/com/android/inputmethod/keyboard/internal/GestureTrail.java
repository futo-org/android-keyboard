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

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.ResizableIntArray;

/*
 * @attr ref R.styleable#MainKeyboardView_gestureTrailFadeoutStartDelay
 * @attr ref R.styleable#MainKeyboardView_gestureTrailFadeoutDuration
 * @attr ref R.styleable#MainKeyboardView_gestureTrailUpdateInterval
 * @attr ref R.styleable#MainKeyboardView_gestureTrailColor
 * @attr ref R.styleable#MainKeyboardView_gestureTrailWidth
 */
final class GestureTrail {
    public static final boolean DEBUG_SHOW_POINTS = false;
    public static final int POINT_TYPE_SAMPLED = 1;
    public static final int POINT_TYPE_INTERPOLATED = 2;
    private static final int FADEOUT_START_DELAY_FOR_DEBUG = 2000; // millisecond
    private static final int FADEOUT_DURATION_FOR_DEBUG = 200; // millisecond

    private static final int DEFAULT_CAPACITY = GestureStrokeWithPreviewPoints.PREVIEW_CAPACITY;

    // These three {@link ResizableIntArray}s should be synchronized by {@link #mEventTimes}.
    private final ResizableIntArray mXCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mYCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mEventTimes = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mPointTypes = new ResizableIntArray(
            DEBUG_SHOW_POINTS ? DEFAULT_CAPACITY : 0);
    private int mCurrentStrokeId = -1;
    // The wall time of the zero value in {@link #mEventTimes}
    private long mCurrentTimeBase;
    private int mTrailStartIndex;
    private int mLastInterpolatedDrawIndex;

    static final class Params {
        public final int mTrailColor;
        public final float mTrailStartWidth;
        public final float mTrailEndWidth;
        public final float mTrailBodyRatio;
        public boolean mTrailShadowEnabled;
        public final float mTrailShadowRatio;
        public final int mFadeoutStartDelay;
        public final int mFadeoutDuration;
        public final int mUpdateInterval;

        public final int mTrailLingerDuration;

        public Params(final TypedArray mainKeyboardViewAttr) {
            mTrailColor = mainKeyboardViewAttr.getColor(
                    R.styleable.MainKeyboardView_gestureTrailColor, 0);
            mTrailStartWidth = mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_gestureTrailStartWidth, 0.0f);
            mTrailEndWidth = mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_gestureTrailEndWidth, 0.0f);
            final int PERCENTAGE_INT = 100;
            mTrailBodyRatio = (float)mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gestureTrailBodyRatio, PERCENTAGE_INT)
                    / (float)PERCENTAGE_INT;
            final int trailShadowRatioInt = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gestureTrailShadowRatio, 0);
            mTrailShadowEnabled = (trailShadowRatioInt > 0);
            mTrailShadowRatio = (float)trailShadowRatioInt / (float)PERCENTAGE_INT;
            mFadeoutStartDelay = DEBUG_SHOW_POINTS ? FADEOUT_START_DELAY_FOR_DEBUG
                    : mainKeyboardViewAttr.getInt(
                            R.styleable.MainKeyboardView_gestureTrailFadeoutStartDelay, 0);
            mFadeoutDuration = DEBUG_SHOW_POINTS ? FADEOUT_DURATION_FOR_DEBUG
                    : mainKeyboardViewAttr.getInt(
                            R.styleable.MainKeyboardView_gestureTrailFadeoutDuration, 0);
            mTrailLingerDuration = mFadeoutStartDelay + mFadeoutDuration;
            mUpdateInterval = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gestureTrailUpdateInterval, 0);
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
        synchronized (mEventTimes) {
            addStrokeLocked(stroke, downTime);
        }
    }

    private void addStrokeLocked(final GestureStrokeWithPreviewPoints stroke, final long downTime) {
        final int trailSize = mEventTimes.getLength();
        stroke.appendPreviewStroke(mEventTimes, mXCoordinates, mYCoordinates, mPointTypes);
        if (mEventTimes.getLength() == trailSize) {
            return;
        }
        final int[] eventTimes = mEventTimes.getPrimitiveArray();
        final int strokeId = stroke.getGestureStrokeId();
        // Because interpolation algorithm in {@link GestureStrokeWithPreviewPoints} can't determine
        // the interpolated points in the last segment of gesture stroke, it may need recalculation
        // of interpolation when new segments are added to the stroke.
        // {@link #mLastInterpolatedDrawIndex} holds the start index of the last segment. It may
        // be updated by the interpolation
        // {@link GestureStrokeWithPreviewPoints#interpolatePreviewStroke}
        // or by animation {@link #drawGestureTrail(Canvas,Paint,Rect,Params)} below.
        final int lastInterpolatedIndex = (strokeId == mCurrentStrokeId)
                ? mLastInterpolatedDrawIndex : trailSize;
        mLastInterpolatedDrawIndex = stroke.interpolateStrokeAndReturnStartIndexOfLastSegment(
                lastInterpolatedIndex, mEventTimes, mXCoordinates, mYCoordinates, mPointTypes);
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
     * Draw gesture trail
     * @param canvas The canvas to draw the gesture trail
     * @param paint The paint object to be used to draw the gesture trail
     * @param outBoundsRect the bounding box of this gesture trail drawing
     * @param params The drawing parameters of gesture trail
     * @return true if some gesture trails remain to be drawn
     */
    public boolean drawGestureTrail(final Canvas canvas, final Paint paint,
            final Rect outBoundsRect, final Params params) {
        synchronized (mEventTimes) {
            return drawGestureTrailLocked(canvas, paint, outBoundsRect, params);
        }
    }

    private boolean drawGestureTrailLocked(final Canvas canvas, final Paint paint,
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
        final int[] pointTypes = mPointTypes.getPrimitiveArray();
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
                    final float body1 = r1 * params.mTrailBodyRatio;
                    final float body2 = r2 * params.mTrailBodyRatio;
                    final Path path = roundedLine.makePath(p1x, p1y, body1, p2x, p2y, body2);
                    if (!path.isEmpty()) {
                        roundedLine.getBounds(mRoundedLineBounds);
                        if (params.mTrailShadowEnabled) {
                            final float shadow2 = r2 * params.mTrailShadowRatio;
                            paint.setShadowLayer(shadow2, 0.0f, 0.0f, params.mTrailColor);
                            final int shadowInset = -(int)Math.ceil(shadow2);
                            mRoundedLineBounds.inset(shadowInset, shadowInset);
                        }
                        // Take union for the bounds.
                        outBoundsRect.union(mRoundedLineBounds);
                        final int alpha = getAlpha(elapsedTime, params);
                        paint.setAlpha(alpha);
                        canvas.drawPath(path, paint);
                    }
                }
                p1x = p2x;
                p1y = p2y;
                r1 = r2;
            }
            if (DEBUG_SHOW_POINTS) {
                debugDrawPoints(canvas, startIndex, trailSize, paint);
            }
        }

        final int newSize = trailSize - startIndex;
        if (newSize < startIndex) {
            mTrailStartIndex = 0;
            if (newSize > 0) {
                System.arraycopy(eventTimes, startIndex, eventTimes, 0, newSize);
                System.arraycopy(xCoords, startIndex, xCoords, 0, newSize);
                System.arraycopy(yCoords, startIndex, yCoords, 0, newSize);
                if (DEBUG_SHOW_POINTS) {
                    System.arraycopy(pointTypes, startIndex, pointTypes, 0, newSize);
                }
            }
            mEventTimes.setLength(newSize);
            mXCoordinates.setLength(newSize);
            mYCoordinates.setLength(newSize);
            if (DEBUG_SHOW_POINTS) {
                mPointTypes.setLength(newSize);
            }
            // The start index of the last segment of the stroke
            // {@link mLastInterpolatedDrawIndex} should also be updated because all array
            // elements have just been shifted for compaction or been zeroed.
            mLastInterpolatedDrawIndex = Math.max(mLastInterpolatedDrawIndex - startIndex, 0);
        }
        return newSize > 0;
    }

    private void debugDrawPoints(final Canvas canvas, final int startIndex, final int endIndex,
            final Paint paint) {
        final int[] xCoords = mXCoordinates.getPrimitiveArray();
        final int[] yCoords = mYCoordinates.getPrimitiveArray();
        final int[] pointTypes = mPointTypes.getPrimitiveArray();
        // {@link Paint} that is zero width stroke and anti alias off draws exactly 1 pixel.
        paint.setAntiAlias(false);
        paint.setStrokeWidth(0);
        for (int i = startIndex; i < endIndex; i++) {
            final int pointType = pointTypes[i];
            if (pointType == POINT_TYPE_INTERPOLATED) {
                paint.setColor(Color.RED);
            } else if (pointType == POINT_TYPE_SAMPLED) {
                paint.setColor(0xFFA000FF);
            } else {
                paint.setColor(Color.GREEN);
            }
            canvas.drawPoint(getXCoordValue(xCoords[i]), yCoords[i], paint);
        }
        paint.setAntiAlias(true);
    }
}
