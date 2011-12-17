/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

// TODO: We should remove this class
public class LatinKeyboard extends Keyboard {
    private static final int SPACE_LED_LENGTH_PERCENT = 80;

    private final Resources mRes;

    /* Space key and its icons, drawables and colors. */
    private final Key mSpaceKey;
    private final Drawable mSpaceIcon;
    private final boolean mAutoCorrectionSpacebarLedEnabled;
    private final Drawable mAutoCorrectionSpacebarLedIcon;
    private final float mSpacebarTextSize;
    private final int mSpacebarTextColor;
    private final int mSpacebarTextShadowColor;
    private final HashMap<Integer, BitmapDrawable> mSpaceDrawableCache =
            new HashMap<Integer, BitmapDrawable>();
    private final boolean mIsSpacebarTriggeringPopupByLongPress;

    private boolean mAutoCorrectionSpacebarLedOn;
    private boolean mMultipleEnabledIMEsOrSubtypes;
    private boolean mNeedsToDisplayLanguage;
    private float mSpacebarTextFadeFactor = 0.0f;

    /* Shortcut key and its icons if available */
    private final Key mShortcutKey;
    private final Drawable mEnabledShortcutIcon;
    private final Drawable mDisabledShortcutIcon;

    // Height in space key the language name will be drawn. (proportional to space key height)
    public static final float SPACEBAR_LANGUAGE_BASELINE = 0.6f;
    // If the full language name needs to be smaller than this value to be drawn on space key,
    // its short language name will be used instead.
    private static final float MINIMUM_SCALE_OF_LANGUAGE_NAME = 0.8f;

    private LatinKeyboard(Context context, KeyboardParams params) {
        super(params);
        mRes = context.getResources();

        // The index of space key is available only after Keyboard constructor has finished.
        mSpaceKey = getKey(CODE_SPACE);
        mSpaceIcon = (mSpaceKey != null) ? mSpaceKey.getIcon() : null;

        mShortcutKey = getKey(CODE_SHORTCUT);
        mEnabledShortcutIcon = (mShortcutKey != null) ? mShortcutKey.getIcon() : null;
        final int longPressSpaceKeyTimeout =
                mRes.getInteger(R.integer.config_long_press_space_key_timeout);
        mIsSpacebarTriggeringPopupByLongPress = (longPressSpaceKeyTimeout > 0);

        final TypedArray a = context.obtainStyledAttributes(
                null, R.styleable.LatinKeyboard, R.attr.latinKeyboardStyle, R.style.LatinKeyboard);
        mAutoCorrectionSpacebarLedEnabled = a.getBoolean(
                R.styleable.LatinKeyboard_autoCorrectionSpacebarLedEnabled, false);
        mAutoCorrectionSpacebarLedIcon = a.getDrawable(
                R.styleable.LatinKeyboard_autoCorrectionSpacebarLedIcon);
        mDisabledShortcutIcon = a.getDrawable(R.styleable.LatinKeyboard_disabledShortcutIcon);
        final float spacebarTextRatio = a.getFraction(R.styleable.LatinKeyboard_spacebarTextRatio,
                1000, 1000, 1) / 1000.0f;
        final int keyHeight = mMostCommonKeyHeight - mVerticalGap;
        mSpacebarTextSize = keyHeight * spacebarTextRatio;
        mSpacebarTextColor = a.getColor(R.styleable.LatinKeyboard_spacebarTextColor, 0);
        mSpacebarTextShadowColor = a.getColor(
                R.styleable.LatinKeyboard_spacebarTextShadowColor, 0);
        a.recycle();
    }

    public static class Builder extends KeyboardBuilder<KeyboardParams> {
        public Builder(Context context) {
            super(context, new KeyboardParams());
        }

        @Override
        public Builder load(int xmlId, KeyboardId id) {
            super.load(xmlId, id);
            return this;
        }

        @Override
        public LatinKeyboard build() {
            return new LatinKeyboard(mContext, mParams);
        }
    }

