/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import android.inputmethodservice.InputMethodService;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.settings.Settings;

public final class UserLogRingCharBuffer {
    public /* for test */ static final int BUFSIZE = 20;
    public /* for test */ int mLength = 0;

    private static UserLogRingCharBuffer sUserLogRingCharBuffer = new UserLogRingCharBuffer();
    private static final char PLACEHOLDER_DELIMITER_CHAR = '\uFFFC';
    private static final int INVALID_COORDINATE = -2;
    private boolean mEnabled = false;
    private int mEnd = 0;
    private char[] mCharBuf = new char[BUFSIZE];
    private int[] mXBuf = new int[BUFSIZE];
    private int[] mYBuf = new int[BUFSIZE];

    private UserLogRingCharBuffer() {
        // Intentional empty constructor for singleton.
    }

    @UsedForTesting
    public static UserLogRingCharBuffer getInstance() {
        return sUserLogRingCharBuffer;
    }

    public static UserLogRingCharBuffer init(final InputMethodService context,
            final boolean enabled, final boolean usabilityStudy) {
        if (!(enabled || usabilityStudy)) {
            return null;
        }
        sUserLogRingCharBuffer.mEnabled = true;
        UsabilityStudyLogUtils.getInstance().init(context);
        return sUserLogRingCharBuffer;
    }

    private static int normalize(final int in) {
        int ret = in % BUFSIZE;
        return ret < 0 ? ret + BUFSIZE : ret;
    }

    // TODO: accept code points
    @UsedForTesting
    public void push(final char c, final int x, final int y) {
        if (!mEnabled) {
            return;
        }
        if (LatinImeLogger.sUsabilityStudy) {
            UsabilityStudyLogUtils.getInstance().writeChar(c, x, y);
        }
        mCharBuf[mEnd] = c;
        mXBuf[mEnd] = x;
        mYBuf[mEnd] = y;
        mEnd = normalize(mEnd + 1);
        if (mLength < BUFSIZE) {
            ++mLength;
        }
    }

    public char pop() {
        if (mLength < 1) {
            return PLACEHOLDER_DELIMITER_CHAR;
        }
        mEnd = normalize(mEnd - 1);
        --mLength;
        return mCharBuf[mEnd];
    }

    public char getBackwardNthChar(final int n) {
        if (mLength <= n || n < 0) {
            return PLACEHOLDER_DELIMITER_CHAR;
        }
        return mCharBuf[normalize(mEnd - n - 1)];
    }

    public int getPreviousX(final char c, final int back) {
        final int index = normalize(mEnd - 2 - back);
        if (mLength <= back
                || Character.toLowerCase(c) != Character.toLowerCase(mCharBuf[index])) {
            return INVALID_COORDINATE;
        }
        return mXBuf[index];
    }

    public int getPreviousY(final char c, final int back) {
        int index = normalize(mEnd - 2 - back);
        if (mLength <= back
                || Character.toLowerCase(c) != Character.toLowerCase(mCharBuf[index])) {
            return INVALID_COORDINATE;
        }
        return mYBuf[index];
    }

    public String getLastWord(final int ignoreCharCount) {
        final StringBuilder sb = new StringBuilder();
        int i = ignoreCharCount;
        for (; i < mLength; ++i) {
            final char c = mCharBuf[normalize(mEnd - 1 - i)];
            if (!Settings.getInstance().isWordSeparator(c)) {
                break;
            }
        }
        for (; i < mLength; ++i) {
            char c = mCharBuf[normalize(mEnd - 1 - i)];
            if (!Settings.getInstance().isWordSeparator(c)) {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.reverse().toString();
    }

    public void reset() {
        mLength = 0;
    }
}
