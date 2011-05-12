/*
 * Copyright (C) 2010 Google Inc.
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public abstract class KeyDetector {
    public static final int NOT_A_KEY = -1;
    public static final int NOT_A_CODE = -1;

    protected Keyboard mKeyboard;

    private Key[] mKeys;

    protected int mCorrectionX;

    protected int mCorrectionY;

    protected boolean mProximityCorrectOn;

    protected int mProximityThresholdSquare;

    public Key[] setKeyboard(Keyboard keyboard, float correctionX, float correctionY) {
        if (keyboard == null)
            throw new NullPointerException();
        mCorrectionX = (int)correctionX;
        mCorrectionY = (int)correctionY;
        mKeyboard = keyboard;
        List<Key> keys = mKeyboard.getKeys();
        Key[] array = keys.toArray(new Key[keys.size()]);
        mKeys = array;
        return array;
    }

    protected int getTouchX(int x) {
        return x + mCorrectionX;
    }

    protected int getTouchY(int y) {
        return y + mCorrectionY;
    }

    protected Key[] getKeys() {
        if (mKeys == null)
            throw new IllegalStateException("keyboard isn't set");
        // mKeyboard is guaranteed not to be null at setKeybaord() method if mKeys is not null
        return mKeys;
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

    /**
     * Computes maximum size of the array that can contain all nearby key indices returned by
     * {@link #getKeyIndexAndNearbyCodes}.
     *
     * @return Returns maximum size of the array that can contain all nearby key indices returned
     *         by {@link #getKeyIndexAndNearbyCodes}.
     */
    abstract protected int getMaxNearbyKeys();

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
    abstract public int getKeyIndexAndNearbyCodes(int x, int y, final int[] allCodes);

    /**
     * Compute the most common key width in order to use it as proximity key detection threshold.
     *
     * @param keyboard The keyboard to compute the most common key width
     * @return The most common key width in the keyboard
     */
    public static int getMostCommonKeyWidth(final Keyboard keyboard) {
        if (keyboard == null) return 0;
        final List<Key> keys = keyboard.getKeys();
        if (keys == null || keys.size() == 0) return 0;
        final HashMap<Integer, Integer> histogram = new HashMap<Integer, Integer>();
        int maxCount = 0;
        int mostCommonWidth = 0;
        for (final Key key : keys) {
            final Integer width = key.mWidth + key.mGap;
            Integer count = histogram.get(width);
            if (count == null)
                count = 0;
            histogram.put(width, ++count);
            if (count > maxCount) {
                maxCount = count;
                mostCommonWidth = width;
            }
        }
        return mostCommonWidth;
    }
}
