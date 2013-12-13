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

package com.android.inputmethod.latin;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Unit tests for WordComposer.
 */
@SmallTest
public class WordComposerTests extends AndroidTestCase {
    public void testMoveCursor() {
        final WordComposer wc = new WordComposer();
        final String STR_WITHIN_BMP = "abcdef";
        wc.setComposingWord(STR_WITHIN_BMP, null);
        assertEquals(wc.size(),
                STR_WITHIN_BMP.codePointCount(0, STR_WITHIN_BMP.length()));
        assertFalse(wc.isCursorFrontOrMiddleOfComposingWord());
        wc.setCursorPositionWithinWord(2);
        assertTrue(wc.isCursorFrontOrMiddleOfComposingWord());
        // Move the cursor to after the 'd'
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(2));
        assertTrue(wc.isCursorFrontOrMiddleOfComposingWord());
        // Move the cursor to after the 'e'
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(1));
        assertTrue(wc.isCursorFrontOrMiddleOfComposingWord());
        assertEquals(wc.size(), 6);
        // Move the cursor to after the 'f'
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(1));
        assertFalse(wc.isCursorFrontOrMiddleOfComposingWord());
        // Move the cursor past the end of the word
        assertFalse(wc.moveCursorByAndReturnIfInsideComposingWord(1));
        assertFalse(wc.moveCursorByAndReturnIfInsideComposingWord(15));

        // \uD861\uDED7 is 𨛗, a character outside the BMP
        final String STR_WITH_SUPPLEMENTARY_CHAR = "abcde\uD861\uDED7fgh";
        wc.setComposingWord(STR_WITH_SUPPLEMENTARY_CHAR, null);
        assertEquals(wc.size(), STR_WITH_SUPPLEMENTARY_CHAR.codePointCount(0,
                        STR_WITH_SUPPLEMENTARY_CHAR.length()));
        assertFalse(wc.isCursorFrontOrMiddleOfComposingWord());
        wc.setCursorPositionWithinWord(3);
        assertTrue(wc.isCursorFrontOrMiddleOfComposingWord());
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(6));
        assertTrue(wc.isCursorFrontOrMiddleOfComposingWord());
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(1));
        assertFalse(wc.isCursorFrontOrMiddleOfComposingWord());

        wc.setComposingWord(STR_WITH_SUPPLEMENTARY_CHAR, null);
        wc.setCursorPositionWithinWord(3);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(7));

        wc.setComposingWord(STR_WITH_SUPPLEMENTARY_CHAR, null);
        wc.setCursorPositionWithinWord(3);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(7));

        wc.setComposingWord(STR_WITH_SUPPLEMENTARY_CHAR, null);
        wc.setCursorPositionWithinWord(3);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(-3));
        assertFalse(wc.moveCursorByAndReturnIfInsideComposingWord(-1));

        wc.setComposingWord(STR_WITH_SUPPLEMENTARY_CHAR, null);
        wc.setCursorPositionWithinWord(3);
        assertFalse(wc.moveCursorByAndReturnIfInsideComposingWord(-9));

        wc.setComposingWord(STR_WITH_SUPPLEMENTARY_CHAR, null);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(-10));

        wc.setComposingWord(STR_WITH_SUPPLEMENTARY_CHAR, null);
        assertFalse(wc.moveCursorByAndReturnIfInsideComposingWord(-11));

        wc.setComposingWord(STR_WITH_SUPPLEMENTARY_CHAR, null);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(0));

        wc.setComposingWord(STR_WITH_SUPPLEMENTARY_CHAR, null);
        wc.setCursorPositionWithinWord(2);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(0));
    }
}
