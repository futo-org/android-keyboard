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

import android.content.res.ColorStateList;
import android.graphics.Typeface;

import com.android.inputmethod.latin.utils.ResourceUtils;

public final class KeyDrawParams {
    public Typeface mTypeface;

    public int mLetterSize;
    public int mLabelSize;
    public int mLargeLetterSize;
    public int mLargeLabelSize;
    public int mHintLetterSize;
    public int mShiftedLetterHintSize;
    public int mHintLabelSize;
    public int mPreviewTextSize;

    public ColorStateList mTextColorStateList;
    public int mTextInactivatedColor;
    public int mTextShadowColor;
    public int mHintLetterColor;
    public int mHintLabelColor;
    public int mShiftedLetterHintInactivatedColor;
    public int mShiftedLetterHintActivatedColor;
    public int mPreviewTextColor;

    public int mAnimAlpha;

    public KeyDrawParams() {}

    private KeyDrawParams(final KeyDrawParams copyFrom) {
        mTypeface = copyFrom.mTypeface;

        mLetterSize = copyFrom.mLetterSize;
        mLabelSize = copyFrom.mLabelSize;
        mLargeLetterSize = copyFrom.mLargeLetterSize;
        mLargeLabelSize = copyFrom.mLargeLabelSize;
        mHintLetterSize = copyFrom.mHintLetterSize;
        mShiftedLetterHintSize = copyFrom.mShiftedLetterHintSize;
        mHintLabelSize = copyFrom.mHintLabelSize;
        mPreviewTextSize = copyFrom.mPreviewTextSize;

        mTextColorStateList = copyFrom.mTextColorStateList;
        mTextInactivatedColor = copyFrom.mTextInactivatedColor;
        mTextShadowColor = copyFrom.mTextShadowColor;
        mHintLetterColor = copyFrom.mHintLetterColor;
        mHintLabelColor = copyFrom.mHintLabelColor;
        mShiftedLetterHintInactivatedColor = copyFrom.mShiftedLetterHintInactivatedColor;
        mShiftedLetterHintActivatedColor = copyFrom.mShiftedLetterHintActivatedColor;
        mPreviewTextColor = copyFrom.mPreviewTextColor;

        mAnimAlpha = copyFrom.mAnimAlpha;
    }

    public void updateParams(final int keyHeight, final KeyVisualAttributes attr) {
        if (attr == null) {
            return;
        }

        if (attr.mTypeface != null) {
            mTypeface = attr.mTypeface;
        }

        mLetterSize = selectTextSizeFromDimensionOrRatio(keyHeight,
                attr.mLetterSize, attr.mLetterRatio, mLetterSize);
        mLabelSize = selectTextSizeFromDimensionOrRatio(keyHeight,
                attr.mLabelSize, attr.mLabelRatio, mLabelSize);
        mLargeLabelSize = selectTextSize(keyHeight, attr.mLargeLabelRatio, mLargeLabelSize);
        mLargeLetterSize = selectTextSize(keyHeight, attr.mLargeLetterRatio, mLargeLetterSize);
        mHintLetterSize = selectTextSize(keyHeight, attr.mHintLetterRatio, mHintLetterSize);
        mShiftedLetterHintSize = selectTextSize(keyHeight,
                attr.mShiftedLetterHintRatio, mShiftedLetterHintSize);
        mHintLabelSize = selectTextSize(keyHeight, attr.mHintLabelRatio, mHintLabelSize);
        mPreviewTextSize = selectTextSize(keyHeight, attr.mPreviewTextRatio, mPreviewTextSize);
        mTextColorStateList =
                attr.mTextColorStateList != null ? attr.mTextColorStateList : mTextColorStateList;
        mTextInactivatedColor = selectColor(attr.mTextInactivatedColor, mTextInactivatedColor);
        mTextShadowColor = selectColor(attr.mTextShadowColor, mTextShadowColor);
        mHintLetterColor = selectColor(attr.mHintLetterColor, mHintLetterColor);
        mHintLabelColor = selectColor(attr.mHintLabelColor, mHintLabelColor);
        mShiftedLetterHintInactivatedColor = selectColor(
                attr.mShiftedLetterHintInactivatedColor, mShiftedLetterHintInactivatedColor);
        mShiftedLetterHintActivatedColor = selectColor(
                attr.mShiftedLetterHintActivatedColor, mShiftedLetterHintActivatedColor);
        mPreviewTextColor = selectColor(attr.mPreviewTextColor, mPreviewTextColor);
    }

    public KeyDrawParams mayCloneAndUpdateParams(final int keyHeight,
            final KeyVisualAttributes attr) {
        if (attr == null) {
            return this;
        }
        final KeyDrawParams newParams = new KeyDrawParams(this);
        newParams.updateParams(keyHeight, attr);
        return newParams;
    }

    private static final int selectTextSizeFromDimensionOrRatio(final int keyHeight,
            final int dimens, final float ratio, final int defaultDimens) {
        if (ResourceUtils.isValidDimensionPixelSize(dimens)) {
            return dimens;
        }
        if (ResourceUtils.isValidFraction(ratio)) {
            return (int)(keyHeight * ratio);
        }
        return defaultDimens;
    }

    private static final int selectTextSize(final int keyHeight, final float ratio,
            final int defaultSize) {
        if (ResourceUtils.isValidFraction(ratio)) {
            return (int)(keyHeight * ratio);
        }
        return defaultSize;
    }

    private static final int selectColor(final int attrColor, final int defaultColor) {
        if (attrColor != 0) {
            return attrColor;
        }
        return defaultColor;
    }
}
