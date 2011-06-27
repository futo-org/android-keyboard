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
import android.content.res.Resources.Theme;
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

import com.android.inputmethod.keyboard.internal.SlidingLocaleDrawable;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

// TODO: We should remove this class
public class LatinKeyboard extends Keyboard {
    private static final int SPACE_LED_LENGTH_PERCENT = 80;

    public static final int CODE_NEXT_LANGUAGE = -100;
    public static final int CODE_PREV_LANGUAGE = -101;

    private final Resources mRes;
    private final Theme mTheme;
    private final SubtypeSwitcher mSubtypeSwitcher = SubtypeSwitcher.getInstance();

    /* Space key and its icons, drawables and colors. */
    private final Key mSpaceKey;
    private final Drawable mSpaceIcon;
    private final Drawable mSpacePreviewIcon;
    private final int mSpaceKeyIndex;
    private final boolean mAutoCorrectionSpacebarLedEnabled;
    private final Drawable mAutoCorrectionSpacebarLedIcon;
    private final int mSpacebarTextColor;
    private final int mSpacebarTextShadowColor;
    private float mSpacebarTextFadeFactor = 0.0f;
    private final int mSpacebarLanguageSwitchThreshold;
    private int mSpacebarSlidingLanguageSwitchDiff;
    private final SlidingLocaleDrawable mSlidingLocaleIcon;
    private final HashMap<Integer, SoftReference<BitmapDrawable>> mSpaceDrawableCache =
            new HashMap<Integer, SoftReference<BitmapDrawable>>();

    /* Shortcut key and its icons if available */
    private final Key mShortcutKey;
    private final Drawable mEnabledShortcutIcon;
    private final Drawable mDisabledShortcutIcon;

    // BLACK LEFT-POINTING TRIANGLE and two spaces.
    public static final String ARROW_LEFT = "\u25C0  ";
    // Two spaces and BLACK RIGHT-POINTING TRIANGLE.
    public static final String ARROW_RIGHT = "  \u25B6";

    // Minimum width of spacebar dragging to trigger the language switch (represented by the number
    // of the most common key width of this keyboard).
    private static final int SPACEBAR_DRAG_WIDTH = 3;
    // Minimum width of space key preview (proportional to keyboard width).
    private static final float SPACEBAR_POPUP_MIN_RATIO = 0.5f;
    // Height in space key the language name will be drawn. (proportional to space key height)
    public static final float SPACEBAR_LANGUAGE_BASELINE = 0.6f;
    // If the full language name needs to be smaller than this value to be drawn on space key,
    // its short language name will be used instead.
    private static final float MINIMUM_SCALE_OF_LANGUAGE_NAME = 0.8f;

    private static final String SMALL_TEXT_SIZE_OF_LANGUAGE_ON_SPACEBAR = "small";
    private static final String MEDIUM_TEXT_SIZE_OF_LANGUAGE_ON_SPACEBAR = "medium";

    public LatinKeyboard(Context context, KeyboardId id, int width) {
        super(context, id.getXmlId(), id, width);
        mRes = context.getResources();
        mTheme = context.getTheme();

        final List<Key> keys = getKeys();
        int spaceKeyIndex = -1;
        int shortcutKeyIndex = -1;
        final int keyCount = keys.size();
        for (int index = 0; index < keyCount; index++) {
            // For now, assuming there are up to one space key and one shortcut key respectively.
            switch (keys.get(index).mCode) {
            case CODE_SPACE:
                spaceKeyIndex = index;
                break;
            case CODE_SHORTCUT:
                shortcutKeyIndex = index;
                break;
            }
        }

        // The index of space key is available only after Keyboard constructor has finished.
        mSpaceKey = (spaceKeyIndex >= 0) ? keys.get(spaceKeyIndex) : null;
        mSpaceIcon = (mSpaceKey != null) ? mSpaceKey.getIcon() : null;
        mSpacePreviewIcon = (mSpaceKey != null) ? mSpaceKey.getPreviewIcon() : null;
        mSpaceKeyIndex = spaceKeyIndex;

        mShortcutKey = (shortcutKeyIndex >= 0) ? keys.get(shortcutKeyIndex) : null;
        mEnabledShortcutIcon = (mShortcutKey != null) ? mShortcutKey.getIcon() : null;

        final TypedArray a = context.obtainStyledAttributes(
                null, R.styleable.LatinKeyboard, R.attr.latinKeyboardStyle, R.style.LatinKeyboard);
        mAutoCorrectionSpacebarLedEnabled = a.getBoolean(
                R.styleable.LatinKeyboard_autoCorrectionSpacebarLedEnabled, false);
        mAutoCorrectionSpacebarLedIcon = a.getDrawable(
                R.styleable.LatinKeyboard_autoCorrectionSpacebarLedIcon);
        mDisabledShortcutIcon = a.getDrawable(R.styleable.LatinKeyboard_disabledShortcutIcon);
        mSpacebarTextColor = a.getColor(R.styleable.LatinKeyboard_spacebarTextColor, 0);
        mSpacebarTextShadowColor = a.getColor(
                R.styleable.LatinKeyboard_spacebarTextShadowColor, 0);
        a.recycle();

        // The threshold is "key width" x 1.25
        mSpacebarLanguageSwitchThreshold = (getMostCommonKeyWidth() * 5) / 4;

        if (mSpaceKey != null && mSpacePreviewIcon != null) {
            final int slidingIconWidth = Math.max(mSpaceKey.mWidth,
                    (int)(getMinWidth() * SPACEBAR_POPUP_MIN_RATIO));
            final int spaceKeyheight = mSpacePreviewIcon.getIntrinsicHeight();
            mSlidingLocaleIcon = new SlidingLocaleDrawable(
                    context, mSpacePreviewIcon, slidingIconWidth, spaceKeyheight);
            mSlidingLocaleIcon.setBounds(0, 0, slidingIconWidth, spaceKeyheight);
        } else {
            mSlidingLocaleIcon = null;
        }
    }

