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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.List;
import java.util.Locale;

// TODO: We should remove this class
public class LatinKeyboard extends Keyboard {

    private static final boolean DEBUG_PREFERRED_LETTER = false;
    private static final String TAG = "LatinKeyboard";

    public static final int OPACITY_FULLY_OPAQUE = 255;
    private static final int SPACE_LED_LENGTH_PERCENT = 80;

    private Drawable mShiftLockPreviewIcon;
    private Drawable mSpaceAutoCorrectionIndicator;
    private final Drawable mButtonArrowLeftIcon;
    private final Drawable mButtonArrowRightIcon;
    private final int mSpaceBarTextShadowColor;
    private int mSpaceKeyIndex = -1;
    private int mSpaceDragStartX;
    private int mSpaceDragLastDiff;
    private final Resources mRes;
    private final Context mContext;
    private boolean mCurrentlyInSpace;
    private SlidingLocaleDrawable mSlidingLocaleIcon;
    private int[] mPrefLetterFrequencies;
    private int mPrefLetter;
    private int mPrefLetterX;
    private int mPrefLetterY;
    private int mPrefDistance;

    private static final float SPACEBAR_DRAG_THRESHOLD = 0.8f;
    private static final float OVERLAP_PERCENTAGE_LOW_PROB = 0.70f;
    private static final float OVERLAP_PERCENTAGE_HIGH_PROB = 0.85f;
    // Minimum width of space key preview (proportional to keyboard width)
    private static final float SPACEBAR_POPUP_MIN_RATIO = 0.4f;
    // Height in space key the language name will be drawn. (proportional to space key height)
    public static final float SPACEBAR_LANGUAGE_BASELINE = 0.6f;
    // If the full language name needs to be smaller than this value to be drawn on space key,
    // its short language name will be used instead.
    private static final float MINIMUM_SCALE_OF_LANGUAGE_NAME = 0.8f;

    private static int sSpacebarVerticalCorrection;

    private static final String SMALL_TEXT_SIZE_OF_LANGUAGE_ON_SPACEBAR = "small";
    private static final String MEDIUM_TEXT_SIZE_OF_LANGUAGE_ON_SPACEBAR = "medium";

