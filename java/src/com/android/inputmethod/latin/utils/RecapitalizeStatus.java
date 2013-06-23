/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import java.util.Locale;

/**
 * The status of the current recapitalize process.
 */
public class RecapitalizeStatus {
    public static final int NOT_A_RECAPITALIZE_MODE = -1;
    public static final int CAPS_MODE_ORIGINAL_MIXED_CASE = 0;
    public static final int CAPS_MODE_ALL_LOWER = 1;
    public static final int CAPS_MODE_FIRST_WORD_UPPER = 2;
    public static final int CAPS_MODE_ALL_UPPER = 3;
    // When adding a new mode, don't forget to update the CAPS_MODE_LAST constant.
    public static final int CAPS_MODE_LAST = CAPS_MODE_ALL_UPPER;

    private static final int[] ROTATION_STYLE = {
        CAPS_MODE_ORIGINAL_MIXED_CASE,
        CAPS_MODE_ALL_LOWER,
        CAPS_MODE_FIRST_WORD_UPPER,
        CAPS_MODE_ALL_UPPER
    };

    private static final int getStringMode(final String string, final String separators) {
        if (StringUtils.isIdenticalAfterUpcase(string)) {
            return CAPS_MODE_ALL_UPPER;
        } else if (StringUtils.isIdenticalAfterDowncase(string)) {
            return CAPS_MODE_ALL_LOWER;
        } else if (StringUtils.isIdenticalAfterCapitalizeEachWord(string, separators)) {
            return CAPS_MODE_FIRST_WORD_UPPER;
        } else {
            return CAPS_MODE_ORIGINAL_MIXED_CASE;
        }
    }

    /**
     * We store the location of the cursor and the string that was there before the recapitalize
     * action was done, and the location of the cursor and the string that was there after.
     */
    private int mCursorStartBefore;
    private String mStringBefore;
    private int mCursorStartAfter;
    private int mCursorEndAfter;
    private int mRotationStyleCurrentIndex;
    private boolean mSkipOriginalMixedCaseMode;
    private Locale mLocale;
    private String mSeparators;
    private String mStringAfter;
    private boolean mIsActive;

    public RecapitalizeStatus() {
        // By default, initialize with dummy values that won't match any real recapitalize.
        initialize(-1, -1, "", Locale.getDefault(), "");
        deactivate();
    }

    public void initialize(final int cursorStart, final int cursorEnd, final String string,
            final Locale locale, final String separators) {
        mCursorStartBefore = cursorStart;
        mStringBefore = string;
        mCursorStartAfter = cursorStart;
        mCursorEndAfter = cursorEnd;
        mStringAfter = string;
        final int initialMode = getStringMode(mStringBefore, separators);
        mLocale = locale;
        mSeparators = separators;
        if (CAPS_MODE_ORIGINAL_MIXED_CASE == initialMode) {
            mRotationStyleCurrentIndex = 0;
            mSkipOriginalMixedCaseMode = false;
        } else {
            // Find the current mode in the array.
            int currentMode;
            for (currentMode = ROTATION_STYLE.length - 1; currentMode > 0; --currentMode) {
                if (ROTATION_STYLE[currentMode] == initialMode) {
                    break;
                }
            }
            mRotationStyleCurrentIndex = currentMode;
            mSkipOriginalMixedCaseMode = true;
        }
        mIsActive = true;
    }

    public void deactivate() {
        mIsActive = false;
    }

    public boolean isActive() {
        return mIsActive;
    }

    public boolean isSetAt(final int cursorStart, final int cursorEnd) {
        return cursorStart == mCursorStartAfter && cursorEnd == mCursorEndAfter;
    }

    /**
     * Rotate through the different possible capitalization modes.
     */
    public void rotate() {
        final String oldResult = mStringAfter;
        int count = 0; // Protection against infinite loop.
        do {
            mRotationStyleCurrentIndex = (mRotationStyleCurrentIndex + 1) % ROTATION_STYLE.length;
            if (CAPS_MODE_ORIGINAL_MIXED_CASE == ROTATION_STYLE[mRotationStyleCurrentIndex]
                    && mSkipOriginalMixedCaseMode) {
                mRotationStyleCurrentIndex =
                        (mRotationStyleCurrentIndex + 1) % ROTATION_STYLE.length;
            }
            ++count;
            switch (ROTATION_STYLE[mRotationStyleCurrentIndex]) {
            case CAPS_MODE_ORIGINAL_MIXED_CASE:
                mStringAfter = mStringBefore;
                break;
            case CAPS_MODE_ALL_LOWER:
                mStringAfter = mStringBefore.toLowerCase(mLocale);
                break;
            case CAPS_MODE_FIRST_WORD_UPPER:
                mStringAfter = StringUtils.capitalizeEachWord(mStringBefore, mSeparators,
                        mLocale);
                break;
            case CAPS_MODE_ALL_UPPER:
                mStringAfter = mStringBefore.toUpperCase(mLocale);
                break;
            default:
                mStringAfter = mStringBefore;
            }
        } while (mStringAfter.equals(oldResult) && count < ROTATION_STYLE.length + 1);
        mCursorEndAfter = mCursorStartAfter + mStringAfter.length();
    }

    /**
     * Remove leading/trailing whitespace from the considered string.
     */
    public void trim() {
        final int len = mStringBefore.length();
        int nonWhitespaceStart = 0;
        for (; nonWhitespaceStart < len;
                nonWhitespaceStart = mStringBefore.offsetByCodePoints(nonWhitespaceStart, 1)) {
            final int codePoint = mStringBefore.codePointAt(nonWhitespaceStart);
            if (!Character.isWhitespace(codePoint)) break;
        }
        int nonWhitespaceEnd = len;
        for (; nonWhitespaceEnd > 0;
                nonWhitespaceEnd = mStringBefore.offsetByCodePoints(nonWhitespaceEnd, -1)) {
            final int codePoint = mStringBefore.codePointBefore(nonWhitespaceEnd);
            if (!Character.isWhitespace(codePoint)) break;
        }
        // If nonWhitespaceStart >= nonWhitespaceEnd, that means the selection contained only
        // whitespace, so we leave it as is.
        if ((0 != nonWhitespaceStart || len != nonWhitespaceEnd)
                && nonWhitespaceStart < nonWhitespaceEnd) {
            mCursorEndAfter = mCursorStartBefore + nonWhitespaceEnd;
            mCursorStartBefore = mCursorStartAfter = mCursorStartBefore + nonWhitespaceStart;
            mStringAfter = mStringBefore =
                    mStringBefore.substring(nonWhitespaceStart, nonWhitespaceEnd);
        }
    }

    public String getRecapitalizedString() {
        return mStringAfter;
    }

    public int getNewCursorStart() {
        return mCursorStartAfter;
    }

    public int getNewCursorEnd() {
        return mCursorEndAfter;
    }

    public int getCurrentMode() {
        return ROTATION_STYLE[mRotationStyleCurrentIndex];
    }
}
