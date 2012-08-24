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
import android.os.SystemClock;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResizableIntArray;

class GesturePreviewTrail {
    private static final int DEFAULT_CAPACITY = GestureStrokeWithPreviewTrail.PREVIEW_CAPACITY;

    private final GesturePreviewTrailParams mPreviewParams;
    private final ResizableIntArray mXCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mYCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mEventTimes = new ResizableIntArray(DEFAULT_CAPACITY);
    private int mCurrentStrokeId = -1;
    private long mCurrentDownTime;

    // Use this value as imaginary zero because x-coordinates may be zero.
    private static final int DOWN_EVENT_MARKER = -128;

    static class GesturePreviewTrailParams {
        public final int mFadeoutStartDelay;
        public final int mFadeoutDuration;
        public final int mUpdateInterval;

        public GesturePreviewTrailParams(final TypedArray keyboardViewAttr) {
            mFadeoutStartDelay = keyboardViewAttr.getInt(
                    R.styleable.KeyboardView_gesturePreviewTrailFadeoutStartDelay, 0);
            mFadeoutDuration = keyboardViewAttr.getInt(
                    R.styleable.KeyboardView_gesturePreviewTrailFadeoutDuration, 0);
            mUpdateInterval = keyboardViewAttr.getInt(
                    R.styleable.KeyboardView_gesturePreviewTrailUpdateInterval, 0);
        }
    }

    public GesturePreviewTrail(final GesturePreviewTrailParams params) {
        mPreviewParams = params;
    }

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

    public void addStroke(final GestureStrokeWithPreviewTrail stroke, final long downTime) {
        final int strokeId = stroke.getGestureStrokeId();
        final boolean isNewStroke = strokeId != mCurrentStrokeId;
        final int trailSize = mEventTimes.getLength();
        stroke.appendPreviewStroke(mEventTimes, mXCoordinates, mYCoordinates);
        final int newTrailSize = mEventTimes.getLength();
        if (stroke.getGestureStrokePreviewSize() == 0) {
            return;
        }
        if (isNewStroke) {
            final int elapsedTime = (int)(downTime - mCurrentDownTime);
            final int[] eventTimes = mEventTimes.getPrimitiveArray();
            for (int i = 0; i < trailSize; i++) {
                eventTimes[i] -= elapsedTime;
            }

            if (newTrailSize > trailSize) {
                final int[] xCoords = mXCoordinates.getPrimitiveArray();
                xCoords[trailSize] = markAsDownEvent(xCoords[trailSize]);
            }
            mCurrentDownTime = downTime;
            mCurrentStrokeId = strokeId;
        }
    }

    private int getAlpha(final int elapsedTime) {
        if (elapsedTime < mPreviewParams.mFadeoutStartDelay) {
            return Constants.Color.ALPHA_OPAQUE;
        }
        final int decreasingAlpha = Constants.Color.ALPHA_OPAQUE
                * (elapsedTime - mPreviewParams.mFadeoutStartDelay)
                / mPreviewParams.mFadeoutDuration;
        return Constants.Color.ALPHA_OPAQUE - decreasingAlpha;
    }

    /**
     * Draw gesture preview trail
     * @param canvas The canvas to draw the gesture preview trail
     * @param paint The paint object to be used to draw the gesture preview trail
     * @return true if some gesture preview trails remain to be drawn
     */
    public boolean drawGestureTrail(final Canvas canvas, final Paint paint) {
        final int trailSize = mEventTimes.getLength();
        if (trailSize == 0) {
            return false;
        }

        final int[] eventTimes = mEventTimes.getPrimitiveArray();
        final int[] xCoords = mXCoordinates.getPrimitiveArray();
        final int[] yCoords = mYCoordinates.getPrimitiveArray();
        final int sinceDown = (int)(SystemClock.uptimeMillis() - mCurrentDownTime);
        final int lingeringDuration = mPreviewParams.mFadeoutStartDelay
                + mPreviewParams.mFadeoutDuration;
        int startIndex;
        for (startIndex = 0; startIndex < trailSize; startIndex++) {
            final int elapsedTime = sinceDown - eventTimes[startIndex];
            // Skip too old trail points.
            if (elapsedTime < lingeringDuration) {
                break;
            }
        }

        if (startIndex < trailSize) {
            int lastX = getXCoordValue(xCoords[startIndex]);
            int lastY = yCoords[startIndex];
            for (int i = startIndex + 1; i < trailSize - 1; i++) {
                final int x = xCoords[i];
                final int y = yCoords[i];
                final int elapsedTime = sinceDown - eventTimes[i];
                // Draw trail line only when the current point isn't a down point.
                if (!isDownEventXCoord(x)) {
                    paint.setAlpha(getAlpha(elapsedTime));
                    canvas.drawLine(lastX, lastY, x, y, paint);
                }
                lastX = getXCoordValue(x);
                lastY = y;
            }
        }

        // TODO: Implement ring buffer to avoid moving points.
        // Discard faded out points.
        final int newSize = trailSize - startIndex;
        System.arraycopy(eventTimes, startIndex, eventTimes, 0, newSize);
        System.arraycopy(xCoords, startIndex, xCoords, 0, newSize);
        System.arraycopy(yCoords, startIndex, yCoords, 0, newSize);
        mEventTimes.setLength(newSize);
        mXCoordinates.setLength(newSize);
        mYCoordinates.setLength(newSize);
        return newSize > 0;
    }
}
