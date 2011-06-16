/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.ViewConfiguration;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;

/**
 * Animation to be displayed on the spacebar preview popup when switching languages by swiping the
 * spacebar. It draws the current, previous and next languages and moves them by the delta of touch
 * movement on the spacebar.
 */
public class SlidingLocaleDrawable extends Drawable {
    private static final int SLIDE_SPEED_MULTIPLIER_RATIO = 150;
    private final int mWidth;
    private final int mHeight;
    private final Drawable mBackground;
    private final int mSpacebarTextColor;
    private final TextPaint mTextPaint;
    private final int mMiddleX;
    private final Drawable mLeftDrawable;
    private final Drawable mRightDrawable;
    private final int mThreshold;

    private int mDiff;
    private boolean mHitThreshold;
    private String mCurrentLanguage;
    private String mNextLanguage;
    private String mPrevLanguage;

    public SlidingLocaleDrawable(Context context, Drawable background, int width, int height) {
        mBackground = background;
        Keyboard.setDefaultBounds(mBackground);
        mWidth = width;
        mHeight = height;
        final TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(getTextSizeFromTheme(
                context, android.R.style.TextAppearance_Medium, 18));
        textPaint.setColor(Color.TRANSPARENT);
        textPaint.setTextAlign(Align.CENTER);
        textPaint.setAntiAlias(true);
        mTextPaint = textPaint;
        mMiddleX = (mWidth - mBackground.getIntrinsicWidth()) / 2;

        final TypedArray a = context.obtainStyledAttributes(
                null, R.styleable.LatinKeyboard, R.attr.latinKeyboardStyle, R.style.LatinKeyboard);
        mSpacebarTextColor = a.getColor(R.styleable.LatinKeyboard_spacebarTextColor, 0);
        mLeftDrawable = a.getDrawable(R.styleable.LatinKeyboard_spacebarArrowPreviewLeftIcon);
        mRightDrawable = a.getDrawable(R.styleable.LatinKeyboard_spacebarArrowPreviewRightIcon);
        a.recycle();

        mThreshold = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private static int getTextSizeFromTheme(Context context, int style, int defValue) {
        TypedArray array = context.getTheme().obtainStyledAttributes(
                style, new int[] { android.R.attr.textSize });
        int textSize = array.getDimensionPixelSize(array.getResourceId(0, 0), defValue);
        return textSize;
    }

    void setDiff(int diff) {
        if (diff == Integer.MAX_VALUE) {
            mHitThreshold = false;
            mCurrentLanguage = null;
            return;
        }
        mDiff = Math.max(diff, diff * SLIDE_SPEED_MULTIPLIER_RATIO / 100);
        if (mDiff > mWidth) mDiff = mWidth;
        if (mDiff < -mWidth) mDiff = -mWidth;
        if (Math.abs(mDiff) > mThreshold) mHitThreshold = true;
        invalidateSelf();
    }


    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        if (mHitThreshold) {
            Paint paint = mTextPaint;
            final int width = mWidth;
            final int height = mHeight;
            final int diff = mDiff;
            final Drawable lArrow = mLeftDrawable;
            final Drawable rArrow = mRightDrawable;
            canvas.clipRect(0, 0, width, height);
            if (mCurrentLanguage == null) {
                SubtypeSwitcher subtypeSwitcher = SubtypeSwitcher.getInstance();
                mCurrentLanguage = subtypeSwitcher.getInputLanguageName();
                mNextLanguage = subtypeSwitcher.getNextInputLanguageName();
                mPrevLanguage = subtypeSwitcher.getPreviousInputLanguageName();
            }
            // Draw language text with shadow
            final float baseline = mHeight * LatinKeyboard.SPACEBAR_LANGUAGE_BASELINE
                    - paint.descent();
            paint.setColor(mSpacebarTextColor);
            canvas.drawText(mCurrentLanguage, width / 2 + diff, baseline, paint);
            canvas.drawText(mNextLanguage, diff - width / 2, baseline, paint);
            canvas.drawText(mPrevLanguage, diff + width + width / 2, baseline, paint);

            Keyboard.setDefaultBounds(lArrow);
            rArrow.setBounds(width - rArrow.getIntrinsicWidth(), 0, width,
                    rArrow.getIntrinsicHeight());
            lArrow.draw(canvas);
            rArrow.draw(canvas);
        }
        if (mBackground != null) {
            canvas.translate(mMiddleX, 0);
            mBackground.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
        // Ignore
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        // Ignore
    }

    @Override
    public int getIntrinsicWidth() {
        return mWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mHeight;
    }
}
