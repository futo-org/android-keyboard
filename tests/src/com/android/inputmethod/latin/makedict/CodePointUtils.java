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

package com.android.inputmethod.latin.makedict;

import java.util.Random;

// Utility methods related with code points used for tests.
public class CodePointUtils {
    private CodePointUtils() {
        // This utility class is not publicly instantiable.
    }

    public static int[] generateCodePointSet(final int codePointSetSize, final Random random) {
        final int[] codePointSet = new int[codePointSetSize];
        for (int i = codePointSet.length - 1; i >= 0; ) {
            final int r = Math.abs(random.nextInt());
            if (r < 0) continue;
            // Don't insert 0~0x20, but insert any other code point.
            // Code points are in the range 0~0x10FFFF.
            final int candidateCodePoint = 0x20 + r % (Character.MAX_CODE_POINT - 0x20);
            // Code points between MIN_ and MAX_SURROGATE are not valid on their own.
            if (candidateCodePoint >= Character.MIN_SURROGATE
                    && candidateCodePoint <= Character.MAX_SURROGATE) continue;
            codePointSet[i] = candidateCodePoint;
            --i;
        }
        return codePointSet;
    }

    /**
     * Generates a random word.
     */
    public static String generateWord(final Random random, final int[] codePointSet) {
        StringBuilder builder = new StringBuilder();
        // 8 * 4 = 32 chars max, but we do it the following way so as to bias the random toward
        // longer words. This should be closer to natural language, and more importantly, it will
        // exercise the algorithms in dicttool much more.
        final int count = 1 + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5);
        while (builder.length() < count) {
            builder.appendCodePoint(codePointSet[Math.abs(random.nextInt()) % codePointSet.length]);
        }
        return builder.toString();
    }
}
