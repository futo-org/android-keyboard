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
import java.util.Random;

/**
 * This is a Keyboard class to host recently used keys.
 */
// TODO: Save/restore recent keys from/to preferences.
public class RecentsKeyboard extends Keyboard {
    private static final int TEMPLATE_KEY_CODE_0 = 0x30;
    private static final int TEMPLATE_KEY_CODE_1 = 0x31;

    private final int mLeftPadding;
    private final int mHorizontalStep;
    private final int mVerticalStep;
    private final int mColumnsNum;
    private final int mMaxRecentKeyCount;
    private final ArrayDeque<RecentKey> mRecentKeys = CollectionUtils.newArrayDeque();

    private Key[] mCachedRecentKeys;

    public RecentsKeyboard(final Keyboard templateKeyboard) {
        super(templateKeyboard);
        final Key key0 = getTemplateKey(TEMPLATE_KEY_CODE_0);
        final Key key1 = getTemplateKey(TEMPLATE_KEY_CODE_1);
        mLeftPadding = key0.getX();
        mHorizontalStep = Math.abs(key1.getX() - key0.getX());
        mVerticalStep = key0.getHeight() + mVerticalGap;
        mColumnsNum = mBaseWidth / mHorizontalStep;
        final int rowsNum = mBaseHeight / mVerticalStep;
        mMaxRecentKeyCount = mColumnsNum * rowsNum;
    }

    private Key getTemplateKey(final int code) {
        for (final Key key : super.getKeys()) {
            if (key.getCode() == code) {
                return key;
            }
        }
        throw new RuntimeException("Can't find template key: code=" + code);
    }

    private final Random random = new Random();

    public void addRecentKey(final Key usedKey) {
        synchronized (mRecentKeys) {
            mCachedRecentKeys = null;
            final RecentKey key = (usedKey instanceof RecentKey)
                    ? (RecentKey)usedKey : new RecentKey(usedKey);
            while (mRecentKeys.remove(key)) {
                // Remove duplicate keys.
            }
            mRecentKeys.addFirst(key);
            while (mRecentKeys.size() > mMaxRecentKeyCount) {
                mRecentKeys.removeLast();
            }
            int index = 0;
            for (final RecentKey recentKey : mRecentKeys) {
                final int keyX = getKeyX(index);
                final int keyY = getKeyY(index);
                final int x = keyX+random.nextInt(recentKey.getWidth());
                final int y = keyY+random.nextInt(recentKey.getHeight());
                recentKey.updateCorrdinates(keyX, keyY);
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
        synchronized (mRecentKeys) {
            if (mCachedRecentKeys != null) {
                return mCachedRecentKeys;
            }
            mCachedRecentKeys = mRecentKeys.toArray(new Key[mRecentKeys.size()]);
            return mCachedRecentKeys;
        }
    }

    @Override
    public Key[] getNearestKeys(final int x, final int y) {
        // TODO: Calculate the nearest key index in mRecentKeys from x and y.
        return getKeys();
    }

    static final class RecentKey extends Key {
        private int mCurrentX;
        private int mCurrentY;

        public RecentKey(final Key originalKey) {
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
            return "RecentKey: " + super.toString();
        }
    }
}
