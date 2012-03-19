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
    private static final int ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE = 2;

    private final int mKeyHysteresisDistanceSquared;

    private Keyboard mKeyboard;
    private int mCorrectionX;
    private int mCorrectionY;
    private boolean mProximityCorrectOn;
    private int mProximityThresholdSquare;

    // working area
    private static final int MAX_NEARBY_KEYS = 12;
    private final int[] mDistances = new int[MAX_NEARBY_KEYS];
    private final Key[] mNeighborKeys = new Key[MAX_NEARBY_KEYS];

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

    public int getTouchX(int x) {
        return x + mCorrectionX;
    }

    public int getTouchY(int y) {
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
     * Computes maximum size of the array that can contain all nearby key codes returned by
     * {@link #getNearbyCodes}.
     *
     * @return Returns maximum size of the array that can contain all nearby key codes returned
     *         by {@link #getNearbyCodes}.
     */
    protected int getMaxNearbyKeys() {
        return MAX_NEARBY_KEYS;
    }

    /**
     * Allocates array that can hold all key codes returned by {@link #getNearbyCodes}
     * method. The maximum size of the array should be computed by {@link #getMaxNearbyKeys}.
     *
     * @return Allocates and returns an array that can hold all key codes returned by
     *         {@link #getNearbyCodes} method. All elements in the returned array are
     *         initialized by {@link #NOT_A_CODE} value.
     */
    public int[] newCodeArray() {
        int[] codes = new int[getMaxNearbyKeys()];
        Arrays.fill(codes, NOT_A_CODE);
        return codes;
    }

    private void initializeNearbyKeys() {
        Arrays.fill(mDistances, Integer.MAX_VALUE);
        Arrays.fill(mNeighborKeys, null);
    }

    /**
     * Insert the key into nearby keys buffer and sort nearby keys by ascending order of distance.
     * If the distance of two keys are the same, the key which the point is on should be considered
     * as a closer one.
     *
     * @param key the key to be inserted into the nearby keys buffer.
     * @param distance distance between the key's edge and user touched point.
     * @param isOnKey true if the point is on the key.
     * @return order of the key in the nearby buffer, 0 if it is the nearest key.
     */
    private int sortNearbyKeys(Key key, int distance, boolean isOnKey) {
        final int[] distances = mDistances;
        final Key[] neighborKeys = mNeighborKeys;
        for (int insertPos = 0; insertPos < distances.length; insertPos++) {
            final int comparingDistance = distances[insertPos];
            if (distance < comparingDistance || (distance == comparingDistance && isOnKey)) {
                final int nextPos = insertPos + 1;
                if (nextPos < distances.length) {
                    System.arraycopy(distances, insertPos, distances, nextPos,
                            distances.length - nextPos);
                    System.arraycopy(neighborKeys, insertPos, neighborKeys, nextPos,
                            neighborKeys.length - nextPos);
                }
                distances[insertPos] = distance;
                neighborKeys[insertPos] = key;
                return insertPos;
            }
        }
        return distances.length;
    }

    private void getNearbyKeyCodes(final int primaryCode, final int[] allCodes) {
        final Key[] neighborKeys = mNeighborKeys;
        final int maxCodesSize = allCodes.length;

        // allCodes[0] should always have the key code even if it is a non-letter key.
        if (neighborKeys[0] == null) {
            allCodes[0] = NOT_A_CODE;
            return;
        }

        int numCodes = 0;
        for (int j = 0; j < neighborKeys.length && numCodes < maxCodesSize; j++) {
            final Key key = neighborKeys[j];
            if (key == null)
                break;
            final int code = key.mCode;
            // filter out a non-letter key from nearby keys
            if (code < Keyboard.CODE_SPACE)
                continue;
            allCodes[numCodes++] = code;
        }
        if (maxCodesSize <= numCodes) {
            return;
        }

        final int code = (primaryCode == NOT_A_CODE) ? allCodes[0] : primaryCode;
        if (code == NOT_A_CODE) {
            return;
        }
        final List<Integer> additionalChars = mKeyboard.getAdditionalProximityChars().get(code);
        if (additionalChars == null || additionalChars.size() == 0) {
            return;
        }
        int currentCodesSize = numCodes;
        allCodes[numCodes++] = ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE;
        if (maxCodesSize <= numCodes) {
            return;
        }
        // TODO: This is O(N^2). Assuming additionalChars.size() is up to 4 or 5.
        for (int i = 0; i < additionalChars.size(); ++i) {
            final int additionalChar = additionalChars.get(i);
            boolean contains = false;
            for (int j = 0; j < currentCodesSize; ++j) {
                if (additionalChar == allCodes[j]) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                allCodes[numCodes++] = additionalChar;
                if (maxCodesSize <= numCodes) {
                    return;
                }
            }
        }
    }

    /**
     * Finds all possible nearby key codes around a touch event point and returns the nearest key.
     * The algorithm to determine the nearby keys depends on the threshold set by
     * {@link #setProximityThreshold(int)} and the mode set by
     * {@link #setProximityCorrectionEnabled(boolean)}.
     *
     * @param x The x-coordinate of a touch point
     * @param y The y-coordinate of a touch point
     * @param allCodes All nearby key codes except functional key are returned in this array
     */
    // TODO: Move this method to native code.
    public void getNearbyCodes(int x, int y, final int[] allCodes) {
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

        initializeNearbyKeys();
        Key primaryKey = null;
        for (final Key key : mKeyboard.getNearestKeys(touchX, touchY)) {
            final boolean isOnKey = key.isOnKey(touchX, touchY);
            final int distance = key.squaredDistanceToEdge(touchX, touchY);
            if (isOnKey || (mProximityCorrectOn && distance < mProximityThresholdSquare)) {
                final int insertedPosition = sortNearbyKeys(key, distance, isOnKey);
                if (insertedPosition == 0 && isOnKey) {
                    primaryKey = key;
                }
            }
        }

        getNearbyKeyCodes(primaryKey != null ? primaryKey.mCode : NOT_A_CODE, allCodes);
        if (DEBUG) {
            Log.d(TAG, "x=" + x + " y=" + y
                    + " primary=" + printableCode(primaryKey)
                    + " codes=" + printableCodes(allCodes));
        }
    }

    /**
     * Detect the key whose hitbox the touch point is in.
     *
     * @param x The x-coordinate of a touch point
     * @param y The y-coordinate of a touch point
     * @return the key that the touch point hits.
     */
    public Key detectHitKey(int x, int y) {
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

        int minDistance = Integer.MAX_VALUE;
        Key primaryKey = null;
        for (final Key key: mKeyboard.getNearestKeys(touchX, touchY)) {
            final boolean isOnKey = key.isOnKey(touchX, touchY);
            final int distance = key.squaredDistanceToEdge(touchX, touchY);
            // To take care of hitbox overlaps, we compare mCode here too.
            if (primaryKey == null || distance < minDistance
                    || (distance == minDistance && isOnKey && key.mCode > primaryKey.mCode)) {
                minDistance = distance;
                primaryKey = key;
            }
        }
        return primaryKey;
    }

    public static String printableCode(Key key) {
        return key != null ? Keyboard.printableCode(key.mCode) : "none";
    }

    public static String printableCodes(int[] codes) {
        final StringBuilder sb = new StringBuilder();
        boolean addDelimiter = false;
        for (final int code : codes) {
            if (code == NOT_A_CODE) break;
            if (code == ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE) {
                sb.append(" | ");
                addDelimiter = false;
            } else {
                if (addDelimiter) sb.append(", ");
                sb.append(Keyboard.printableCode(code));
                addDelimiter = true;
            }
        }
        return "[" + sb + "]";
    }
}
