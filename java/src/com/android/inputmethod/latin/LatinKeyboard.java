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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.Log;
import android.view.ViewConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class LatinKeyboard extends BaseKeyboard {

    private static final boolean DEBUG_PREFERRED_LETTER = false;
    private static final String TAG = "LatinKeyboard";
    private static final int OPACITY_FULLY_OPAQUE = 255;
    private static final int SPACE_LED_LENGTH_PERCENT = 80;

    private final Drawable mShiftedIcon;
    private Drawable mShiftLockPreviewIcon;
    private final HashMap<Key, Drawable> mNormalShiftIcons = new HashMap<Key, Drawable>();
    private Drawable mSpaceIcon;
    private Drawable mSpaceAutoCompletionIndicator;
    private Drawable mSpacePreviewIcon;
    private final Drawable mButtonArrowLeftIcon;
    private final Drawable mButtonArrowRightIcon;
    private final int mSpaceBarTextShadowColor;
    private Key mSpaceKey;
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

    private LatinKeyboardShiftState mShiftState = new LatinKeyboardShiftState();

    private static final float SPACEBAR_DRAG_THRESHOLD = 0.8f;
    private static final float OVERLAP_PERCENTAGE_LOW_PROB = 0.70f;
    private static final float OVERLAP_PERCENTAGE_HIGH_PROB = 0.85f;
    // Minimum width of space key preview (proportional to keyboard width)
    private static final float SPACEBAR_POPUP_MIN_RATIO = 0.4f;
    // Height in space key the language name will be drawn. (proportional to space key height)
    private static final float SPACEBAR_LANGUAGE_BASELINE = 0.6f;
    // If the full language name needs to be smaller than this value to be drawn on space key,
    // its short language name will be used instead.
    private static final float MINIMUM_SCALE_OF_LANGUAGE_NAME = 0.8f;

    private static int sSpacebarVerticalCorrection;

    public LatinKeyboard(Context context, KeyboardSwitcher.KeyboardId id) {
        super(context, id);
        final Resources res = context.getResources();
        mContext = context;
        mRes = res;
        if (id.mColorScheme == BaseKeyboardView.COLOR_SCHEME_BLACK) {
            mShiftedIcon = res.getDrawable(R.drawable.sym_bkeyboard_shift_locked);
            mSpaceBarTextShadowColor = res.getColor(
                    R.color.latinkeyboard_bar_language_shadow_black);
        } else { // default color scheme is BaseKeyboardView.COLOR_SCHEME_WHITE
            mShiftedIcon = res.getDrawable(R.drawable.sym_keyboard_shift_locked);
            mSpaceBarTextShadowColor = res.getColor(
                    R.color.latinkeyboard_bar_language_shadow_white);
        }
        mShiftLockPreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_shift_locked);
        setDefaultBounds(mShiftLockPreviewIcon);
        mSpaceAutoCompletionIndicator = res.getDrawable(R.drawable.sym_keyboard_space_led);
        mButtonArrowLeftIcon = res.getDrawable(R.drawable.sym_keyboard_language_arrows_left);
        mButtonArrowRightIcon = res.getDrawable(R.drawable.sym_keyboard_language_arrows_right);
        sSpacebarVerticalCorrection = res.getDimensionPixelOffset(
                R.dimen.spacebar_vertical_correction);
        mSpaceKeyIndex = indexOf(LatinIME.KEYCODE_SPACE);
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y, 
            XmlResourceParser parser, KeyStyles keyStyles) {
        Key key = new LatinKey(res, parent, x, y, parser, keyStyles);
        switch (key.codes[0]) {
        case LatinIME.KEYCODE_SPACE:
            mSpaceKey = key;
            mSpaceIcon = key.icon;
            mSpacePreviewIcon = key.iconPreview;
            break;
        }

        return key;
    }

    public void enableShiftLock() {
        for (final Key key : getShiftKeys()) {
            if (key instanceof LatinKey) {
                ((LatinKey)key).enableShiftLock();
            }
            mNormalShiftIcons.put(key, key.icon);
        }
    }

    public boolean setShiftLocked(boolean newShiftLockState) {
        for (final Key key : getShiftKeys()) {
            key.on = newShiftLockState;
            key.icon = newShiftLockState ? mShiftedIcon : mNormalShiftIcons.get(key);
        }
        mShiftState.setShiftLocked(newShiftLockState);
        return true;
    }

    public boolean isShiftLocked() {
        return mShiftState.isShiftLocked();
    }

    @Override
    public boolean setShifted(boolean newShiftState) {
        if (getShiftKeys().size() == 0)
            return super.setShifted(newShiftState);

        for (final Key key : getShiftKeys()) {
            if (!newShiftState && !mShiftState.isShiftLocked()) {
                key.icon = mNormalShiftIcons.get(key);
            } else if (newShiftState && !mShiftState.isShiftedOrShiftLocked()) {
                key.icon = mShiftedIcon;
            }
        }
        return mShiftState.setShifted(newShiftState);
    }

    @Override
    public boolean isShiftedOrShiftLocked() {
        if (getShiftKeys().size() > 0) {
            return mShiftState.isShiftedOrShiftLocked();
        } else {
            return super.isShiftedOrShiftLocked();
        }
    }

    public void setAutomaticTemporaryUpperCase() {
        setShifted(true);
        mShiftState.setAutomaticTemporaryUpperCase();
    }

    public boolean isAutomaticTemporaryUpperCase() {
        return isAlphaKeyboard() && mShiftState.isAutomaticTemporaryUpperCase();
    }

    public boolean isManualTemporaryUpperCase() {
        return isAlphaKeyboard() && mShiftState.isManualTemporaryUpperCase();
    }

    /* package */ LatinKeyboardShiftState getKeyboardShiftState() {
        return mShiftState;
    }

    public boolean isAlphaKeyboard() {
        return mId.getXmlId() == R.xml.kbd_qwerty;
    }

    public boolean isPhoneKeyboard() {
        return mId.mMode == KeyboardSwitcher.MODE_PHONE;
    }

    public boolean isNumberKeyboard() {
        return mId.mMode == KeyboardSwitcher.MODE_NUMBER;
    }

    /**
     * @return a key which should be invalidated.
     */
    public Key onAutoCompletionStateChanged(boolean isAutoCompletion) {
        updateSpaceBarForLocale(isAutoCompletion);
        return mSpaceKey;
    }

    private void updateSpaceBarForLocale(boolean isAutoCompletion) {
        final Resources res = mRes;
        // If application locales are explicitly selected.
        if (SubtypeSwitcher.getInstance().needsToDisplayLanguage()) {
            mSpaceKey.icon = new BitmapDrawable(res,
                    drawSpaceBar(OPACITY_FULLY_OPAQUE, isAutoCompletion));
        } else {
            // sym_keyboard_space_led can be shared with Black and White symbol themes.
            if (isAutoCompletion) {
                mSpaceKey.icon = new BitmapDrawable(res,
                        drawSpaceBar(OPACITY_FULLY_OPAQUE, isAutoCompletion));
            } else {
                mSpaceKey.icon = mSpaceIcon;
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

    private Bitmap drawSpaceBar(int opacity, boolean isAutoCompletion) {
        final int width = mSpaceKey.width;
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

            final boolean allowVariableTextSize = true;
            final String language = layoutSpaceBar(paint, subtypeSwitcher.getInputLocale(),
                    mButtonArrowLeftIcon, mButtonArrowRightIcon, width, height,
                    getTextSizeFromTheme(android.R.style.TextAppearance_Small, 14),
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
        if (isAutoCompletion) {
            final int iconWidth = width * SPACE_LED_LENGTH_PERCENT / 100;
            final int iconHeight = mSpaceAutoCompletionIndicator.getIntrinsicHeight();
            int x = (width - iconWidth) / 2;
            int y = height - iconHeight;
            mSpaceAutoCompletionIndicator.setBounds(x, y, x + iconWidth, y + iconHeight);
            mSpaceAutoCompletionIndicator.draw(canvas);
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
            final int width = Math.max(mSpaceKey.width,
                    (int)(getMinWidth() * SPACEBAR_POPUP_MIN_RATIO));
            final int height = mSpacePreviewIcon.getIntrinsicHeight();
            mSlidingLocaleIcon = new SlidingLocaleDrawable(mSpacePreviewIcon, width, height);
            mSlidingLocaleIcon.setBounds(0, 0, width, height);
            mSpaceKey.iconPreview = mSlidingLocaleIcon;
        }
        mSlidingLocaleIcon.setDiff(diff);
        if (Math.abs(diff) == Integer.MAX_VALUE) {
            mSpaceKey.iconPreview = mSpacePreviewIcon;
        } else {
            mSpaceKey.iconPreview = mSlidingLocaleIcon;
        }
        mSpaceKey.iconPreview.invalidateSelf();
    }

    public int getLanguageChangeDirection() {
        if (mSpaceKey == null || SubtypeSwitcher.getInstance().getEnabledKeyboardLocaleCount() <= 1
                || Math.abs(mSpaceDragLastDiff) < mSpaceKey.width * SPACEBAR_DRAG_THRESHOLD) {
            return 0; // No change
        }
        return mSpaceDragLastDiff > 0 ? 1 : -1;
    }

    boolean isCurrentlyInSpace() {
        return mCurrentlyInSpace;
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
    public boolean isInside(LatinKey key, int x, int y) {
        final int code = key.codes[0];
        if (code == KEYCODE_SHIFT || code == KEYCODE_DELETE) {
            y -= key.height / 10;
            if (code == KEYCODE_SHIFT) x += key.width / 6;
            if (code == KEYCODE_DELETE) x -= key.width / 6;
        } else if (code == LatinIME.KEYCODE_SPACE) {
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
                    boolean insideSpace = key.isInsideSuper(x, y);
                    if (insideSpace) {
                        mCurrentlyInSpace = true;
                        mSpaceDragStartX = x;
                        updateLocaleDrag(0);
                    }
                    return insideSpace;
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
                    if (mPrefLetter == code && !key.isInsideSuper(x, y)) {
                        Log.d(TAG, "CORRECTED !!!!!!");
                    }
                }
                return mPrefLetter == code;
            } else {
                final boolean inside = key.isInsideSuper(x, y);
                int[] nearby = getNearestKeys(x, y);
                List<Key> nearbyKeys = getKeys();
                if (inside) {
                    // If it's a preferred letter
                    if (inPrefList(code, pref)) {
                        // Check if its frequency is much lower than a nearby key
                        mPrefLetter = code;
                        mPrefLetterX = x;
                        mPrefLetterY = y;
                        for (int i = 0; i < nearby.length; i++) {
                            Key k = nearbyKeys.get(nearby[i]);
                            if (k != key && inPrefList(k.codes[0], pref)) {
                                final int dist = distanceFrom(k, x, y);
                                if (dist < (int) (k.width * OVERLAP_PERCENTAGE_LOW_PROB) &&
                                        (pref[k.codes[0]] > pref[mPrefLetter] * 3))  {
                                    mPrefLetter = k.codes[0];
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
                    if (inPrefList(k.codes[0], pref)) {
                        final int dist = distanceFrom(k, x, y);
                        if (dist < (int) (k.width * OVERLAP_PERCENTAGE_HIGH_PROB)
                                && dist < mPrefDistance)  {
                            mPrefLetter = k.codes[0];
                            mPrefLetterX = x;
                            mPrefLetterY = y;
                            mPrefDistance = dist;
                        }
                    }
                }
                // Didn't find any
                if (mPrefLetter == 0) {
                    return inside;
                } else {
                    return mPrefLetter == code;
                }
            }
        }

        // Lock into the spacebar
        if (mCurrentlyInSpace) return false;

        return key.isInsideSuper(x, y);
    }

    private boolean inPrefList(int code, int[] pref) {
        if (code < pref.length && code >= 0) return pref[code] > 0;
        return false;
    }

    private int distanceFrom(Key k, int x, int y) {
        if (y > k.y && y < k.y + k.height) {
            return Math.abs(k.x + k.width / 2 - x);
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
            if (keys.get(i).codes[0] == code) return i;
        }
        return -1;
    }

    private int getTextSizeFromTheme(int style, int defValue) {
        TypedArray array = mContext.getTheme().obtainStyledAttributes(
                style, new int[] { android.R.attr.textSize });
        int textSize = array.getDimensionPixelSize(array.getResourceId(0, 0), defValue);
        return textSize;
    }

    public static class LatinKey extends BaseKeyboard.Key {

        // functional normal state (with properties)
        private final int[] KEY_STATE_FUNCTIONAL_NORMAL = {
                android.R.attr.state_single
        };

        // functional pressed state (with properties)
        private final int[] KEY_STATE_FUNCTIONAL_PRESSED = {
                android.R.attr.state_single,
                android.R.attr.state_pressed
        };

        private boolean mShiftLockEnabled;

        public LatinKey(Resources res, BaseKeyboard.Row parent, int x, int y,
                XmlResourceParser parser, KeyStyles keyStyles) {
            super(res, parent, x, y, parser, keyStyles);
            if (popupCharacters != null && popupCharacters.length() == 0) {
                // If there is a keyboard with no keys specified in popupCharacters
                popupResId = 0;
            }
        }

        private void enableShiftLock() {
            mShiftLockEnabled = true;
        }

        // sticky is used for shift key.  If a key is not sticky and is modifier,
        // the key will be treated as functional.
        private boolean isFunctionalKey() {
            return !sticky && modifier;
        }

        @Override
        public void onReleased(boolean inside) {
            if (!mShiftLockEnabled) {
                super.onReleased(inside);
            } else {
                pressed = !pressed;
            }
        }

        /**
         * Overriding this method so that we can reduce the target area for certain keys.
         */
        @Override
        public boolean isInside(int x, int y) {
            boolean result = (keyboard instanceof LatinKeyboard)
                    && ((LatinKeyboard)keyboard).isInside(this, x, y);
            return result;
        }

        private boolean isInsideSuper(int x, int y) {
            return super.isInside(x, y);
        }

        @Override
        public int[] getCurrentDrawableState() {
            if (isFunctionalKey()) {
                if (pressed) {
                    return KEY_STATE_FUNCTIONAL_PRESSED;
                } else {
                    return KEY_STATE_FUNCTIONAL_NORMAL;
                }
            }
            return super.getCurrentDrawableState();
        }
    }

    /**
     * Animation to be displayed on the spacebar preview popup when switching 
     * languages by swiping the spacebar. It draws the current, previous and
     * next languages and moves them by the delta of touch movement on the spacebar.
     */
    private class SlidingLocaleDrawable extends Drawable {

        private final int mWidth;
        private final int mHeight;
        private final Drawable mBackground;
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

        public SlidingLocaleDrawable(Drawable background, int width, int height) {
            mBackground = background;
            setDefaultBounds(mBackground);
            mWidth = width;
            mHeight = height;
            final TextPaint textPaint = new TextPaint();
            textPaint.setTextSize(getTextSizeFromTheme(android.R.style.TextAppearance_Medium, 18));
            textPaint.setColor(R.color.latinkeyboard_transparent);
            textPaint.setTextAlign(Align.CENTER);
            textPaint.setAlpha(OPACITY_FULLY_OPAQUE);
            textPaint.setAntiAlias(true);
            mTextPaint = textPaint;
            mMiddleX = (mWidth - mBackground.getIntrinsicWidth()) / 2;
            final Resources res = mRes;
            mLeftDrawable = res.getDrawable(
                    R.drawable.sym_keyboard_feedback_language_arrows_left);
            mRightDrawable = res.getDrawable(
                    R.drawable.sym_keyboard_feedback_language_arrows_right);
            mThreshold = ViewConfiguration.get(mContext).getScaledTouchSlop();
        }

        private void setDiff(int diff) {
            if (diff == Integer.MAX_VALUE) {
                mHitThreshold = false;
                mCurrentLanguage = null;
                return;
            }
            mDiff = diff;
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
                final float baseline = mHeight * SPACEBAR_LANGUAGE_BASELINE - paint.descent();
                paint.setColor(mRes.getColor(R.color.latinkeyboard_feedback_language_text));
                canvas.drawText(mCurrentLanguage, width / 2 + diff, baseline, paint);
                canvas.drawText(mNextLanguage, diff - width / 2, baseline, paint);
                canvas.drawText(mPrevLanguage, diff + width + width / 2, baseline, paint);

                setDefaultBounds(lArrow);
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
}
