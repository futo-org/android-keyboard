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

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

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

    private final Context mContext;
    private final SubtypeSwitcher mSubtypeSwitcher = SubtypeSwitcher.getInstance();

    /* Space key and its icons, drawables and colors. */
    private final Key mSpaceKey;
    private final Drawable mSpaceIcon;
    private final Drawable mSpacePreviewIcon;
    private final int[] mSpaceKeyIndexArray;
    private final Drawable mSpaceAutoCorrectionIndicator;
    private final Drawable mButtonArrowLeftIcon;
    private final Drawable mButtonArrowRightIcon;
    private final int mSpacebarTextColor;
    private final int mSpacebarTextShadowColor;
    private final int mSpacebarVerticalCorrection;
    private float mSpacebarTextFadeFactor = 0.0f;
    private int mSpaceDragStartX;
    private int mSpaceDragLastDiff;
    private boolean mCurrentlyInSpace;
    private SlidingLocaleDrawable mSlidingLocaleIcon;
    private final HashMap<Integer, SoftReference<BitmapDrawable>> mSpaceDrawableCache =
            new HashMap<Integer, SoftReference<BitmapDrawable>>();

    /* Shortcut key and its icons if available */
    private final Key mShortcutKey;
    private final Drawable mEnabledShortcutIcon;
    private final Drawable mDisabledShortcutIcon;

    // Minimum width of spacebar dragging to trigger the language switch (represented by the number
    // of the most common key width of this keyboard).
    private static final int SPACEBAR_DRAG_WIDTH = 3;
    // Minimum width of space key preview (proportional to keyboard width).
    private static final float SPACEBAR_POPUP_MIN_RATIO = 0.4f;
    // Height in space key the language name will be drawn. (proportional to space key height)
    public static final float SPACEBAR_LANGUAGE_BASELINE = 0.6f;
    // If the full language name needs to be smaller than this value to be drawn on space key,
    // its short language name will be used instead.
    private static final float MINIMUM_SCALE_OF_LANGUAGE_NAME = 0.8f;

    private static final String SMALL_TEXT_SIZE_OF_LANGUAGE_ON_SPACEBAR = "small";
    private static final String MEDIUM_TEXT_SIZE_OF_LANGUAGE_ON_SPACEBAR = "medium";

    public LatinKeyboard(Context context, KeyboardId id) {
        super(context, id.getXmlId(), id);
        final Resources res = context.getResources();
        mContext = context;

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
        mSpaceKeyIndexArray = new int[] { spaceKeyIndex };

        mShortcutKey = (shortcutKeyIndex >= 0) ? keys.get(shortcutKeyIndex) : null;
        mEnabledShortcutIcon = (mShortcutKey != null) ? mShortcutKey.getIcon() : null;

        mSpacebarTextColor = res.getColor(R.color.latinkeyboard_bar_language_text);
        if (id.mColorScheme == KeyboardView.COLOR_SCHEME_BLACK) {
            mSpacebarTextShadowColor = res.getColor(
                    R.color.latinkeyboard_bar_language_shadow_black);
            mDisabledShortcutIcon = res.getDrawable(R.drawable.sym_bkeyboard_voice_off);
        } else { // default color scheme is KeyboardView.COLOR_SCHEME_WHITE
            mSpacebarTextShadowColor = res.getColor(
                    R.color.latinkeyboard_bar_language_shadow_white);
            mDisabledShortcutIcon = res.getDrawable(R.drawable.sym_keyboard_voice_off_holo);
        }
        mSpaceAutoCorrectionIndicator = res.getDrawable(R.drawable.sym_keyboard_space_led);
        mButtonArrowLeftIcon = res.getDrawable(R.drawable.sym_keyboard_language_arrows_left);
        mButtonArrowRightIcon = res.getDrawable(R.drawable.sym_keyboard_language_arrows_right);
        mSpacebarVerticalCorrection = res.getDimensionPixelOffset(
                R.dimen.spacebar_vertical_correction);
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

    private static ColorFilter getSpacebarDrawableFilter(float fadeFactor) {
        final ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setScale(1, 1, 1, fadeFactor);
        return new ColorMatrixColorFilter(colorMatrix);
    }

    public void updateShortcutKey(boolean available, LatinKeyboardView view) {
        if (mShortcutKey == null)
            return;
        mShortcutKey.mEnabled = available;
        mShortcutKey.setIcon(available ? mEnabledShortcutIcon : mDisabledShortcutIcon);
        if (view != null)
            view.invalidateKey(mShortcutKey);
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
    private static String layoutSpacebar(Paint paint, Locale locale, Drawable lArrow,
            Drawable rArrow, int width, int height, float origTextSize,
            boolean allowVariableTextSize) {
        final float arrowWidth = lArrow.getIntrinsicWidth();
        final float arrowHeight = lArrow.getIntrinsicHeight();
        final float maxTextWidth = width - (arrowWidth + arrowWidth);
        final Rect bounds = new Rect();

        // Estimate appropriate language name text size to fit in maxTextWidth.
        String language = SubtypeSwitcher.getFullDisplayName(locale, true);
        int textWidth = getTextWidth(paint, language, origTextSize, bounds);
        // Assuming text width and text size are proportional to each other.
        float textSize = origTextSize * Math.min(maxTextWidth / textWidth, 1.0f);

        final boolean useShortName;
        if (allowVariableTextSize) {
            textWidth = getTextWidth(paint, language, textSize, bounds);
            // If text size goes too small or text does not fit, use short name
            useShortName = textSize / origTextSize < MINIMUM_SCALE_OF_LANGUAGE_NAME
                    || textWidth > maxTextWidth;
        } else {
            useShortName = textWidth > maxTextWidth;
            textSize = origTextSize;
        }
        if (useShortName) {
            language = SubtypeSwitcher.getShortDisplayLanguage(locale);
            textWidth = getTextWidth(paint, language, origTextSize, bounds);
            textSize = origTextSize * Math.min(maxTextWidth / textWidth, 1.0f);
        }
        paint.setTextSize(textSize);

        // Place left and right arrow just before and after language text.
        final float baseline = height * SPACEBAR_LANGUAGE_BASELINE;
        final int top = (int)(baseline - arrowHeight);
        final float remains = (width - textWidth) / 2;
        lArrow.setBounds((int)(remains - arrowWidth), top, (int)remains, (int)baseline);
        rArrow.setBounds((int)(remains + textWidth), top, (int)(remains + textWidth + arrowWidth),
                (int)baseline);

        return language;
    }

    private BitmapDrawable getSpaceDrawable(Locale locale, boolean isAutoCorrection) {
        final Integer hashCode = Arrays.hashCode(
                new Object[] { locale, isAutoCorrection, mSpacebarTextFadeFactor });
        final SoftReference<BitmapDrawable> ref = mSpaceDrawableCache.get(hashCode);
        BitmapDrawable drawable = (ref == null) ? null : ref.get();
        if (drawable == null) {
            drawable = new BitmapDrawable(mContext.getResources(), drawSpacebar(
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
        final Resources res = mContext.getResources();
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

            final boolean allowVariableTextSize = true;
            final String language = layoutSpacebar(paint, inputLocale,
                    mButtonArrowLeftIcon, mButtonArrowRightIcon, width, height,
                    getTextSizeFromTheme(mContext.getTheme(), textStyle, defaultTextSize),
                    allowVariableTextSize);

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

            // Put arrows that are already layed out on either side of the text
            if (mSubtypeSwitcher.useSpacebarLanguageSwitcher()
                    && mSubtypeSwitcher.getEnabledKeyboardLocaleCount() > 1) {
                mButtonArrowLeftIcon.setColorFilter(getSpacebarDrawableFilter(textFadeFactor));
                mButtonArrowRightIcon.setColorFilter(getSpacebarDrawableFilter(textFadeFactor));
                mButtonArrowLeftIcon.draw(canvas);
                mButtonArrowRightIcon.draw(canvas);
            }
        }

        // Draw the spacebar icon at the bottom
        if (isAutoCorrection) {
            final int iconWidth = width * SPACE_LED_LENGTH_PERCENT / 100;
            final int iconHeight = mSpaceAutoCorrectionIndicator.getIntrinsicHeight();
            int x = (width - iconWidth) / 2;
            int y = height - iconHeight;
            mSpaceAutoCorrectionIndicator.setBounds(x, y, x + iconWidth, y + iconHeight);
            mSpaceAutoCorrectionIndicator.draw(canvas);
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

    private void updateLocaleDrag(int diff) {
        if (mSlidingLocaleIcon == null) {
            final int width = Math.max(mSpaceKey.mWidth,
                    (int)(getMinWidth() * SPACEBAR_POPUP_MIN_RATIO));
            final int height = mSpacePreviewIcon.getIntrinsicHeight();
            mSlidingLocaleIcon =
                    new SlidingLocaleDrawable(mContext, mSpacePreviewIcon, width, height);
            mSlidingLocaleIcon.setBounds(0, 0, width, height);
            mSpaceKey.setPreviewIcon(mSlidingLocaleIcon);
        }
        mSlidingLocaleIcon.setDiff(diff);
        if (Math.abs(diff) == Integer.MAX_VALUE) {
            mSpaceKey.setPreviewIcon(mSpacePreviewIcon);
        } else {
            mSpaceKey.setPreviewIcon(mSlidingLocaleIcon);
        }
        mSpaceKey.getPreviewIcon().invalidateSelf();
    }

    // This method is called when "popup on keypress" is off.
    @Override
    public boolean needSpacebarPreview(int keyIndex) {
        if (!mSubtypeSwitcher.useSpacebarLanguageSwitcher())
            return false;
        // Dismiss key preview.
        if (keyIndex == KeyDetector.NOT_A_KEY)
            return true;
        // Key is not a spacebar.
        if (keyIndex != mSpaceKeyIndexArray[0])
            return false;
        // The language switcher will be displayed only when the dragging distance is greater
        // than average key width of this keyboard.
        return Math.abs(mSpaceDragLastDiff) > getMostCommonKeyWidth();
    }

    public int getLanguageChangeDirection() {
        if (mSpaceKey == null || SubtypeSwitcher.getInstance().getEnabledKeyboardLocaleCount() <= 1
                || Math.abs(mSpaceDragLastDiff) < getMostCommonKeyWidth() * SPACEBAR_DRAG_WIDTH) {
            return 0; // No change
        }
        return mSpaceDragLastDiff > 0 ? 1 : -1;
    }

    public void keyReleased() {
        mCurrentlyInSpace = false;
        mSpaceDragLastDiff = 0;
        if (mSpaceKey != null) {
            updateLocaleDrag(Integer.MAX_VALUE);
        }
    }

    /**
     * Does the magic of locking the touch gesture into the spacebar when
     * switching input languages.
     */
    @Override
    public boolean isInside(Key key, int pointX, int pointY) {
        int x = pointX;
        int y = pointY;
        final int code = key.mCode;
        if (code == CODE_SPACE) {
            y += mSpacebarVerticalCorrection;
            if (SubtypeSwitcher.getInstance().useSpacebarLanguageSwitcher()
                    && SubtypeSwitcher.getInstance().getEnabledKeyboardLocaleCount() > 1) {
                if (mCurrentlyInSpace) {
                    int diff = x - mSpaceDragStartX;
                    if (Math.abs(diff - mSpaceDragLastDiff) > 0) {
                        updateLocaleDrag(diff);
                    }
                    mSpaceDragLastDiff = diff;
                    return true;
                } else {
                    boolean isOnSpace = key.isOnKey(x, y);
                    if (isOnSpace) {
                        mCurrentlyInSpace = true;
                        mSpaceDragStartX = x;
                        updateLocaleDrag(0);
                    }
                    return isOnSpace;
                }
            }
        }

        // Lock into the spacebar
        if (mCurrentlyInSpace) return false;

        return key.isOnKey(x, y);
    }

    @Override
    public int[] getNearestKeys(int x, int y) {
        if (mCurrentlyInSpace) {
            return mSpaceKeyIndexArray;
        } else {
            // Avoid dead pixels at edges of the keyboard
            return super.getNearestKeys(Math.max(0, Math.min(x, getMinWidth() - 1)),
                    Math.max(0, Math.min(y, getHeight() - 1)));
        }
    }

    private static int getTextSizeFromTheme(Theme theme, int style, int defValue) {
        TypedArray array = theme.obtainStyledAttributes(
                style, new int[] { android.R.attr.textSize });
        int textSize = array.getDimensionPixelSize(array.getResourceId(0, 0), defValue);
        return textSize;
    }
}
