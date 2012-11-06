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

import com.android.inputmethod.latin.ResizableIntArray;

public final class GestureStrokeWithPreviewPoints extends GestureStroke {
    public static final int PREVIEW_CAPACITY = 256;

    private final ResizableIntArray mPreviewEventTimes = new ResizableIntArray(PREVIEW_CAPACITY);
    private final ResizableIntArray mPreviewXCoordinates = new ResizableIntArray(PREVIEW_CAPACITY);
    private final ResizableIntArray mPreviewYCoordinates = new ResizableIntArray(PREVIEW_CAPACITY);

    private int mStrokeId;
    private int mLastPreviewSize;

    private int mMinPreviewSampleLengthSquare;
    private int mLastX;
    private int mLastY;

    // TODO: Move this to resource.
    private static final float MIN_PREVIEW_SAMPLE_LENGTH_RATIO_TO_KEY_WIDTH = 0.1f;

    public GestureStrokeWithPreviewPoints(final int pointerId, final GestureStrokeParams params) {
        super(pointerId, params);
    }

    @Override
    protected void reset() {
        super.reset();
        mStrokeId++;
        mLastPreviewSize = 0;
        mPreviewEventTimes.setLength(0);
        mPreviewXCoordinates.setLength(0);
        mPreviewYCoordinates.setLength(0);
    }

    public int getGestureStrokeId() {
        return mStrokeId;
    }

    public int getGestureStrokePreviewSize() {
        return mPreviewEventTimes.getLength();
    }

    @Override
    public void setKeyboardGeometry(final int keyWidth) {
        super.setKeyboardGeometry(keyWidth);
        final float sampleLength = keyWidth * MIN_PREVIEW_SAMPLE_LENGTH_RATIO_TO_KEY_WIDTH;
        mMinPreviewSampleLengthSquare = (int)(sampleLength * sampleLength);
    }

    private boolean needsSampling(final int x, final int y) {
        final int dx = x - mLastX;
        final int dy = y - mLastY;
        return dx * dx + dy * dy >= mMinPreviewSampleLengthSquare;
    }

    @Override
    public void addPoint(final int x, final int y, final int time, final boolean isMajorEvent) {
        super.addPoint(x, y, time, isMajorEvent);
        if (isMajorEvent || needsSampling(x, y)) {
            mPreviewEventTimes.add(time);
            mPreviewXCoordinates.add(x);
            mPreviewYCoordinates.add(y);
            mLastX = x;
            mLastY = y;
        }
    }

    public void appendPreviewStroke(final ResizableIntArray eventTimes,
            final ResizableIntArray xCoords, final ResizableIntArray yCoords) {
        final int length = mPreviewEventTimes.getLength() - mLastPreviewSize;
        if (length <= 0) {
            return;
        }
        eventTimes.append(mPreviewEventTimes, mLastPreviewSize, length);
        xCoords.append(mPreviewXCoordinates, mLastPreviewSize, length);
        yCoords.append(mPreviewYCoordinates, mLastPreviewSize, length);
        mLastPreviewSize = mPreviewEventTimes.getLength();
    }
}