    public void setSpacebarTextFadeFactor(float fadeFactor, LatinKeyboardView view) {
        mSpacebarTextFadeFactor = fadeFactor;
        updateSpacebarForLocale(false);
        if (view != null)
            view.invalidateKey(mSpaceKey);
    }

    private static int getSpacebarTextColor(int color, float fadeFactor) {
        final int newColor = Color.argb((int)(Color.alpha(color) * fadeFactor),
                Color.red(color), Color.green(color), Color.blue(color));
        return newColor;
    }

    public void updateShortcutKey(boolean available, LatinKeyboardView view) {
        if (mShortcutKey == null)
            return;
        mShortcutKey.setEnabled(available);
        mShortcutKey.setIcon(available ? mEnabledShortcutIcon : mDisabledShortcutIcon);
        if (view != null)
            view.invalidateKey(mShortcutKey);
    }

    public boolean needsAutoCorrectionSpacebarLed() {
        return mAutoCorrectionSpacebarLedEnabled;
    }

    /**
     * @return a key which should be invalidated.
     */
    public Key onAutoCorrectionStateChanged(boolean isAutoCorrection) {
        updateSpacebarForLocale(isAutoCorrection);
        return mSpaceKey;
    }

    private void updateSpacebarForLocale(boolean isAutoCorrection) {
        if (mSpaceKey == null)
            return;
        // If application locales are explicitly selected.
        if (mSubtypeSwitcher.needsToDisplayLanguage()) {
            mSpaceKey.setIcon(getSpaceDrawable(
                    mSubtypeSwitcher.getInputLocale(), isAutoCorrection));
        } else if (isAutoCorrection) {
            mSpaceKey.setIcon(getSpaceDrawable(null, true));
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
        String language = ARROW_LEFT + SubtypeSwitcher.getFullDisplayName(locale, true)
                + ARROW_RIGHT;
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
            language = ARROW_LEFT + SubtypeSwitcher.getMiddleDisplayLanguage(locale) + ARROW_RIGHT;
            textWidth = getTextWidth(paint, language, origTextSize, bounds);
            textSize = origTextSize * Math.min(width / textWidth, 1.0f);
            useShortName = (textSize / origTextSize < MINIMUM_SCALE_OF_LANGUAGE_NAME)
                    || (textWidth > width);
        } else {
            useShortName = false;
        }

        if (useShortName) {
            language = ARROW_LEFT + SubtypeSwitcher.getShortDisplayLanguage(locale) + ARROW_RIGHT;
            textWidth = getTextWidth(paint, language, origTextSize, bounds);
            textSize = origTextSize * Math.min(width / textWidth, 1.0f);
        }
        paint.setTextSize(textSize);

        return language;
    }

    private BitmapDrawable getSpaceDrawable(Locale locale, boolean isAutoCorrection) {
        final Integer hashCode = Arrays.hashCode(
                new Object[] { locale, isAutoCorrection, mSpacebarTextFadeFactor });
        final SoftReference<BitmapDrawable> ref = mSpaceDrawableCache.get(hashCode);
        BitmapDrawable drawable = (ref == null) ? null : ref.get();
        if (drawable == null) {
            drawable = new BitmapDrawable(mRes, drawSpacebar(
                    locale, isAutoCorrection, mSpacebarTextFadeFactor));
            mSpaceDrawableCache.put(hashCode, new SoftReference<BitmapDrawable>(drawable));
        }
        return drawable;
    }

