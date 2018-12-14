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

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.common.Constants;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RecapitalizeStatusTests {
    private static final int[] SPACE = { Constants.CODE_SPACE };

    @Test
    public void testTrim() {
        final RecapitalizeStatus status = new RecapitalizeStatus();
        status.start(30, 40, "abcdefghij", Locale.ENGLISH, SPACE);
        status.trim();
        assertEquals("abcdefghij", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(40, status.getNewCursorEnd());

        status.start(30, 44, "    abcdefghij", Locale.ENGLISH, SPACE);
        status.trim();
        assertEquals("abcdefghij", status.getRecapitalizedString());
        assertEquals(34, status.getNewCursorStart());
        assertEquals(44, status.getNewCursorEnd());

        status.start(30, 40, "abcdefgh  ", Locale.ENGLISH, SPACE);
        status.trim();
        assertEquals("abcdefgh", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(38, status.getNewCursorEnd());

        status.start(30, 45, "   abcdefghij  ", Locale.ENGLISH, SPACE);
        status.trim();
        assertEquals("abcdefghij", status.getRecapitalizedString());
        assertEquals(33, status.getNewCursorStart());
        assertEquals(43, status.getNewCursorEnd());
    }

    @Test
    public void testRotate() {
        final RecapitalizeStatus status = new RecapitalizeStatus();
        status.start(29, 40, "abcd efghij", Locale.ENGLISH, SPACE);
        status.rotate();
        assertEquals("Abcd Efghij", status.getRecapitalizedString());
        assertEquals(29, status.getNewCursorStart());
        assertEquals(40, status.getNewCursorEnd());
        status.rotate();
        assertEquals("ABCD EFGHIJ", status.getRecapitalizedString());
        status.rotate();
        assertEquals("abcd efghij", status.getRecapitalizedString());
        status.rotate();
        assertEquals("Abcd Efghij", status.getRecapitalizedString());

        status.start(29, 40, "Abcd Efghij", Locale.ENGLISH, SPACE);
        status.rotate();
        assertEquals("ABCD EFGHIJ", status.getRecapitalizedString());
        assertEquals(29, status.getNewCursorStart());
        assertEquals(40, status.getNewCursorEnd());
        status.rotate();
        assertEquals("abcd efghij", status.getRecapitalizedString());
        status.rotate();
        assertEquals("Abcd Efghij", status.getRecapitalizedString());
        status.rotate();
        assertEquals("ABCD EFGHIJ", status.getRecapitalizedString());

        status.start(29, 40, "ABCD EFGHIJ", Locale.ENGLISH, SPACE);
        status.rotate();
        assertEquals("abcd efghij", status.getRecapitalizedString());
        assertEquals(29, status.getNewCursorStart());
        assertEquals(40, status.getNewCursorEnd());
        status.rotate();
        assertEquals("Abcd Efghij", status.getRecapitalizedString());
        status.rotate();
        assertEquals("ABCD EFGHIJ", status.getRecapitalizedString());
        status.rotate();
        assertEquals("abcd efghij", status.getRecapitalizedString());

        status.start(29, 39, "AbCDefghij", Locale.ENGLISH, SPACE);
        status.rotate();
        assertEquals("abcdefghij", status.getRecapitalizedString());
        assertEquals(29, status.getNewCursorStart());
        assertEquals(39, status.getNewCursorEnd());
        status.rotate();
        assertEquals("Abcdefghij", status.getRecapitalizedString());
        status.rotate();
        assertEquals("ABCDEFGHIJ", status.getRecapitalizedString());
        status.rotate();
        assertEquals("AbCDefghij", status.getRecapitalizedString());
        status.rotate();
        assertEquals("abcdefghij", status.getRecapitalizedString());

        status.start(29, 40, "Abcd efghij", Locale.ENGLISH, SPACE);
        status.rotate();
        assertEquals("abcd efghij", status.getRecapitalizedString());
        assertEquals(29, status.getNewCursorStart());
        assertEquals(40, status.getNewCursorEnd());
        status.rotate();
        assertEquals("Abcd Efghij", status.getRecapitalizedString());
        status.rotate();
        assertEquals("ABCD EFGHIJ", status.getRecapitalizedString());
        status.rotate();
        assertEquals("Abcd efghij", status.getRecapitalizedString());
        status.rotate();
        assertEquals("abcd efghij", status.getRecapitalizedString());

        status.start(30, 34, "grüß", Locale.GERMAN, SPACE);
        status.rotate();
        assertEquals("Grüß", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(34, status.getNewCursorEnd());
        status.rotate();
        assertEquals("GRÜSS", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(35, status.getNewCursorEnd());
        status.rotate();
        assertEquals("grüß", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(34, status.getNewCursorEnd());
        status.rotate();
        assertEquals("Grüß", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(34, status.getNewCursorEnd());

        status.start(30, 33, "œuf", Locale.FRENCH, SPACE);
        status.rotate();
        assertEquals("Œuf", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(33, status.getNewCursorEnd());
        status.rotate();
        assertEquals("ŒUF", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(33, status.getNewCursorEnd());
        status.rotate();
        assertEquals("œuf", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(33, status.getNewCursorEnd());
        status.rotate();
        assertEquals("Œuf", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(33, status.getNewCursorEnd());

        status.start(30, 33, "œUf", Locale.FRENCH, SPACE);
        status.rotate();
        assertEquals("œuf", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(33, status.getNewCursorEnd());
        status.rotate();
        assertEquals("Œuf", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(33, status.getNewCursorEnd());
        status.rotate();
        assertEquals("ŒUF", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(33, status.getNewCursorEnd());
        status.rotate();
        assertEquals("œUf", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(33, status.getNewCursorEnd());
        status.rotate();
        assertEquals("œuf", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(33, status.getNewCursorEnd());

        status.start(30, 35, "école", Locale.FRENCH, SPACE);
        status.rotate();
        assertEquals("École", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(35, status.getNewCursorEnd());
        status.rotate();
        assertEquals("ÉCOLE", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(35, status.getNewCursorEnd());
        status.rotate();
        assertEquals("école", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(35, status.getNewCursorEnd());
        status.rotate();
        assertEquals("École", status.getRecapitalizedString());
        assertEquals(30, status.getNewCursorStart());
        assertEquals(35, status.getNewCursorEnd());
    }
}