    public LatinKeyboard(Context context, KeyboardId id) {
        super(context, id);
        final Resources res = context.getResources();
        mContext = context;
        mRes = res;
        if (id.mColorScheme == KeyboardView.COLOR_SCHEME_BLACK) {
            mSpaceBarTextShadowColor = res.getColor(
                    R.color.latinkeyboard_bar_language_shadow_black);
        } else { // default color scheme is KeyboardView.COLOR_SCHEME_WHITE
            mSpaceBarTextShadowColor = res.getColor(
                    R.color.latinkeyboard_bar_language_shadow_white);
        }
        mShiftLockPreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_shift_locked);
        setDefaultBounds(mShiftLockPreviewIcon);
        mSpaceAutoCorrectionIndicator = res.getDrawable(R.drawable.sym_keyboard_space_led);
        mButtonArrowLeftIcon = res.getDrawable(R.drawable.sym_keyboard_language_arrows_left);
        mButtonArrowRightIcon = res.getDrawable(R.drawable.sym_keyboard_language_arrows_right);
        sSpacebarVerticalCorrection = res.getDimensionPixelOffset(
                R.dimen.spacebar_vertical_correction);
        mSpaceKeyIndex = indexOf(CODE_SPACE);
    }

    /**
     * @return a key which should be invalidated.
     */
    public Key onAutoCorrectionStateChanged(boolean isAutoCorrection) {
        updateSpaceBarForLocale(isAutoCorrection);
        return mSpaceKey;
    }

    private void updateSpaceBarForLocale(boolean isAutoCorrection) {
        final Resources res = mRes;
        // If application locales are explicitly selected.
        if (SubtypeSwitcher.getInstance().needsToDisplayLanguage()) {
            mSpaceKey.setIcon(new BitmapDrawable(res,
                    drawSpaceBar(OPACITY_FULLY_OPAQUE, isAutoCorrection)));
        } else {
            // sym_keyboard_space_led can be shared with Black and White symbol themes.
            if (isAutoCorrection) {
                mSpaceKey.setIcon(new BitmapDrawable(res,
                        drawSpaceBar(OPACITY_FULLY_OPAQUE, isAutoCorrection)));
            } else {
                mSpaceKey.setIcon(mSpaceIcon);
            }
        }
    }

    // Compute width of text with specified text size using paint.
    private static int getTextWidth(Paint paint, String text, float textSize, Rect bounds) {
        paint.setTextSize(textSize);
        paint.getTextBounds(text, 0, text.length(), bounds);
        return bounds.width();
    }

    // Layout local language name and left and right arrow on space bar.
    private static String layoutSpaceBar(Paint paint, Locale locale, Drawable lArrow,
            Drawable rArrow, int width, int height, float origTextSize,
            boolean allowVariableTextSize) {
        final float arrowWidth = lArrow.getIntrinsicWidth();
        final float arrowHeight = lArrow.getIntrinsicHeight();
        final float maxTextWidth = width - (arrowWidth + arrowWidth);
        final Rect bounds = new Rect();

        // Estimate appropriate language name text size to fit in maxTextWidth.
        String language = SubtypeSwitcher.getDisplayLanguage(locale);
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

    @SuppressWarnings("unused")
    private Bitmap drawSpaceBar(int opacity, boolean isAutoCorrection) {
        final int width = mSpaceKey.mWidth;
        final int height = mSpaceIcon.getIntrinsicHeight();
        final Bitmap buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(buffer);
        final Resources res = mRes;
        canvas.drawColor(res.getColor(R.color.latinkeyboard_transparent), PorterDuff.Mode.CLEAR);

        SubtypeSwitcher subtypeSwitcher = SubtypeSwitcher.getInstance();
        // If application locales are explicitly selected.
        if (subtypeSwitcher.needsToDisplayLanguage()) {
            final Paint paint = new Paint();
            paint.setAlpha(opacity);
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
            final String language = layoutSpaceBar(paint, subtypeSwitcher.getInputLocale(),
                    mButtonArrowLeftIcon, mButtonArrowRightIcon, width, height,
                    getTextSizeFromTheme(textStyle, defaultTextSize),
                    allowVariableTextSize);

            // Draw language text with shadow
            final float baseline = height * SPACEBAR_LANGUAGE_BASELINE;
            final float descent = paint.descent();
            paint.setColor(mSpaceBarTextShadowColor);
            canvas.drawText(language, width / 2, baseline - descent - 1, paint);
            paint.setColor(res.getColor(R.color.latinkeyboard_bar_language_text));
            canvas.drawText(language, width / 2, baseline - descent, paint);

            // Put arrows that are already layed out on either side of the text
            if (SubtypeSwitcher.USE_SPACEBAR_LANGUAGE_SWITCHER
                    && subtypeSwitcher.getEnabledKeyboardLocaleCount() > 1) {
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
        } else {
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

    public int getLanguageChangeDirection() {
        if (mSpaceKey == null || SubtypeSwitcher.getInstance().getEnabledKeyboardLocaleCount() <= 1
                || Math.abs(mSpaceDragLastDiff) < mSpaceKey.mWidth * SPACEBAR_DRAG_THRESHOLD) {
            return 0; // No change
        }
        return mSpaceDragLastDiff > 0 ? 1 : -1;
    }

    public void setPreferredLetters(int[] frequencies) {
        mPrefLetterFrequencies = frequencies;
        mPrefLetter = 0;
    }

    public void keyReleased() {
        mCurrentlyInSpace = false;
        mSpaceDragLastDiff = 0;
        mPrefLetter = 0;
        mPrefLetterX = 0;
        mPrefLetterY = 0;
        mPrefDistance = Integer.MAX_VALUE;
        if (mSpaceKey != null) {
            updateLocaleDrag(Integer.MAX_VALUE);
        }
    }

    /**
     * Does the magic of locking the touch gesture into the spacebar when
     * switching input languages.
     */
    @Override
    @SuppressWarnings("unused") // SubtypeSwitcher.USE_SPACEBAR_LANGUAGE_SWITCHER is constant
    public boolean isInside(Key key, int pointX, int pointY) {
        int x = pointX;
        int y = pointY;
        final int code = key.mCodes[0];
        if (code == CODE_SHIFT || code == CODE_DELETE) {
            y -= key.mHeight / 10;
            if (code == CODE_SHIFT) x += key.mWidth / 6;
            if (code == CODE_DELETE) x -= key.mWidth / 6;
        } else if (code == CODE_SPACE) {
            y += LatinKeyboard.sSpacebarVerticalCorrection;
            if (SubtypeSwitcher.USE_SPACEBAR_LANGUAGE_SWITCHER
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
        } else if (mPrefLetterFrequencies != null) {
            // New coordinate? Reset
            if (mPrefLetterX != x || mPrefLetterY != y) {
                mPrefLetter = 0;
                mPrefDistance = Integer.MAX_VALUE;
            }
            // Handle preferred next letter
            final int[] pref = mPrefLetterFrequencies;
            if (mPrefLetter > 0) {
                if (DEBUG_PREFERRED_LETTER) {
                    if (mPrefLetter == code && !key.isOnKey(x, y)) {
                        Log.d(TAG, "CORRECTED !!!!!!");
                    }
                }
                return mPrefLetter == code;
            } else {
                final boolean isOnKey = key.isOnKey(x, y);
                int[] nearby = getNearestKeys(x, y);
                List<Key> nearbyKeys = getKeys();
                if (isOnKey) {
                    // If it's a preferred letter
                    if (inPrefList(code, pref)) {
                        // Check if its frequency is much lower than a nearby key
                        mPrefLetter = code;
                        mPrefLetterX = x;
                        mPrefLetterY = y;
                        for (int i = 0; i < nearby.length; i++) {
                            Key k = nearbyKeys.get(nearby[i]);
                            if (k != key && inPrefList(k.mCodes[0], pref)) {
                                final int dist = distanceFrom(k, x, y);
                                if (dist < (int) (k.mWidth * OVERLAP_PERCENTAGE_LOW_PROB) &&
                                        (pref[k.mCodes[0]] > pref[mPrefLetter] * 3))  {
                                    mPrefLetter = k.mCodes[0];
                                    mPrefDistance = dist;
                                    if (DEBUG_PREFERRED_LETTER) {
                                        Log.d(TAG, "CORRECTED ALTHOUGH PREFERRED !!!!!!");
                                    }
                                    break;
                                }
                            }
                        }

                        return mPrefLetter == code;
                    }
                }

                // Get the surrounding keys and intersect with the preferred list
                // For all in the intersection
                //   if distance from touch point is within a reasonable distance
                //       make this the pref letter
                // If no pref letter
                //   return inside;
                // else return thiskey == prefletter;

                for (int i = 0; i < nearby.length; i++) {
                    Key k = nearbyKeys.get(nearby[i]);
                    if (inPrefList(k.mCodes[0], pref)) {
                        final int dist = distanceFrom(k, x, y);
                        if (dist < (int) (k.mWidth * OVERLAP_PERCENTAGE_HIGH_PROB)
                                && dist < mPrefDistance)  {
                            mPrefLetter = k.mCodes[0];
                            mPrefLetterX = x;
                            mPrefLetterY = y;
                            mPrefDistance = dist;
                        }
                    }
                }
                // Didn't find any
                if (mPrefLetter == 0) {
                    return isOnKey;
                } else {
                    return mPrefLetter == code;
                }
            }
        }

        // Lock into the spacebar
        if (mCurrentlyInSpace) return false;

        return key.isOnKey(x, y);
    }

    private boolean inPrefList(int code, int[] pref) {
        if (code < pref.length && code >= 0) return pref[code] > 0;
        return false;
    }

    private int distanceFrom(Key k, int x, int y) {
        if (y > k.mY && y < k.mY + k.mHeight) {
            return Math.abs(k.mX + k.mWidth / 2 - x);
        } else {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public int[] getNearestKeys(int x, int y) {
        if (mCurrentlyInSpace) {
            return new int[] { mSpaceKeyIndex };
        } else {
            // Avoid dead pixels at edges of the keyboard
            return super.getNearestKeys(Math.max(0, Math.min(x, getMinWidth() - 1)),
                    Math.max(0, Math.min(y, getHeight() - 1)));
        }
    }

    private int indexOf(int code) {
        List<Key> keys = getKeys();
        int count = keys.size();
        for (int i = 0; i < count; i++) {
            if (keys.get(i).mCodes[0] == code) return i;
        }
        return -1;
    }

    private int getTextSizeFromTheme(int style, int defValue) {
        TypedArray array = mContext.getTheme().obtainStyledAttributes(
                style, new int[] { android.R.attr.textSize });
        int textSize = array.getDimensionPixelSize(array.getResourceId(0, 0), defValue);
        return textSize;
    }
}