    private Bitmap drawSpacebar(Locale inputLocale, boolean isAutoCorrection,
            float textFadeFactor) {
        final int width = mSpaceKey.mWidth;
        final int height = mSpaceIcon != null ? mSpaceIcon.getIntrinsicHeight() : mSpaceKey.mHeight;
        final Bitmap buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(buffer);
        final Resources res = mRes;
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // If application locales are explicitly selected.
        if (inputLocale != null) {
            final Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTextAlign(Align.CENTER);

            final String textSizeOfLanguageOnSpacebar = res.getString(
                    R.string.config_text_size_of_language_on_spacebar,
                    SMALL_TEXT_SIZE_OF_LANGUAGE_ON_SPACEBAR);
            final int textStyle;
            final int defaultTextSize;
            if (MEDIUM_TEXT_SIZE_OF_LANGUAGE_ON_SPACEBAR.equals(textSizeOfLanguageOnSpacebar)) {
                textStyle = android.R.style.TextAppearance_Medium;
                defaultTextSize = 18;
            } else {
                textStyle = android.R.style.TextAppearance_Small;
                defaultTextSize = 14;
            }

            final String language = layoutSpacebar(paint, inputLocale, width, getTextSizeFromTheme(
                    mTheme, textStyle, defaultTextSize));

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

    public void setSpacebarSlidingLanguageSwitchDiff(int diff) {
        mSpacebarSlidingLanguageSwitchDiff = diff;
    }

    public void updateSpacebarPreviewIcon(int diff) {
        if (mSpacebarSlidingLanguageSwitchDiff == diff)
            return;
        mSpacebarSlidingLanguageSwitchDiff = diff;
        if (mSlidingLocaleIcon == null)
            return;
        mSlidingLocaleIcon.setDiff(diff);
        if (Math.abs(diff) == Integer.MAX_VALUE) {
            mSpaceKey.setPreviewIcon(mSpacePreviewIcon);
        } else {
            mSpaceKey.setPreviewIcon(mSlidingLocaleIcon);
        }
        mSpaceKey.getPreviewIcon().invalidateSelf();
    }

    public boolean shouldTriggerSpacebarSlidingLanguageSwitch(int diff) {
        // On phone and number layouts, sliding language switch is disabled.
        // TODO: Sort out how to enable language switch on these layouts.
        if (isPhoneKeyboard() || isNumberKeyboard())
            return false;
        return Math.abs(diff) > mSpacebarLanguageSwitchThreshold;
    }

    /**
     * Return true if spacebar needs showing preview even when "popup on keypress" is off.
     * @param keyIndex index of the pressing key
     * @return true if spacebar needs showing preview
     */
    @Override
    public boolean needSpacebarPreview(int keyIndex) {
        // This method is called when "popup on keypress" is off.
        if (!mSubtypeSwitcher.useSpacebarLanguageSwitcher())
            return false;
        // Dismiss key preview.
        if (keyIndex == KeyDetector.NOT_A_KEY)
            return true;
        // Key is not a spacebar.
        if (keyIndex != mSpaceKeyIndex)
            return false;
        // The language switcher will be displayed only when the dragging distance is greater
        // than the threshold.
        return shouldTriggerSpacebarSlidingLanguageSwitch(mSpacebarSlidingLanguageSwitchDiff);
    }

    public int getLanguageChangeDirection() {
        if (mSpaceKey == null || mSubtypeSwitcher.getEnabledKeyboardLocaleCount() <= 1
                || Math.abs(mSpacebarSlidingLanguageSwitchDiff)
                    < getMostCommonKeyWidth() * SPACEBAR_DRAG_WIDTH) {
            return 0; // No change
        }
        return mSpacebarSlidingLanguageSwitchDiff > 0 ? 1 : -1;
    }

    @Override
    public int[] getNearestKeys(int x, int y) {
        // Avoid dead pixels at edges of the keyboard
        return super.getNearestKeys(Math.max(0, Math.min(x, getMinWidth() - 1)),
                Math.max(0, Math.min(y, getHeight() - 1)));
    }

    public static int getTextSizeFromTheme(Theme theme, int style, int defValue) {
        TypedArray array = theme.obtainStyledAttributes(
                style, new int[] { android.R.attr.textSize });
        int textSize = array.getDimensionPixelSize(array.getResourceId(0, 0), defValue);
        return textSize;
    }
}
