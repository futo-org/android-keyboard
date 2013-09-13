/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.text.TextUtils;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.ArrayDeque;

/**
 * This is a Keyboard class where you can add keys dynamically shown in a grid layout
 */
// TODO: Save/restore recent keys from/to preferences.
public class DynamicGridKeyboard extends Keyboard {
    private static final int TEMPLATE_KEY_CODE_0 = 0x30;
    private static final int TEMPLATE_KEY_CODE_1 = 0x31;

    private final int mLeftPadding;
    private final int mHorizontalStep;
    private final int mVerticalStep;
    private final int mColumnsNum;
    private final int mMaxKeyCount;
    private final ArrayDeque<GridKey> mGridKeys = CollectionUtils.newArrayDeque();

    private Key[] mCachedGridKeys;

    public DynamicGridKeyboard(final Keyboard templateKeyboard, final int maxKeyCount) {
        super(templateKeyboard);
        final Key key0 = getTemplateKey(TEMPLATE_KEY_CODE_0);
        final Key key1 = getTemplateKey(TEMPLATE_KEY_CODE_1);
        mLeftPadding = key0.getX();
        mHorizontalStep = Math.abs(key1.getX() - key0.getX());
        mVerticalStep = key0.getHeight() + mVerticalGap;
        mColumnsNum = mBaseWidth / mHorizontalStep;
        mMaxKeyCount = maxKeyCount;
    }

    private Key getTemplateKey(final int code) {
        for (final Key key : super.getKeys()) {
            if (key.getCode() == code) {
                return key;
            }
        }
        throw new RuntimeException("Can't find template key: code=" + code);
    }

    public void addKeyFirst(final Key usedKey) {
        addKey(usedKey, true);
    }

    public void addKeyLast(final Key usedKey) {
        addKey(usedKey, false);
    }

    private void addKey(final Key usedKey, final boolean addFirst) {
        synchronized (mGridKeys) {
            mCachedGridKeys = null;
            final GridKey key = new GridKey(usedKey);
            while (mGridKeys.remove(key)) {
                // Remove duplicate keys.
            }
            if (addFirst) {
                mGridKeys.addFirst(key);
            } else {
                mGridKeys.addLast(key);
            }
            while (mGridKeys.size() > mMaxKeyCount) {
                mGridKeys.removeLast();
            }
            int index = 0;
            for (final GridKey gridKey : mGridKeys) {
                final int keyX = getKeyX(index);
                final int keyY = getKeyY(index);
                gridKey.updateCorrdinates(keyX, keyY);
                index++;
            }
        }
    }

    private int getKeyX(final int index) {
        final int column = index % mColumnsNum;
        return column * mHorizontalStep + mLeftPadding;
    }

    private int getKeyY(final int index) {
        final int row = index / mColumnsNum;
        return row * mVerticalStep + mTopPadding;
    }

    @Override
    public Key[] getKeys() {
        synchronized (mGridKeys) {
            if (mCachedGridKeys != null) {
                return mCachedGridKeys;
            }
            mCachedGridKeys = mGridKeys.toArray(new Key[mGridKeys.size()]);
            return mCachedGridKeys;
        }
    }

    @Override
    public Key[] getNearestKeys(final int x, final int y) {
        // TODO: Calculate the nearest key index in mGridKeys from x and y.
        return getKeys();
    }

    static final class GridKey extends Key {
        private int mCurrentX;
        private int mCurrentY;

        public GridKey(final Key originalKey) {
            super(originalKey);
        }

        public void updateCorrdinates(final int x, final int y) {
            mCurrentX = x;
            mCurrentY = y;
            getHitBox().offsetTo(x, y);
        }

        @Override
        public int getX() {
            return mCurrentX;
        }

        @Override
        public int getY() {
            return mCurrentY;
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Key)) return false;
            final Key key = (Key)o;
            if (getCode() != key.getCode()) return false;
            if (!TextUtils.equals(getLabel(), key.getLabel())) return false;
            return TextUtils.equals(getOutputText(), key.getOutputText());
        }

        @Override
        public String toString() {
            return "GridKey: " + super.toString();
        }
    }
}
