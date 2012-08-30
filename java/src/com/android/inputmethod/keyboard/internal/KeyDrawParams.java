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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResourceUtils;

public class KeyDrawParams {
    // XML attributes
    public final int mKeyTextColor;
    public final int mKeyTextInactivatedColor;
    public final float mKeyLabelHorizontalPadding;
    public final float mKeyHintLetterPadding;
    public final float mKeyPopupHintLetterPadding;
    public final float mKeyShiftedLetterHintPadding;
    public final int mKeyTextShadowColor;
    public final float mKeyTextShadowRadius;
    public final Drawable mKeyBackground;
    public final int mKeyHintLetterColor;
    public final int mKeyHintLabelColor;
    public final int mKeyShiftedLetterHintInactivatedColor;
    public final int mKeyShiftedLetterHintActivatedColor;

    private final Typeface mKeyTypefaceFromKeyboardView;
    private final float mKeyLetterRatio;
    private final int mKeyLetterSizeFromKeyboardView;
    private final float mKeyLargeLetterRatio;
    private final float mKeyLabelRatio;
    private final float mKeyLargeLabelRatio;
    private final float mKeyHintLetterRatio;
    private final float mKeyShiftedLetterHintRatio;
    private final float mKeyHintLabelRatio;

    public final Rect mPadding = new Rect();
    public Typeface mKeyTypeface;
    public int mKeyLetterSize;
    public int mKeyLargeLetterSize;
    public int mKeyLabelSize;
    public int mKeyLargeLabelSize;
    public int mKeyHintLetterSize;
    public int mKeyShiftedLetterHintSize;
    public int mKeyHintLabelSize;
    public int mAnimAlpha;

    public KeyDrawParams(final TypedArray keyboardViewAttr, final TypedArray keyAttr) {
        mKeyBackground = keyboardViewAttr.getDrawable(R.styleable.KeyboardView_keyBackground);
        mKeyBackground.getPadding(mPadding);

        mKeyLetterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLetterSize);
        mKeyLetterSizeFromKeyboardView = ResourceUtils.getDimensionPixelSize(keyAttr,
                R.styleable.Keyboard_Key_keyLetterSize);
        mKeyLabelRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLabelSize);
        mKeyLabelSize = ResourceUtils.getDimensionPixelSize(keyAttr,
                R.styleable.Keyboard_Key_keyLabelSize);
        mKeyLargeLabelRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLargeLabelRatio);
        mKeyLargeLetterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLargeLetterRatio);
        mKeyHintLetterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyHintLetterRatio);
        mKeyShiftedLetterHintRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyShiftedLetterHintRatio);
        mKeyHintLabelRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyHintLabelRatio);
        mKeyLabelHorizontalPadding = keyAttr.getDimension(
                R.styleable.Keyboard_Key_keyLabelHorizontalPadding, 0);
        mKeyHintLetterPadding = keyAttr.getDimension(
                R.styleable.Keyboard_Key_keyHintLetterPadding, 0);
        mKeyPopupHintLetterPadding = keyAttr.getDimension(
                R.styleable.Keyboard_Key_keyPopupHintLetterPadding, 0);
        mKeyShiftedLetterHintPadding = keyAttr.getDimension(
                R.styleable.Keyboard_Key_keyShiftedLetterHintPadding, 0);
        mKeyTextColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyTextColor, Color.WHITE);
        mKeyTextInactivatedColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyTextInactivatedColor, Color.WHITE);
        mKeyHintLetterColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyHintLetterColor, Color.TRANSPARENT);
        mKeyHintLabelColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyHintLabelColor, Color.TRANSPARENT);
        mKeyShiftedLetterHintInactivatedColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor, Color.TRANSPARENT);
        mKeyShiftedLetterHintActivatedColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor, Color.TRANSPARENT);
        mKeyTypefaceFromKeyboardView = Typeface.defaultFromStyle(
                keyAttr.getInt(R.styleable.Keyboard_Key_keyTypeface, Typeface.NORMAL));
        mKeyTextShadowColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyTextShadowColor, Color.TRANSPARENT);
        mKeyTextShadowRadius = keyAttr.getFloat(
                R.styleable.Keyboard_Key_keyTextShadowRadius, 0f);
    }

    public void updateParams(final Keyboard keyboard) {
        mKeyTypeface = (keyboard.mKeyTypeface != null)
                ? keyboard.mKeyTypeface : mKeyTypefaceFromKeyboardView;
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mKeyLetterSize = selectTextSizeFromDimensionOrRatio(keyHeight,
                mKeyLetterSizeFromKeyboardView, mKeyLetterRatio,
                mKeyLetterSizeFromKeyboardView);
        // Override if size/ratio is specified in Keyboard.
        mKeyLetterSize = selectTextSizeFromDimensionOrRatio(keyHeight, keyboard.mKeyLetterSize,
                keyboard.mKeyLetterRatio, mKeyLetterSize);
        if (ResourceUtils.isValidFraction(mKeyLabelRatio)) {
            mKeyLabelSize = (int)(keyHeight * mKeyLabelRatio);
        }
        mKeyLargeLabelSize = (int)(keyHeight * mKeyLargeLabelRatio);
        mKeyLargeLetterSize = (int)(keyHeight * mKeyLargeLetterRatio);
        mKeyHintLetterSize = selectTextSizeFromKeyboardOrView(keyHeight,
                keyboard.mKeyHintLetterRatio, mKeyHintLetterRatio);
        mKeyShiftedLetterHintSize = selectTextSizeFromKeyboardOrView(keyHeight,
                keyboard.mKeyShiftedLetterHintRatio, mKeyShiftedLetterHintRatio);
        mKeyHintLabelSize = (int)(keyHeight * mKeyHintLabelRatio);
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

    private static final int selectTextSizeFromKeyboardOrView(final int keyHeight,
            final float ratioFromKeyboard, final float ratioFromView) {
        final float ratio = ResourceUtils.isValidFraction(ratioFromKeyboard)
                ? ratioFromKeyboard : ratioFromView;
        return (int)(keyHeight * ratio);
    }

    public void blendAlpha(final Paint paint) {
        final int color = paint.getColor();
        paint.setARGB((paint.getAlpha() * mAnimAlpha) / Constants.Color.ALPHA_OPAQUE,
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