    // TODO: Move this drawing method to LatinKeyboardView.
    // TODO: Use Key.keyLabel to draw language name of spacebar.
    public Key updateSpacebarLanguage(float fadeFactor, boolean multipleEnabledIMEsOrSubtypes,
            boolean needsToDisplayLanguage) {
        mSpacebarTextFadeFactor = fadeFactor;
        mMultipleEnabledIMEsOrSubtypes = multipleEnabledIMEsOrSubtypes;
        mNeedsToDisplayLanguage = needsToDisplayLanguage;
        updateSpacebarIcon();
        return mSpaceKey;
    }

    private static int getSpacebarTextColor(int color, float fadeFactor) {
        final int newColor = Color.argb((int)(Color.alpha(color) * fadeFactor),
                Color.red(color), Color.green(color), Color.blue(color));
        return newColor;
    }

    // TODO: Move this drawing method to LatinKeyboardView.
    public Key updateShortcutKey(boolean available) {
        if (mShortcutKey == null)
            return null;
        mShortcutKey.setEnabled(available);
        mShortcutKey.setIcon(available ? mEnabledShortcutIcon : mDisabledShortcutIcon);
        return mShortcutKey;
    }

    // TODO: Get rid of this method
    public boolean needsAutoCorrectionSpacebarLed() {
        return mAutoCorrectionSpacebarLedEnabled;
    }

    // TODO: Move this drawing method to LatinKeyboardView.
    /**
     * @return a key which should be invalidated.
     */
    public Key updateAutoCorrectionState(boolean isAutoCorrection) {
        mAutoCorrectionSpacebarLedOn = isAutoCorrection;
        updateSpacebarIcon();
        return mSpaceKey;
    }

    @Override
    public CharSequence adjustLabelCase(CharSequence label) {
        if (mId.isAlphabetKeyboard() && isShiftedOrShiftLocked() && !TextUtils.isEmpty(label)
                && label.length() < 3 && Character.isLowerCase(label.charAt(0))) {
            return label.toString().toUpperCase(mId.mLocale);
        }
        return label;
    }

    private void updateSpacebarIcon() {
        if (mSpaceKey == null) return;
        final boolean shouldShowInputMethodPicker = mIsSpacebarTriggeringPopupByLongPress
                && mMultipleEnabledIMEsOrSubtypes;
        mSpaceKey.setNeedsSpecialPopupHint(shouldShowInputMethodPicker);
        if (mNeedsToDisplayLanguage) {
            mSpaceKey.setIcon(getSpaceDrawable(mId.mLocale));
        } else if (mAutoCorrectionSpacebarLedOn) {
            mSpaceKey.setIcon(getSpaceDrawable(null));
        } else {
            mSpaceKey.setIcon(mSpaceIcon);
        }
    }

    // Compute width of text with specified text size using paint.
    private static int getTextWidth(Paint paint, String text, float textSize, Rect bounds) {
        paint.setTextSize(textSize);
        paint.getTextBounds(text, 0, text.length(), bounds);
        return bounds.width();
    }

    // Layout local language name and left and right arrow on spacebar.
    private static String layoutSpacebar(Paint paint, Locale locale, int width,
            float origTextSize) {
        final Rect bounds = new Rect();

        // Estimate appropriate language name text size to fit in maxTextWidth.
        String language = Utils.getFullDisplayName(locale, true);
        int textWidth = getTextWidth(paint, language, origTextSize, bounds);
        // Assuming text width and text size are proportional to each other.
        float textSize = origTextSize * Math.min(width / textWidth, 1.0f);
        // allow variable text size
        textWidth = getTextWidth(paint, language, textSize, bounds);
        // If text size goes too small or text does not fit, use middle or short name
        final boolean useMiddleName = (textSize / origTextSize < MINIMUM_SCALE_OF_LANGUAGE_NAME)
                || (textWidth > width);

        final boolean useShortName;
        if (useMiddleName) {
            language = Utils.getMiddleDisplayLanguage(locale);
            textWidth = getTextWidth(paint, language, origTextSize, bounds);
            textSize = origTextSize * Math.min(width / textWidth, 1.0f);
            useShortName = (textSize / origTextSize < MINIMUM_SCALE_OF_LANGUAGE_NAME)
                    || (textWidth > width);
        } else {
            useShortName = false;
        }

        if (useShortName) {
            language = Utils.getShortDisplayLanguage(locale);
            textWidth = getTextWidth(paint, language, origTextSize, bounds);
            textSize = origTextSize * Math.min(width / textWidth, 1.0f);
        }
        paint.setTextSize(textSize);

        return language;
    }

