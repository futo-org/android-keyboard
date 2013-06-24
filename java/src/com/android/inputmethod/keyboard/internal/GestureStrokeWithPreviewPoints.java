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

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.ResizableIntArray;

public final class GestureStrokeWithPreviewPoints extends GestureStroke {
    public static final int PREVIEW_CAPACITY = 256;

    private final ResizableIntArray mPreviewEventTimes = new ResizableIntArray(PREVIEW_CAPACITY);
    private final ResizableIntArray mPreviewXCoordinates = new ResizableIntArray(PREVIEW_CAPACITY);
    private final ResizableIntArray mPreviewYCoordinates = new ResizableIntArray(PREVIEW_CAPACITY);

    private final GestureStrokePreviewParams mPreviewParams;

    private int mStrokeId;
    private int mLastPreviewSize;
    private final HermiteInterpolator mInterpolator = new HermiteInterpolator();
    private int mLastInterpolatedPreviewIndex;

    private int mLastX;
    private int mLastY;
    private double mDistanceFromLastSample;

    public static final class GestureStrokePreviewParams {
        public final double mMinSamplingDistance; // in pixel
        public final double mMaxInterpolationAngularThreshold; // in radian
        public final double mMaxInterpolationDistanceThreshold; // in pixel
        public final int mMaxInterpolationSegments;

        public static final GestureStrokePreviewParams DEFAULT = new GestureStrokePreviewParams();

        private static final int DEFAULT_MAX_INTERPOLATION_ANGULAR_THRESHOLD = 15; // in degree

        private GestureStrokePreviewParams() {
            mMinSamplingDistance = 0.0d;
            mMaxInterpolationAngularThreshold =
                    degreeToRadian(DEFAULT_MAX_INTERPOLATION_ANGULAR_THRESHOLD);
            mMaxInterpolationDistanceThreshold = mMinSamplingDistance;
            mMaxInterpolationSegments = 4;
        }

        private static double degreeToRadian(final int degree) {
            return degree / 180.0d * Math.PI;
        }

