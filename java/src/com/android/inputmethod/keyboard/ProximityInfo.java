/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import android.graphics.Rect;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.keyboard.internal.TouchPositionCorrection;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.JniUtils;

import java.util.Arrays;

public class ProximityInfo {
    private static final String TAG = ProximityInfo.class.getSimpleName();
    private static final boolean DEBUG = false;

    // Must be equal to MAX_PROXIMITY_CHARS_SIZE in native/jni/src/defines.h
    public static final int MAX_PROXIMITY_CHARS_SIZE = 16;
    /** Number of key widths from current touch point to search for nearest keys. */
    private static final float SEARCH_DISTANCE = 1.2f;
    private static final Key[] EMPTY_KEY_ARRAY = new Key[0];
    private static final float DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS = 0.15f;

    private final int mGridWidth;
    private final int mGridHeight;
    private final int mGridSize;
    private final int mCellWidth;
    private final int mCellHeight;
    // TODO: Find a proper name for mKeyboardMinWidth
    private final int mKeyboardMinWidth;
    private final int mKeyboardHeight;
    private final int mMostCommonKeyWidth;
    private final int mMostCommonKeyHeight;
    private final Key[] mKeys;
    private final Key[][] mGridNeighbors;
    private final String mLocaleStr;

    ProximityInfo(final String localeStr, final int gridWidth, final int gridHeight,
            final int minWidth, final int height, final int mostCommonKeyWidth,
            final int mostCommonKeyHeight, final Key[] keys,
            final TouchPositionCorrection touchPositionCorrection) {
        if (TextUtils.isEmpty(localeStr)) {
            mLocaleStr = "";
        } else {
            mLocaleStr = localeStr;
        }
        mGridWidth = gridWidth;
        mGridHeight = gridHeight;
        mGridSize = mGridWidth * mGridHeight;
        mCellWidth = (minWidth + mGridWidth - 1) / mGridWidth;
        mCellHeight = (height + mGridHeight - 1) / mGridHeight;
        mKeyboardMinWidth = minWidth;
        mKeyboardHeight = height;
        mMostCommonKeyHeight = mostCommonKeyHeight;
        mMostCommonKeyWidth = mostCommonKeyWidth;
        mKeys = keys;
        mGridNeighbors = new Key[mGridSize][];
        if (minWidth == 0 || height == 0) {
            // No proximity required. Keyboard might be more keys keyboard.
            return;
        }
        computeNearestNeighbors();
        mNativeProximityInfo = createNativeProximityInfo(touchPositionCorrection);
    }

    private long mNativeProximityInfo;
    static {
        JniUtils.loadNativeLibrary();
    }

    // TODO: Stop passing proximityCharsArray
    private static native long setProximityInfoNative(String locale,
            int displayWidth, int displayHeight, int gridWidth, int gridHeight,
            int mostCommonKeyWidth, int mostCommonKeyHeight, int[] proximityCharsArray,
            int keyCount, int[] keyXCoordinates, int[] keyYCoordinates, int[] keyWidths,
            int[] keyHeights, int[] keyCharCodes, float[] sweetSpotCenterXs,
            float[] sweetSpotCenterYs, float[] sweetSpotRadii);

    private static native void releaseProximityInfoNative(long nativeProximityInfo);

    private static boolean needsProximityInfo(final Key key) {
        // Don't include special keys into ProximityInfo.
        return key.mCode >= Constants.CODE_SPACE;
    }

    private static int getProximityInfoKeysCount(final Key[] keys) {
        int count = 0;
        for (final Key key : keys) {
            if (needsProximityInfo(key)) {
                count++;
            }
        }
        return count;
    }