    private BitmapDrawable getSpaceDrawable(Locale locale) {
        final Integer hashCode = Arrays.hashCode(
                new Object[] { locale, mAutoCorrectionSpacebarLedOn, mSpacebarTextFadeFactor });
        final BitmapDrawable cached = mSpaceDrawableCache.get(hashCode);
        if (cached != null) {
            return cached;
        }
        final BitmapDrawable drawable = new BitmapDrawable(mRes, drawSpacebar(
                locale, mAutoCorrectionSpacebarLedOn, mSpacebarTextFadeFactor));
        mSpaceDrawableCache.put(hashCode, drawable);
        return drawable;
    }

    private Bitmap drawSpacebar(Locale inputLocale, boolean isAutoCorrection,
            float textFadeFactor) {
        final int width = mSpaceKey.mWidth;
        final int height = mSpaceIcon != null ? mSpaceIcon.getIntrinsicHeight() : mSpaceKey.mHeight;
        final Bitmap buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(buffer);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // If application locales are explicitly selected.
        if (inputLocale != null) {
            final Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTextAlign(Align.CENTER);

            final String language = layoutSpacebar(paint, inputLocale, width, mSpacebarTextSize);

            // Draw language text with shadow
            // In case there is no space icon, we will place the language text at the center of
            // spacebar.
            final float descent = paint.descent();
            final float textHeight = -paint.ascent() + descent;
            final float baseline = (mSpaceIcon != null) ? height * SPACEBAR_LANGUAGE_BASELINE
                    : height / 2 + textHeight / 2;
            paint.setColor(getSpacebarTextColor(mSpacebarTextShadowColor, textFadeFactor));
            canvas.drawText(language, width / 2, baseline - descent - 1, paint);
            paint.setColor(getSpacebarTextColor(mSpacebarTextColor, textFadeFactor));
            canvas.drawText(language, width / 2, baseline - descent, paint);
        }

        // Draw the spacebar icon at the bottom
        if (isAutoCorrection) {
            final int iconWidth = width * SPACE_LED_LENGTH_PERCENT / 100;
            final int iconHeight = mAutoCorrectionSpacebarLedIcon.getIntrinsicHeight();
            int x = (width - iconWidth) / 2;
            int y = height - iconHeight;
            mAutoCorrectionSpacebarLedIcon.setBounds(x, y, x + iconWidth, y + iconHeight);
            mAutoCorrectionSpacebarLedIcon.draw(canvas);
        } else if (mSpaceIcon != null) {
            final int iconWidth = mSpaceIcon.getIntrinsicWidth();
            final int iconHeight = mSpaceIcon.getIntrinsicHeight();
            int x = (width - iconWidth) / 2;
            int y = height - iconHeight;
            mSpaceIcon.setBounds(x, y, x + iconWidth, y + iconHeight);
            mSpaceIcon.draw(canvas);
        }
        return buffer;
    }

    @Override
    public Key[] getNearestKeys(int x, int y) {
        // Avoid dead pixels at edges of the keyboard
        return super.getNearestKeys(Math.max(0, Math.min(x, mOccupiedWidth - 1)),
                Math.max(0, Math.min(y, mOccupiedHeight - 1)));
    }
}
