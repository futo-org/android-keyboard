/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard;

import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class KeyDetector {
    private static final String TAG = KeyDetector.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final int NOT_A_CODE = -1;
    public static final int NOT_A_KEY = -1;

    private final int mKeyHysteresisDistanceSquared;

    private Keyboard mKeyboard;
    private int mCorrectionX;
    private int mCorrectionY;
    private boolean mProximityCorrectOn;
    private int mProximityThresholdSquare;

    // working area
    private static final int MAX_NEARBY_KEYS = 12;
    private final int[] mDistances = new int[MAX_NEARBY_KEYS];
    private final int[] mIndices = new int[MAX_NEARBY_KEYS];

    /**
     * This class handles key detection.
     *
     * @param keyHysteresisDistance if the pointer movement distance is smaller than this, the
     * movement will not been handled as meaningful movement. The unit is pixel.
     */
    public KeyDetector(float keyHysteresisDistance) {
        mKeyHysteresisDistanceSquared = (int)(keyHysteresisDistance * keyHysteresisDistance);
    }

    public void setKeyboard(Keyboard keyboard, float correctionX, float correctionY) {
        if (keyboard == null)
            throw new NullPointerException();
        mCorrectionX = (int)correctionX;
        mCorrectionY = (int)correctionY;
        mKeyboard = keyboard;
        final int threshold = keyboard.mMostCommonKeyWidth;
        mProximityThresholdSquare = threshold * threshold;
    }

    public int getKeyHysteresisDistanceSquared() {
        return mKeyHysteresisDistanceSquared;
    }

    protected int getTouchX(int x) {
        return x + mCorrectionX;
    }

    protected int getTouchY(int y) {
        return y + mCorrectionY;
    }

    public Keyboard getKeyboard() {
        if (mKeyboard == null)
            throw new IllegalStateException("keyboard isn't set");
        return mKeyboard;
    }

    public void setProximityCorrectionEnabled(boolean enabled) {
        mProximityCorrectOn = enabled;
    }

    public boolean isProximityCorrectionEnabled() {
        return mProximityCorrectOn;
    }

    public void setProximityThreshold(int threshold) {
        mProximityThresholdSquare = threshold * threshold;
    }

    public boolean alwaysAllowsSlidingInput() {
        return false;
    }

    /**
     * Computes maximum size of the array that can contain all nearby key indices returned by
     * {@link #getKeyIndexAndNearbyCodes}.
     *
     * @return Returns maximum size of the array that can contain all nearby key indices returned
     *         by {@link #getKeyIndexAndNearbyCodes}.
     */
    protected int getMaxNearbyKeys() {
        return MAX_NEARBY_KEYS;
    }

    /**
     * Allocates array that can hold all key indices returned by {@link #getKeyIndexAndNearbyCodes}
     * method. The maximum size of the array should be computed by {@link #getMaxNearbyKeys}.
     *
     * @return Allocates and returns an array that can hold all key indices returned by
     *         {@link #getKeyIndexAndNearbyCodes} method. All elements in the returned array are
     *         initialized by {@link #NOT_A_CODE} value.
     */
    public int[] newCodeArray() {
        int[] codes = new int[getMaxNearbyKeys()];
        Arrays.fill(codes, NOT_A_CODE);
        return codes;
    }

    private void initializeNearbyKeys() {
        Arrays.fill(mDistances, Integer.MAX_VALUE);
        Arrays.fill(mIndices, NOT_A_KEY);
    }

    /**
     * Insert the key into nearby keys buffer and sort nearby keys by ascending order of distance.
     * If the distance of two keys are the same, the key which the point is on should be considered
     * as a closer one.
     *
     * @param keyIndex index of the key.
     * @param distance distance between the key's edge and user touched point.
     * @param isOnKey true if the point is on the key.
     * @return order of the key in the nearby buffer, 0 if it is the nearest key.
     */
    private int sortNearbyKeys(int keyIndex, int distance, boolean isOnKey) {
        final int[] distances = mDistances;
        final int[] indices = mIndices;
        for (int insertPos = 0; insertPos < distances.length; insertPos++) {
            final int comparingDistance = distances[insertPos];
            if (distance < comparingDistance || (distance == comparingDistance && isOnKey)) {
                final int nextPos = insertPos + 1;
                if (nextPos < distances.length) {
                    System.arraycopy(distances, insertPos, distances, nextPos,
                            distances.length - nextPos);
                    System.arraycopy(indices, insertPos, indices, nextPos,
                            indices.length - nextPos);
                }
                distances[insertPos] = distance;
                indices[insertPos] = keyIndex;
                return insertPos;
            }
        }
        return distances.length;
    }

    private void getNearbyKeyCodes(final int[] allCodes) {
        final List<Key> keys = getKeyboard().mKeys;
        final int[] indices = mIndices;

        // allCodes[0] should always have the key code even if it is a non-letter key.
        if (indices[0] == NOT_A_KEY) {
            allCodes[0] = NOT_A_CODE;
            return;
        }

        int numCodes = 0;
        for (int j = 0; j < indices.length && numCodes < allCodes.length; j++) {
            final int index = indices[j];
            if (index == NOT_A_KEY)
                break;
            final int code = keys.get(index).mCode;
            // filter out a non-letter key from nearby keys
            if (code < Keyboard.CODE_SPACE)
                continue;
            allCodes[numCodes++] = code;
        }
    }

    /**
     * Finds all possible nearby key indices around a touch event point and returns the nearest key
     * index. The algorithm to determine the nearby keys depends on the threshold set by
     * {@link #setProximityThreshold(int)} and the mode set by
     * {@link #setProximityCorrectionEnabled(boolean)}.
     *
     * @param x The x-coordinate of a touch point
     * @param y The y-coordinate of a touch point
     * @param allCodes All nearby key code except functional key are returned in this array
     * @return The nearest key index
     */
    public int getKeyIndexAndNearbyCodes(int x, int y, final int[] allCodes) {
        final List<Key> keys = getKeyboard().mKeys;
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

        initializeNearbyKeys();
        int primaryIndex = NOT_A_KEY;
        for (final int index : mKeyboard.getNearestKeys(touchX, touchY)) {
            final Key key = keys.get(index);
            final boolean isOnKey = key.isOnKey(touchX, touchY);
            final int distance = key.squaredDistanceToEdge(touchX, touchY);
            if (isOnKey || (mProximityCorrectOn && distance < mProximityThresholdSquare)) {
                final int insertedPosition = sortNearbyKeys(index, distance, isOnKey);
                if (insertedPosition == 0 && isOnKey)
                    primaryIndex = index;
            }
        }

        if (allCodes != null && allCodes.length > 0) {
            getNearbyKeyCodes(allCodes);
            if (DEBUG) {
                Log.d(TAG, "x=" + x + " y=" + y
                        + " primary="
                        + (primaryIndex == NOT_A_KEY ? "none" : keys.get(primaryIndex).mCode)
                        + " codes=" + Arrays.toString(allCodes));
            }
        }

        return primaryIndex;
    }
}
