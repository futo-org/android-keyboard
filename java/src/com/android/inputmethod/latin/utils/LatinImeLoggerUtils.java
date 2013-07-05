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

import android.text.TextUtils;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.WordComposer;

public final class LatinImeLoggerUtils {
    private LatinImeLoggerUtils() {
        // This utility class is not publicly instantiable.
    }

    public static void onNonSeparator(final char code, final int x, final int y) {
        UserLogRingCharBuffer.getInstance().push(code, x, y);
        LatinImeLogger.logOnInputChar();
    }

    public static void onSeparator(final int code, final int x, final int y) {
        // Helper method to log a single code point separator
        // TODO: cache this mapping of a code point to a string in a sparse array in StringUtils
        onSeparator(new String(new int[]{code}, 0, 1), x, y);
    }

    public static void onSeparator(final String separator, final int x, final int y) {
        final int length = separator.length();
        for (int i = 0; i < length; i = Character.offsetByCodePoints(separator, i, 1)) {
            int codePoint = Character.codePointAt(separator, i);
            // TODO: accept code points
            UserLogRingCharBuffer.getInstance().push((char)codePoint, x, y);
        }
        LatinImeLogger.logOnInputSeparator();
    }

    public static void onAutoCorrection(final String typedWord, final String correctedWord,
            final String separatorString, final WordComposer wordComposer) {
        final boolean isBatchMode = wordComposer.isBatchMode();
        if (!isBatchMode && TextUtils.isEmpty(typedWord)) {
            return;
        }
        // TODO: this fails when the separator is more than 1 code point long, but
        // the backend can't handle it yet. The only case when this happens is with
        // smileys and other multi-character keys.
        final int codePoint = TextUtils.isEmpty(separatorString) ? Constants.NOT_A_CODE
                : separatorString.codePointAt(0);
        if (!isBatchMode) {
            LatinImeLogger.logOnAutoCorrectionForTyping(typedWord, correctedWord, codePoint);
        } else {
            if (!TextUtils.isEmpty(correctedWord)) {
                // We must make sure that InputPointer contains only the relative timestamps,
                // not actual timestamps.
                LatinImeLogger.logOnAutoCorrectionForGeometric(
                        "", correctedWord, codePoint, wordComposer.getInputPointers());
            }
        }
    }

    public static void onAutoCorrectionCancellation() {
        LatinImeLogger.logOnAutoCorrectionCancelled();
    }
}