        public GestureStrokePreviewParams(final TypedArray mainKeyboardViewAttr) {
            mMinSamplingDistance = mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_gestureTrailMinSamplingDistance,
                    (float)DEFAULT.mMinSamplingDistance);
            final int interpolationAngularDegree = mainKeyboardViewAttr.getInteger(R.styleable
                    .MainKeyboardView_gestureTrailMaxInterpolationAngularThreshold, 0);
            mMaxInterpolationAngularThreshold = (interpolationAngularDegree <= 0)
                    ? DEFAULT.mMaxInterpolationAngularThreshold
                    : degreeToRadian(interpolationAngularDegree);
            mMaxInterpolationDistanceThreshold = mainKeyboardViewAttr.getDimension(R.styleable
                    .MainKeyboardView_gestureTrailMaxInterpolationDistanceThreshold,
                    (float)DEFAULT.mMaxInterpolationDistanceThreshold);
            mMaxInterpolationSegments = mainKeyboardViewAttr.getInteger(
                    R.styleable.MainKeyboardView_gestureTrailMaxInterpolationSegments,
                    DEFAULT.mMaxInterpolationSegments);
        }
    }

    public GestureStrokeWithPreviewPoints(final int pointerId,
            final GestureStrokeParams strokeParams,
            final GestureStrokePreviewParams previewParams) {
        super(pointerId, strokeParams);
        mPreviewParams = previewParams;
    }

    @Override
    protected void reset() {
        super.reset();
        mStrokeId++;
        mLastPreviewSize = 0;
        mLastInterpolatedPreviewIndex = 0;
        mPreviewEventTimes.setLength(0);
        mPreviewXCoordinates.setLength(0);
        mPreviewYCoordinates.setLength(0);
    }

    public int getGestureStrokeId() {
        return mStrokeId;
    }

    private boolean needsSampling(final int x, final int y) {
        mDistanceFromLastSample += Math.hypot(x - mLastX, y - mLastY);
        mLastX = x;
        mLastY = y;
        final boolean isDownEvent = (mPreviewEventTimes.getLength() == 0);
        if (mDistanceFromLastSample >= mPreviewParams.mMinSamplingDistance || isDownEvent) {
            mDistanceFromLastSample = 0.0d;
            return true;
        }
        return false;
    }

    @Override
    public boolean addPointOnKeyboard(final int x, final int y, final int time,
            final boolean isMajorEvent) {
        if (needsSampling(x, y)) {
            mPreviewEventTimes.add(time);
            mPreviewXCoordinates.add(x);
            mPreviewYCoordinates.add(y);
        }
        return super.addPointOnKeyboard(x, y, time, isMajorEvent);

    }

    /**
     * Append sampled preview points.
     *
     * @param eventTimes the event time array of gesture trail to be drawn.
     * @param xCoords the x-coordinates array of gesture trail to be drawn.
     * @param yCoords the y-coordinates array of gesture trail to be drawn.
     * @param types the point types array of gesture trail. This is valid only when
     * {@link GestureTrail#DEBUG_SHOW_POINTS} is true.
     */
    public void appendPreviewStroke(final ResizableIntArray eventTimes,
            final ResizableIntArray xCoords, final ResizableIntArray yCoords,
            final ResizableIntArray types) {
        final int length = mPreviewEventTimes.getLength() - mLastPreviewSize;
        if (length <= 0) {
            return;
        }
        eventTimes.append(mPreviewEventTimes, mLastPreviewSize, length);
        xCoords.append(mPreviewXCoordinates, mLastPreviewSize, length);
        yCoords.append(mPreviewYCoordinates, mLastPreviewSize, length);
        if (GestureTrail.DEBUG_SHOW_POINTS) {
            types.fill(GestureTrail.POINT_TYPE_SAMPLED, types.getLength(), length);
        }
        mLastPreviewSize = mPreviewEventTimes.getLength();
    }

    /**
     * Calculate interpolated points between the last interpolated point and the end of the trail.
     * And return the start index of the last interpolated segment of input arrays because it
     * may need to recalculate the interpolated points in the segment if further segments are
     * added to this stroke.
     *
     * @param lastInterpolatedIndex the start index of the last interpolated segment of
     *        <code>eventTimes</code>, <code>xCoords</code>, and <code>yCoords</code>.
     * @param eventTimes the event time array of gesture trail to be drawn.
     * @param xCoords the x-coordinates array of gesture trail to be drawn.
     * @param yCoords the y-coordinates array of gesture trail to be drawn.
     * @param types the point types array of gesture trail. This is valid only when
     * {@link GestureTrail#DEBUG_SHOW_POINTS} is true.
     * @return the start index of the last interpolated segment of input arrays.
     */
    public int interpolateStrokeAndReturnStartIndexOfLastSegment(final int lastInterpolatedIndex,
            final ResizableIntArray eventTimes, final ResizableIntArray xCoords,
            final ResizableIntArray yCoords, final ResizableIntArray types) {
        final int size = mPreviewEventTimes.getLength();
        final int[] pt = mPreviewEventTimes.getPrimitiveArray();
        final int[] px = mPreviewXCoordinates.getPrimitiveArray();
        final int[] py = mPreviewYCoordinates.getPrimitiveArray();
        mInterpolator.reset(px, py, 0, size);
        // The last segment of gesture stroke needs to be interpolated again because the slope of
        // the tangent at the last point isn't determined.
        int lastInterpolatedDrawIndex = lastInterpolatedIndex;
        int d1 = lastInterpolatedIndex;
        for (int p2 = mLastInterpolatedPreviewIndex + 1; p2 < size; p2++) {
            final int p1 = p2 - 1;
            final int p0 = p1 - 1;
            final int p3 = p2 + 1;
            mLastInterpolatedPreviewIndex = p1;
            lastInterpolatedDrawIndex = d1;
            mInterpolator.setInterval(p0, p1, p2, p3);
            final double m1 = Math.atan2(mInterpolator.mSlope1Y, mInterpolator.mSlope1X);
            final double m2 = Math.atan2(mInterpolator.mSlope2Y, mInterpolator.mSlope2X);
            final double deltaAngle = Math.abs(angularDiff(m2, m1));
            final int segmentsByAngle = (int)Math.ceil(
                    deltaAngle / mPreviewParams.mMaxInterpolationAngularThreshold);
            final double deltaDistance = Math.hypot(mInterpolator.mP1X - mInterpolator.mP2X,
                    mInterpolator.mP1Y - mInterpolator.mP2Y);
            final int segmentsByDistance = (int)Math.ceil(deltaDistance
                    / mPreviewParams.mMaxInterpolationDistanceThreshold);
            final int segments = Math.min(mPreviewParams.mMaxInterpolationSegments,
                    Math.max(segmentsByAngle, segmentsByDistance));
            final int t1 = eventTimes.get(d1);
            final int dt = pt[p2] - pt[p1];
            d1++;
            for (int i = 1; i < segments; i++) {
                final float t = i / (float)segments;
                mInterpolator.interpolate(t);
                eventTimes.add(d1, (int)(dt * t) + t1);
                xCoords.add(d1, (int)mInterpolator.mInterpolatedX);
                yCoords.add(d1, (int)mInterpolator.mInterpolatedY);
                if (GestureTrail.DEBUG_SHOW_POINTS) {
                    types.add(d1, GestureTrail.POINT_TYPE_INTERPOLATED);
                }
                d1++;
            }
            eventTimes.add(d1, pt[p2]);
            xCoords.add(d1, px[p2]);
            yCoords.add(d1, py[p2]);
            if (GestureTrail.DEBUG_SHOW_POINTS) {
                types.add(d1, GestureTrail.POINT_TYPE_SAMPLED);
            }
        }
        return lastInterpolatedDrawIndex;
    }

    private static final double TWO_PI = Math.PI * 2.0d;

    /**
     * Calculate the angular of rotation from <code>a0</code> to <code>a1</code>.
     *
     * @param a1 the angular to which the rotation ends.
     * @param a0 the angular from which the rotation starts.
     * @return the angular rotation value from a0 to a1, normalized to [-PI, +PI].
     */
    private static double angularDiff(final double a1, final double a0) {
        double deltaAngle = a1 - a0;
        while (deltaAngle > Math.PI) {
            deltaAngle -= TWO_PI;
        }
        while (deltaAngle < -Math.PI) {
            deltaAngle += TWO_PI;
        }
        return deltaAngle;
    }
}
