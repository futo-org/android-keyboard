/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.graphics.drawable.Drawable;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeyboardParams {
    public KeyboardId mId;

    /** Total height and width of the keyboard, including the paddings and keys */
    public int mOccupiedHeight;
    public int mOccupiedWidth;

    /** Base height and width of the keyboard used to calculate rows' or keys' heights and widths */
    public int mBaseHeight;
    public int mBaseWidth;

    public int mTopPadding;
    public int mBottomPadding;
    public int mHorizontalEdgesPadding;
    public int mHorizontalCenterPadding;

    public int mDefaultRowHeight;
    public int mDefaultKeyWidth;
    public int mHorizontalGap;
    public int mVerticalGap;

    public boolean mIsRtlKeyboard;
    public int mMoreKeysTemplate;
    public int mMaxMiniKeyboardColumn;

    public int GRID_WIDTH;
    public int GRID_HEIGHT;

    public final List<Key> mKeys = new ArrayList<Key>();
    public final List<Key> mShiftKeys = new ArrayList<Key>();
    public final Set<Key> mShiftLockKeys = new HashSet<Key>();
    public final Map<Key, Drawable> mShiftedIcons = new HashMap<Key, Drawable>();
    public final Map<Key, Drawable> mUnshiftedIcons = new HashMap<Key, Drawable>();
    public final KeyboardIconsSet mIconsSet = new KeyboardIconsSet();

    public int mMostCommonKeyHeight = 0;
    public int mMostCommonKeyWidth = 0;

    protected void clearKeys() {
        mKeys.clear();
        mShiftKeys.clear();
        mShiftLockKeys.clear();
        mShiftedIcons.clear();
        mUnshiftedIcons.clear();
        clearHistogram();
    }

    public void onAddKey(Key key) {
        mKeys.add(key);
        updateHistogram(key);
        if (key.mCode == Keyboard.CODE_SHIFT) {
            mShiftKeys.add(key);
            if (key.isSticky()) {
                mShiftLockKeys.add(key);
            }
        }
    }

    public void addShiftedIcon(Key key, Drawable icon) {
        mUnshiftedIcons.put(key, key.getIcon());
        mShiftedIcons.put(key, icon);
    }

    private int mMaxHeightCount = 0;
    private int mMaxWidthCount = 0;
    private final Map<Integer, Integer> mHeightHistogram = new HashMap<Integer, Integer>();
    private final Map<Integer, Integer> mWidthHistogram = new HashMap<Integer, Integer>();

    private void clearHistogram() {
        mMostCommonKeyHeight = 0;
        mMaxHeightCount = 0;
        mHeightHistogram.clear();

        mMaxWidthCount = 0;
        mMostCommonKeyWidth = 0;
        mWidthHistogram.clear();
    }

    private static int updateHistogramCounter(Map<Integer, Integer> histogram, Integer key) {
        final int count = (histogram.containsKey(key) ? histogram.get(key) : 0) + 1;
        histogram.put(key, count);
        return count;
    }

    private void updateHistogram(Key key) {
        final Integer height = key.mHeight + key.mVerticalGap;
        final int heightCount = updateHistogramCounter(mHeightHistogram, height);
        if (heightCount > mMaxHeightCount) {
            mMaxHeightCount = heightCount;
            mMostCommonKeyHeight = height;
        }

        final Integer width = key.mWidth + key.mHorizontalGap;
        final int widthCount = updateHistogramCounter(mWidthHistogram, width);
        if (widthCount > mMaxWidthCount) {
            mMaxWidthCount = widthCount;
            mMostCommonKeyWidth = width;
        }
    }
}
