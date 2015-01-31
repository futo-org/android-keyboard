/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.inputmethod.keyboard;

import static org.junit.Assert.assertEquals;

import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;

import org.junit.Test;

@SmallTest
public class KeyboardLayoutTest {

    private KeyboardLayout mKeyboardLayout;

    @Test
    public void testNewKeyboardLayout() {
        KeyboardLayout keyboardLayout = KeyboardLayout
                .newKeyboardLayout(new ArrayList<Key>(), 11, 12, 13, 14);

        assertEquals(11, keyboardLayout.mMostCommonKeyWidth);
        assertEquals(12, keyboardLayout.mMostCommonKeyHeight);
        assertEquals(13, keyboardLayout.mKeyboardWidth);
        assertEquals(14, keyboardLayout.mKeyboardHeight);

        assertEquals(0, keyboardLayout.getKeyCodes().length);
        assertEquals(0, keyboardLayout.getKeyWidths().length);
        assertEquals(0, keyboardLayout.getKeyHeights().length);
        assertEquals(0, keyboardLayout.getKeyXCoordinates().length);
        assertEquals(0, keyboardLayout.getKeyYCoordinates().length);

        Key key1 = new Key("label1", 101, 102, "101", "101hint", 103, 104, 105, 106, 1100, 1101, 2, 2);
        Key key2 = new Key("label2", 201, 202, "201", "201hint", 203, 204, 205, 206, 2100, 2201, 2, 2);

        ArrayList<Key> sortedKeys = new ArrayList<>(2);
        sortedKeys.add(key1);
        sortedKeys.add(key2);
        keyboardLayout = KeyboardLayout.newKeyboardLayout(sortedKeys, 11, 12, 13, 14);
        assertEquals(2, keyboardLayout.getKeyCodes().length);
        assertEquals(2, keyboardLayout.getKeyWidths().length);
        assertEquals(2, keyboardLayout.getKeyHeights().length);
        assertEquals(2, keyboardLayout.getKeyXCoordinates().length);
        assertEquals(2, keyboardLayout.getKeyYCoordinates().length);
    }
}
