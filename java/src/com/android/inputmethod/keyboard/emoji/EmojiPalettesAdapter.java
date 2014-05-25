/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.keyboard.emoji;

import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.latin.R;

final class EmojiPalettesAdapter extends PagerAdapter {
    private static final String TAG = EmojiPalettesAdapter.class.getSimpleName();
    private static final boolean DEBUG_PAGER = false;

    private final EmojiPageKeyboardView.OnKeyEventListener mListener;
    private final DynamicGridKeyboard mRecentsKeyboard;
    private final SparseArray<EmojiPageKeyboardView> mActiveKeyboardViews = new SparseArray<>();
    private final EmojiCategory mEmojiCategory;
    private int mActivePosition = 0;

    public EmojiPalettesAdapter(final EmojiCategory emojiCategory,
            final EmojiPageKeyboardView.OnKeyEventListener listener) {
        mEmojiCategory = emojiCategory;
        mListener = listener;
        mRecentsKeyboard = mEmojiCategory.getKeyboard(EmojiCategory.ID_RECENTS, 0);
    }

    public void flushPendingRecentKeys() {
        mRecentsKeyboard.flushPendingRecentKeys();
        final KeyboardView recentKeyboardView =
                mActiveKeyboardViews.get(mEmojiCategory.getRecentTabId());
        if (recentKeyboardView != null) {
            recentKeyboardView.invalidateAllKeys();
        }
    }

    public void addRecentKey(final Key key) {
        if (mEmojiCategory.isInRecentTab()) {
            mRecentsKeyboard.addPendingKey(key);
            return;
        }
        mRecentsKeyboard.addKeyFirst(key);
        final KeyboardView recentKeyboardView =
                mActiveKeyboardViews.get(mEmojiCategory.getRecentTabId());
        if (recentKeyboardView != null) {
            recentKeyboardView.invalidateAllKeys();
        }
    }

    public void onPageScrolled() {
        releaseCurrentKey(false /* withKeyRegistering */);
    }

    public void releaseCurrentKey(final boolean withKeyRegistering) {
        // Make sure the delayed key-down event (highlight effect and haptic feedback) will be
        // canceled.
        final EmojiPageKeyboardView currentKeyboardView =
                mActiveKeyboardViews.get(mActivePosition);
        if (currentKeyboardView == null) {
            return;
        }
        currentKeyboardView.releaseCurrentKey(withKeyRegistering);
    }

    @Override
    public int getCount() {
        return mEmojiCategory.getTotalPageCountOfAllCategories();
    }

    @Override
    public void setPrimaryItem(final ViewGroup container, final int position,
            final Object object) {
        if (mActivePosition == position) {
            return;
        }
        final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(mActivePosition);
        if (oldKeyboardView != null) {
            oldKeyboardView.releaseCurrentKey(false /* withKeyRegistering */);
            oldKeyboardView.deallocateMemory();
        }
        mActivePosition = position;
    }

    @Override
    public Object instantiateItem(final ViewGroup container, final int position) {
        if (DEBUG_PAGER) {
            Log.d(TAG, "instantiate item: " + position);
        }
        final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(position);
        if (oldKeyboardView != null) {
            oldKeyboardView.deallocateMemory();
            // This may be redundant but wanted to be safer..
            mActiveKeyboardViews.remove(position);
        }
        final Keyboard keyboard =
                mEmojiCategory.getKeyboardFromPagePosition(position);
        final LayoutInflater inflater = LayoutInflater.from(container.getContext());
        final EmojiPageKeyboardView keyboardView = (EmojiPageKeyboardView)inflater.inflate(
                R.layout.emoji_keyboard_page, container, false /* attachToRoot */);
        keyboardView.setKeyboard(keyboard);
        keyboardView.setOnKeyEventListener(mListener);
        container.addView(keyboardView);
        mActiveKeyboardViews.put(position, keyboardView);
        return keyboardView;
    }

    @Override
    public boolean isViewFromObject(final View view, final Object object) {
        return view == object;
    }

    @Override
    public void destroyItem(final ViewGroup container, final int position,
            final Object object) {
        if (DEBUG_PAGER) {
            Log.d(TAG, "destroy item: " + position + ", " + object.getClass().getSimpleName());
        }
        final EmojiPageKeyboardView keyboardView = mActiveKeyboardViews.get(position);
        if (keyboardView != null) {
            keyboardView.deallocateMemory();
            mActiveKeyboardViews.remove(position);
        }
        if (object instanceof View) {
            container.removeView((View)object);
        } else {
            Log.w(TAG, "Warning!!! Emoji palette may be leaking. " + object);
        }
    }
}
