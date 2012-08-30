/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResourceUtils;

public class KeyPreviewDrawParams {
    // XML attributes.
    public final Drawable mPreviewBackground;
    public final Drawable mPreviewLeftBackground;
    public final Drawable mPreviewRightBackground;
    public final int mPreviewTextColor;
    public final int mPreviewOffset;
    public final int mPreviewHeight;
    public final int mLingerTimeout;

    private final float mPreviewTextRatio;

    // The graphical geometry of the key preview.
    // <-width->
    // +-------+   ^
    // |       |   |
    // |preview| height (visible)
    // |       |   |
    // +       + ^ v
    //  \     /  |offset
    // +-\   /-+ v
    // |  +-+  |
    // |parent |
    // |    key|
    // +-------+
    // The background of a {@link TextView} being used for a key preview may have invisible
    // paddings. To align the more keys keyboard panel's visible part with the visible part of
    // the background, we need to record the width and height of key preview that don't include
    // invisible paddings.
    public int mPreviewVisibleWidth;
    public int mPreviewVisibleHeight;
    // The key preview may have an arbitrary offset and its background that may have a bottom
    // padding. To align the more keys keyboard and the key preview we also need to record the
    // offset between the top edge of parent key and the bottom of the visible part of key
    // preview background.
    public int mPreviewVisibleOffset;

    public Typeface mKeyTypeface;
    public int mPreviewTextSize;
    public int mKeyLetterSize;
    public final int[] mCoordinates = new int[2];

    private static final int PREVIEW_ALPHA = 240;

    public KeyPreviewDrawParams(final TypedArray keyboardViewAttr, final TypedArray keyAttr) {
        mPreviewBackground = keyboardViewAttr.getDrawable(
                R.styleable.KeyboardView_keyPreviewBackground);
        mPreviewLeftBackground = keyboardViewAttr.getDrawable(
                R.styleable.KeyboardView_keyPreviewLeftBackground);
        mPreviewRightBackground = keyboardViewAttr.getDrawable(
                R.styleable.KeyboardView_keyPreviewRightBackground);
        setAlpha(mPreviewBackground, PREVIEW_ALPHA);
        setAlpha(mPreviewLeftBackground, PREVIEW_ALPHA);
        setAlpha(mPreviewRightBackground, PREVIEW_ALPHA);
        mPreviewOffset = keyboardViewAttr.getDimensionPixelOffset(
                R.styleable.KeyboardView_keyPreviewOffset, 0);
        mPreviewHeight = keyboardViewAttr.getDimensionPixelSize(
                R.styleable.KeyboardView_keyPreviewHeight, 80);
        mLingerTimeout = keyboardViewAttr.getInt(
                R.styleable.KeyboardView_keyPreviewLingerTimeout, 0);

        mPreviewTextRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyPreviewTextRatio);
        mPreviewTextColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyPreviewTextColor, 0);
    }

    public void updateParams(final Keyboard keyboard, final KeyDrawParams keyDrawParams) {
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        if (ResourceUtils.isValidFraction(mPreviewTextRatio)) {
            mPreviewTextSize = (int)(keyHeight * mPreviewTextRatio);
        }
        mKeyLetterSize = keyDrawParams.mKeyLetterSize;
        mKeyTypeface = keyDrawParams.mKeyTypeface;
    }

    private static void setAlpha(final Drawable drawable, final int alpha) {
        if (drawable == null) return;
        drawable.setAlpha(alpha);
    }
}
