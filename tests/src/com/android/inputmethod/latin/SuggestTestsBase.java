/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.test.AndroidTestCase;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.KeyboardId;

import java.io.File;
import java.io.InputStream;
import java.util.Locale;

public class SuggestTestsBase extends AndroidTestCase {
    protected File mTestPackageFile;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestPackageFile = new File(getTestContext().getApplicationInfo().sourceDir);
    }

    protected KeyboardId createKeyboardId(Locale locale, int orientation) {
        final DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        final int width;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            width = Math.max(dm.widthPixels, dm.heightPixels);
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            width = Math.min(dm.widthPixels, dm.heightPixels);
        } else {
            fail("Orientation should be ORIENTATION_LANDSCAPE or ORIENTATION_PORTRAIT: "
                    + "orientation=" + orientation);
            return null;
        }
        return new KeyboardId(locale.toString() + " keyboard",
                com.android.inputmethod.latin.R.xml.kbd_qwerty, locale, orientation, width,
                KeyboardId.MODE_TEXT, new EditorInfo(), false, KeyboardId.F2KEY_MODE_NONE,
                false, false, false);
    }

    protected InputStream openTestRawResource(int resIdInTest) {
        return getTestContext().getResources().openRawResource(resIdInTest);
    }

    protected AssetFileDescriptor openTestRawResourceFd(int resIdInTest) {
        return getTestContext().getResources().openRawResourceFd(resIdInTest);
    }

    private static String format(String message, Object expected, Object actual) {
        return message + " expected:<" + expected + "> but was:<" + actual + ">";
    }

    protected static void suggested(CharSequence expected, CharSequence actual) {
        if (!TextUtils.equals(expected, actual))
            fail(format("assertEquals", expected, actual));
    }

    protected static void suggested(String message, CharSequence expected, CharSequence actual) {
        if (!TextUtils.equals(expected, actual))
            fail(format(message, expected, actual));
    }

    protected static void notSuggested(CharSequence expected, CharSequence actual) {
        if (TextUtils.equals(expected, actual))
            fail(format("assertNotEquals", expected, actual));
    }

    protected static void notSuggested(String message, CharSequence expected, CharSequence actual) {
        if (TextUtils.equals(expected, actual))
            fail(format(message, expected, actual));
    }

    protected static void isInSuggestions(String message, int position) {
        assertTrue(message, position >= 0);
    }

    protected static void isNotInSuggestions(String message, int position) {
        assertTrue(message, position < 0);
    }
}
