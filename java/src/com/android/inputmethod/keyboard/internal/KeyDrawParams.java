/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import com.android.inputmethod.latin.R;

public class KeyDrawParams {
    // XML attributes
    public final int mKeyTextColor;
    public final int mKeyTextInactivatedColor;
    public final Typeface mKeyTextStyle;
    public final float mKeyLabelHorizontalPadding;
    public final float mKeyHintLetterPadding;
    public final float mKeyPopupHintLetterPadding;
    public final float mKeyShiftedLetterHintPadding;
    public final int mShadowColor;
    public final float mShadowRadius;
    public final Drawable mKeyBackground;
    public final int mKeyHintLetterColor;
    public final int mKeyHintLabelColor;
    public final int mKeyShiftedLetterHintInactivatedColor;
    public final int mKeyShiftedLetterHintActivatedColor;

    public final float mKeyLetterRatio;
    private final float mKeyLargeLetterRatio;
    private final float mKeyLabelRatio;
    private final float mKeyLargeLabelRatio;
    private final float mKeyHintLetterRatio;
    private final float mKeyShiftedLetterHintRatio;
    private final float mKeyHintLabelRatio;
    private static final float UNDEFINED_RATIO = -1.0f;

    public final Rect mPadding = new Rect();
    public int mKeyLetterSize;
    public int mKeyLargeLetterSize;
    public int mKeyLabelSize;
    public int mKeyLargeLabelSize;
    public int mKeyHintLetterSize;
    public int mKeyShiftedLetterHintSize;
    public int mKeyHintLabelSize;
    public int mAnimAlpha;

    private static final int ALPHA_OPAQUE = 255;

    public KeyDrawParams(TypedArray a) {
        mKeyBackground = a.getDrawable(R.styleable.KeyboardView_keyBackground);
        if (a.hasValue(R.styleable.KeyboardView_keyLetterSize)) {
            mKeyLetterRatio = UNDEFINED_RATIO;
            mKeyLetterSize = a.getDimensionPixelSize(R.styleable.KeyboardView_keyLetterSize, 0);
        } else {
            mKeyLetterRatio = getRatio(a, R.styleable.KeyboardView_keyLetterRatio);
        }
        if (a.hasValue(R.styleable.KeyboardView_keyLabelSize)) {
            mKeyLabelRatio = UNDEFINED_RATIO;
            mKeyLabelSize = a.getDimensionPixelSize(R.styleable.KeyboardView_keyLabelSize, 0);
        } else {
            mKeyLabelRatio = getRatio(a, R.styleable.KeyboardView_keyLabelRatio);
        }
        mKeyLargeLabelRatio = getRatio(a, R.styleable.KeyboardView_keyLargeLabelRatio);
        mKeyLargeLetterRatio = getRatio(a, R.styleable.KeyboardView_keyLargeLetterRatio);
        mKeyHintLetterRatio = getRatio(a, R.styleable.KeyboardView_keyHintLetterRatio);
        mKeyShiftedLetterHintRatio = getRatio(a,
                R.styleable.KeyboardView_keyShiftedLetterHintRatio);
        mKeyHintLabelRatio = getRatio(a, R.styleable.KeyboardView_keyHintLabelRatio);
        mKeyLabelHorizontalPadding = a.getDimension(
                R.styleable.KeyboardView_keyLabelHorizontalPadding, 0);
        mKeyHintLetterPadding = a.getDimension(
                R.styleable.KeyboardView_keyHintLetterPadding, 0);
        mKeyPopupHintLetterPadding = a.getDimension(
                R.styleable.KeyboardView_keyPopupHintLetterPadding, 0);
        mKeyShiftedLetterHintPadding = a.getDimension(
                R.styleable.KeyboardView_keyShiftedLetterHintPadding, 0);
        mKeyTextColor = a.getColor(R.styleable.KeyboardView_keyTextColor, 0xFF000000);
        mKeyTextInactivatedColor = a.getColor(
                R.styleable.KeyboardView_keyTextInactivatedColor, 0xFF000000);
        mKeyHintLetterColor = a.getColor(R.styleable.KeyboardView_keyHintLetterColor, 0);
        mKeyHintLabelColor = a.getColor(R.styleable.KeyboardView_keyHintLabelColor, 0);
        mKeyShiftedLetterHintInactivatedColor = a.getColor(
                R.styleable.KeyboardView_keyShiftedLetterHintInactivatedColor, 0);
        mKeyShiftedLetterHintActivatedColor = a.getColor(
                R.styleable.KeyboardView_keyShiftedLetterHintActivatedColor, 0);
        mKeyTextStyle = Typeface.defaultFromStyle(
                a.getInt(R.styleable.KeyboardView_keyTextStyle, Typeface.NORMAL));
        mShadowColor = a.getColor(R.styleable.KeyboardView_shadowColor, 0);
        mShadowRadius = a.getFloat(R.styleable.KeyboardView_shadowRadius, 0f);

        mKeyBackground.getPadding(mPadding);
    }

    public void updateKeyHeight(int keyHeight) {
        if (mKeyLetterRatio >= 0.0f)
            mKeyLetterSize = (int)(keyHeight * mKeyLetterRatio);
        if (mKeyLabelRatio >= 0.0f)
            mKeyLabelSize = (int)(keyHeight * mKeyLabelRatio);
        mKeyLargeLabelSize = (int)(keyHeight * mKeyLargeLabelRatio);
        mKeyLargeLetterSize = (int)(keyHeight * mKeyLargeLetterRatio);
        mKeyHintLetterSize = (int)(keyHeight * mKeyHintLetterRatio);
        mKeyShiftedLetterHintSize = (int)(keyHeight * mKeyShiftedLetterHintRatio);
        mKeyHintLabelSize = (int)(keyHeight * mKeyHintLabelRatio);
    }

    public void blendAlpha(Paint paint) {
        final int color = paint.getColor();
        paint.setARGB((paint.getAlpha() * mAnimAlpha) / ALPHA_OPAQUE,
                Color.red(color), Color.green(color), Color.blue(color));
    }

    // Read fraction value in TypedArray as float.
    private static float getRatio(TypedArray a, int index) {
        return a.getFraction(index, 1000, 1000, 1) / 1000.0f;
    }
}