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
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.SparseIntArray;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.ResourceUtils;

public final class KeyVisualAttributes {
    public final Typeface mTypeface;

    public final float mLetterRatio;
    public final int mLetterSize;
    public final float mLabelRatio;
    public final int mLabelSize;
    public final float mLargeLetterRatio;
    public final float mLargeLabelRatio;
    public final float mHintLetterRatio;
    public final float mShiftedLetterHintRatio;
    public final float mHintLabelRatio;
    public final float mPreviewTextRatio;

    public final ColorStateList mTextColorStateList;
    public final int mTextInactivatedColor;
    public final int mTextShadowColor;
    public final int mHintLetterColor;
    public final int mHintLabelColor;
    public final int mShiftedLetterHintInactivatedColor;
    public final int mShiftedLetterHintActivatedColor;
    public final int mPreviewTextColor;

    private static final int[] VISUAL_ATTRIBUTE_IDS = {
        R.styleable.Keyboard_Key_keyTypeface,
        R.styleable.Keyboard_Key_keyLetterSize,
        R.styleable.Keyboard_Key_keyLabelSize,
        R.styleable.Keyboard_Key_keyLargeLetterRatio,
        R.styleable.Keyboard_Key_keyLargeLabelRatio,
        R.styleable.Keyboard_Key_keyHintLetterRatio,
        R.styleable.Keyboard_Key_keyShiftedLetterHintRatio,
        R.styleable.Keyboard_Key_keyHintLabelRatio,
        R.styleable.Keyboard_Key_keyPreviewTextRatio,
        R.styleable.Keyboard_Key_keyTextColor,
        R.styleable.Keyboard_Key_keyTextInactivatedColor,
        R.styleable.Keyboard_Key_keyTextShadowColor,
        R.styleable.Keyboard_Key_keyHintLetterColor,
        R.styleable.Keyboard_Key_keyHintLabelColor,
        R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor,
        R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor,
        R.styleable.Keyboard_Key_keyPreviewTextColor,
    };
    private static final SparseIntArray sVisualAttributeIds = new SparseIntArray();
    private static final int ATTR_DEFINED = 1;
    private static final int ATTR_NOT_FOUND = 0;
    static {
        for (final int attrId : VISUAL_ATTRIBUTE_IDS) {
            sVisualAttributeIds.put(attrId, ATTR_DEFINED);
        }
    }

    public static KeyVisualAttributes newInstance(final TypedArray keyAttr) {
        final int indexCount = keyAttr.getIndexCount();
        for (int i = 0; i < indexCount; i++) {
            final int attrId = keyAttr.getIndex(i);
            if (sVisualAttributeIds.get(attrId, ATTR_NOT_FOUND) == ATTR_NOT_FOUND) {
                continue;
            }
            return new KeyVisualAttributes(keyAttr);
        }
        return null;
    }

    private KeyVisualAttributes(final TypedArray keyAttr) {
        if (keyAttr.hasValue(R.styleable.Keyboard_Key_keyTypeface)) {
            mTypeface = Typeface.defaultFromStyle(
                    keyAttr.getInt(R.styleable.Keyboard_Key_keyTypeface, Typeface.NORMAL));
        } else {
            mTypeface = null;
        }

        mLetterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLetterSize);
        mLetterSize = ResourceUtils.getDimensionPixelSize(keyAttr,
                R.styleable.Keyboard_Key_keyLetterSize);
        mLabelRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLabelSize);
        mLabelSize = ResourceUtils.getDimensionPixelSize(keyAttr,
                R.styleable.Keyboard_Key_keyLabelSize);
        mLargeLetterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLargeLetterRatio);
        mLargeLabelRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLargeLabelRatio);
        mHintLetterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyHintLetterRatio);
        mShiftedLetterHintRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyShiftedLetterHintRatio);
        mHintLabelRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyHintLabelRatio);
        mPreviewTextRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyPreviewTextRatio);

        mTextColorStateList = keyAttr.getColorStateList(R.styleable.Keyboard_Key_keyTextColor);
        mTextInactivatedColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyTextInactivatedColor, 0);
        mTextShadowColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyTextShadowColor, 0);
        mHintLetterColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyHintLetterColor, 0);
        mHintLabelColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyHintLabelColor, 0);
        mShiftedLetterHintInactivatedColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor, 0);
        mShiftedLetterHintActivatedColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor, 0);
        mPreviewTextColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyPreviewTextColor, 0);
    }
}
