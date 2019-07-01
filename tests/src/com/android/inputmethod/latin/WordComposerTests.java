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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.CoordinateUtils;
import com.android.inputmethod.latin.common.StringUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for WordComposer.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WordComposerTests {

    @Test
    public void testMoveCursor() {
        final WordComposer wc = new WordComposer();
        // BMP is the Basic Multilingual Plane, as defined by Unicode. This includes
        // most characters for most scripts, including all Roman alphabet languages,
        // CJK, Arabic, Hebrew. Notable exceptions include some emoji and some
        // very rare Chinese ideograms. BMP characters can be encoded on 2 bytes
        // in UTF-16, whereas those outside the BMP need 4 bytes.
        // http://en.wikipedia.org/wiki/Plane_(Unicode)#Basic_Multilingual_Plane
        final String STR_WITHIN_BMP = "abcdef";
        final int[] CODEPOINTS_WITHIN_BMP = StringUtils.toCodePointArray(STR_WITHIN_BMP);
        final int[] COORDINATES_WITHIN_BMP =
                CoordinateUtils.newCoordinateArray(CODEPOINTS_WITHIN_BMP.length,
                        Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        wc.setComposingWord(CODEPOINTS_WITHIN_BMP, COORDINATES_WITHIN_BMP);
        assertEquals(wc.size(), STR_WITHIN_BMP.codePointCount(0, STR_WITHIN_BMP.length()));
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
        // Do what LatinIME does when the cursor is moved outside of the word,
        // and check the behavior is correct.
        wc.reset();

        // \uD861\uDED7 is 𨛗, a character outside the BMP
        final String STR_WITH_SUPPLEMENTARY_CHAR = "abcde\uD861\uDED7fgh";
        final int[] CODEPOINTS_WITH_SUPPLEMENTARY_CHAR =
                StringUtils.toCodePointArray(STR_WITH_SUPPLEMENTARY_CHAR);
        final int[] COORDINATES_WITH_SUPPLEMENTARY_CHAR =
                CoordinateUtils.newCoordinateArray(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR.length,
                        Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        wc.setComposingWord(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR,
                COORDINATES_WITH_SUPPLEMENTARY_CHAR);
        assertEquals(wc.size(), CODEPOINTS_WITH_SUPPLEMENTARY_CHAR.length);
        assertFalse(wc.isCursorFrontOrMiddleOfComposingWord());
        wc.setCursorPositionWithinWord(3);
        assertTrue(wc.isCursorFrontOrMiddleOfComposingWord());
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(6));
        assertTrue(wc.isCursorFrontOrMiddleOfComposingWord());
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(1));
        assertFalse(wc.isCursorFrontOrMiddleOfComposingWord());

        wc.setComposingWord(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR,
                COORDINATES_WITH_SUPPLEMENTARY_CHAR);
        wc.setCursorPositionWithinWord(3);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(7));

        wc.setComposingWord(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR,
                COORDINATES_WITH_SUPPLEMENTARY_CHAR);
        wc.setCursorPositionWithinWord(3);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(7));

        wc.setComposingWord(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR,
                COORDINATES_WITH_SUPPLEMENTARY_CHAR);
        wc.setCursorPositionWithinWord(3);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(-3));
        assertFalse(wc.moveCursorByAndReturnIfInsideComposingWord(-1));


        wc.setComposingWord(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR,
                COORDINATES_WITH_SUPPLEMENTARY_CHAR);
        wc.setCursorPositionWithinWord(3);
        assertFalse(wc.moveCursorByAndReturnIfInsideComposingWord(-9));

        wc.setComposingWord(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR,
                COORDINATES_WITH_SUPPLEMENTARY_CHAR);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(-10));

        wc.setComposingWord(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR,
                COORDINATES_WITH_SUPPLEMENTARY_CHAR);
        assertFalse(wc.moveCursorByAndReturnIfInsideComposingWord(-11));

        wc.setComposingWord(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR,
                COORDINATES_WITH_SUPPLEMENTARY_CHAR);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(0));

        wc.setComposingWord(CODEPOINTS_WITH_SUPPLEMENTARY_CHAR,
                COORDINATES_WITH_SUPPLEMENTARY_CHAR);
        wc.setCursorPositionWithinWord(2);
        assertTrue(wc.moveCursorByAndReturnIfInsideComposingWord(0));
    }
}