    private long createNativeProximityInfo(final TouchPositionCorrection touchPositionCorrection) {
        final Key[][] gridNeighborKeys = mGridNeighbors;
        final int[] proximityCharsArray = new int[mGridSize * MAX_PROXIMITY_CHARS_SIZE];
        Arrays.fill(proximityCharsArray, Constants.NOT_A_CODE);
        for (int i = 0; i < mGridSize; ++i) {
            final int proximityCharsLength = gridNeighborKeys[i].length;
            int infoIndex = i * MAX_PROXIMITY_CHARS_SIZE;
            for (int j = 0; j < proximityCharsLength; ++j) {
                final Key neighborKey = gridNeighborKeys[i][j];
                // Excluding from proximityCharsArray
                if (!needsProximityInfo(neighborKey)) {
                    continue;
                }
                proximityCharsArray[infoIndex] = neighborKey.mCode;
                infoIndex++;
            }
        }
        if (DEBUG) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mGridSize; i++) {
                sb.setLength(0);
                for (int j = 0; j < MAX_PROXIMITY_CHARS_SIZE; j++) {
                    final int code = proximityCharsArray[i * MAX_PROXIMITY_CHARS_SIZE + j];
                    if (code == Constants.NOT_A_CODE) {
                        break;
                    }
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(Constants.printableCode(code));
                }
                Log.d(TAG, "proxmityChars["+i+"]: " + sb);
            }
        }

        final Key[] keys = mKeys;
        final int keyCount = getProximityInfoKeysCount(keys);
        final int[] keyXCoordinates = new int[keyCount];
        final int[] keyYCoordinates = new int[keyCount];
        final int[] keyWidths = new int[keyCount];
        final int[] keyHeights = new int[keyCount];
        final int[] keyCharCodes = new int[keyCount];
        final float[] sweetSpotCenterXs;
        final float[] sweetSpotCenterYs;
        final float[] sweetSpotRadii;

        for (int infoIndex = 0, keyIndex = 0; keyIndex < keys.length; keyIndex++) {
            final Key key = keys[keyIndex];
            // Excluding from key coordinate arrays
            if (!needsProximityInfo(key)) {
                continue;
            }
            keyXCoordinates[infoIndex] = key.mX;
            keyYCoordinates[infoIndex] = key.mY;
            keyWidths[infoIndex] = key.mWidth;
            keyHeights[infoIndex] = key.mHeight;
            keyCharCodes[infoIndex] = key.mCode;
            infoIndex++;
        }

        if (touchPositionCorrection != null && touchPositionCorrection.isValid()) {
            if (DEBUG) {
                Log.d(TAG, "touchPositionCorrection: ON");
            }
            sweetSpotCenterXs = new float[keyCount];
            sweetSpotCenterYs = new float[keyCount];
            sweetSpotRadii = new float[keyCount];
            final int rows = touchPositionCorrection.getRows();
            final float defaultRadius = DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS
                    * (float)Math.hypot(mMostCommonKeyWidth, mMostCommonKeyHeight);
            for (int infoIndex = 0, keyIndex = 0; keyIndex < keys.length; keyIndex++) {
                final Key key = keys[keyIndex];
                // Excluding from touch position correction arrays
                if (!needsProximityInfo(key)) {
                    continue;
                }
                final Rect hitBox = key.mHitBox;
                sweetSpotCenterXs[infoIndex] = hitBox.exactCenterX();
                sweetSpotCenterYs[infoIndex] = hitBox.exactCenterY();
                sweetSpotRadii[infoIndex] = defaultRadius;
                final int row = hitBox.top / mMostCommonKeyHeight;
                if (row < rows) {
                    final int hitBoxWidth = hitBox.width();
                    final int hitBoxHeight = hitBox.height();
                    final float hitBoxDiagonal = (float)Math.hypot(hitBoxWidth, hitBoxHeight);
                    sweetSpotCenterXs[infoIndex] +=
                            touchPositionCorrection.getX(row) * hitBoxWidth;
                    sweetSpotCenterYs[infoIndex] +=
                            touchPositionCorrection.getY(row) * hitBoxHeight;
                    sweetSpotRadii[infoIndex] =
                            touchPositionCorrection.getRadius(row) * hitBoxDiagonal;
                }
                if (DEBUG) {
                    Log.d(TAG, String.format(
                            "  [%2d] row=%d x/y/r=%7.2f/%7.2f/%5.2f %s code=%s", infoIndex, row,
                            sweetSpotCenterXs[infoIndex], sweetSpotCenterYs[infoIndex],
                            sweetSpotRadii[infoIndex], (row < rows ? "correct" : "default"),
                            Constants.printableCode(key.mCode)));
                }
                infoIndex++;
            }
        } else {
            sweetSpotCenterXs = sweetSpotCenterYs = sweetSpotRadii = null;
            if (DEBUG) {
                Log.d(TAG, "touchPositionCorrection: OFF");
            }
        }

        // TODO: Stop passing proximityCharsArray
        return setProximityInfoNative(mLocaleStr, mKeyboardMinWidth, mKeyboardHeight,
                mGridWidth, mGridHeight, mMostCommonKeyWidth, mMostCommonKeyHeight,
                proximityCharsArray, keyCount, keyXCoordinates, keyYCoordinates, keyWidths,
                keyHeights, keyCharCodes, sweetSpotCenterXs, sweetSpotCenterYs, sweetSpotRadii);
    }

    public long getNativeProximityInfo() {
        return mNativeProximityInfo;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mNativeProximityInfo != 0) {
                releaseProximityInfoNative(mNativeProximityInfo);
                mNativeProximityInfo = 0;
            }
        } finally {
            super.finalize();
        }
    }

    private void computeNearestNeighbors() {
        final int defaultWidth = mMostCommonKeyWidth;
        final Key[] keys = mKeys;
        final int thresholdBase = (int) (defaultWidth * SEARCH_DISTANCE);
        final int threshold = thresholdBase * thresholdBase;
        // Round-up so we don't have any pixels outside the grid
        final Key[] neighborKeys = new Key[keys.length];
        final int gridWidth = mGridWidth * mCellWidth;
        final int gridHeight = mGridHeight * mCellHeight;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                final int centerX = x + mCellWidth / 2;
                final int centerY = y + mCellHeight / 2;
                int count = 0;
                for (final Key key : keys) {
                    if (key.isSpacer()) continue;
                    if (key.squaredDistanceToEdge(centerX, centerY) < threshold) {
                        neighborKeys[count++] = key;
                    }
                }
                mGridNeighbors[(y / mCellHeight) * mGridWidth + (x / mCellWidth)] =
                        Arrays.copyOfRange(neighborKeys, 0, count);
            }
        }
    }

    public void fillArrayWithNearestKeyCodes(final int x, final int y, final int primaryKeyCode,
            final int[] dest) {
        final int destLength = dest.length;
        if (destLength < 1) {
            return;
        }
        int index = 0;
        if (primaryKeyCode > Constants.CODE_SPACE) {
            dest[index++] = primaryKeyCode;
        }
        final Key[] nearestKeys = getNearestKeys(x, y);
        for (Key key : nearestKeys) {
            if (index >= destLength) {
                break;
            }
            final int code = key.mCode;
            if (code <= Constants.CODE_SPACE) {
                break;
            }
            dest[index++] = code;
        }
        if (index < destLength) {
            dest[index] = Constants.NOT_A_CODE;
        }
    }

    public Key[] getNearestKeys(final int x, final int y) {
        if (mGridNeighbors == null) {
            return EMPTY_KEY_ARRAY;
        }
        if (x >= 0 && x < mKeyboardMinWidth && y >= 0 && y < mKeyboardHeight) {
            int index = (y / mCellHeight) * mGridWidth + (x / mCellWidth);
            if (index < mGridSize) {
                return mGridNeighbors[index];
            }
        }
        return EMPTY_KEY_ARRAY;
    }
}
