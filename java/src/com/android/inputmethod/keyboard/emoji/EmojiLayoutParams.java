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

package com.android.inputmethod.keyboard.emoji;

import android.content.res.Resources;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.LinearLayout;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.ResourceUtils;

final class EmojiLayoutParams {
    private static final int DEFAULT_KEYBOARD_ROWS = 4;

    public final int mEmojiPagerHeight;
    private final int mEmojiPagerBottomMargin;
    public final int mEmojiKeyboardHeight;
    private final int mEmojiCategoryPageIdViewHeight;
    public final int mEmojiActionBarHeight;
    public final int mKeyVerticalGap;
    private final int mKeyHorizontalGap;
    private final int mBottomPadding;
    private final int mTopPadding;

    public EmojiLayoutParams(final Resources res) {
        final int defaultKeyboardHeight = ResourceUtils.getDefaultKeyboardHeight(res);
        final int defaultKeyboardWidth = ResourceUtils.getDefaultKeyboardWidth(res);
        mKeyVerticalGap = (int) res.getFraction(R.fraction.config_key_vertical_gap_holo,
                defaultKeyboardHeight, defaultKeyboardHeight);
        mBottomPadding = (int) res.getFraction(R.fraction.config_keyboard_bottom_padding_holo,
                defaultKeyboardHeight, defaultKeyboardHeight);
        mTopPadding = (int) res.getFraction(R.fraction.config_keyboard_top_padding_holo,
                defaultKeyboardHeight, defaultKeyboardHeight);
        mKeyHorizontalGap = (int) (res.getFraction(R.fraction.config_key_horizontal_gap_holo,
                defaultKeyboardWidth, defaultKeyboardWidth));
        mEmojiCategoryPageIdViewHeight =
                (int) (res.getDimension(R.dimen.config_emoji_category_page_id_height));
        final int baseheight = defaultKeyboardHeight - mBottomPadding - mTopPadding
                + mKeyVerticalGap;
        mEmojiActionBarHeight = baseheight / DEFAULT_KEYBOARD_ROWS
                - (mKeyVerticalGap - mBottomPadding) / 2;
        mEmojiPagerHeight = defaultKeyboardHeight - mEmojiActionBarHeight
                - mEmojiCategoryPageIdViewHeight;
        mEmojiPagerBottomMargin = 0;
        mEmojiKeyboardHeight = mEmojiPagerHeight - mEmojiPagerBottomMargin - 1;
    }

    public void setPagerProperties(final ViewPager vp) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) vp.getLayoutParams();
        lp.height = mEmojiKeyboardHeight;
        lp.bottomMargin = mEmojiPagerBottomMargin;
        vp.setLayoutParams(lp);
    }

    public void setCategoryPageIdViewProperties(final View v) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.height = mEmojiCategoryPageIdViewHeight;
        v.setLayoutParams(lp);
    }

    public int getActionBarHeight() {
        return mEmojiActionBarHeight - mBottomPadding;
    }

    public void setActionBarProperties(final LinearLayout ll) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ll.getLayoutParams();
        lp.height = getActionBarHeight();
        ll.setLayoutParams(lp);
    }

    public void setKeyProperties(final View v) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.leftMargin = mKeyHorizontalGap / 2;
        lp.rightMargin = mKeyHorizontalGap / 2;
        v.setLayoutParams(lp);
    }
}
