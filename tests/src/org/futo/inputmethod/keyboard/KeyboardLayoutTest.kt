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
package org.futo.inputmethod.keyboard

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import org.futo.inputmethod.v2keyboard.KeyVisualStyle
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyboardLayoutTest {
    @Test
    fun testNewKeyboardLayout() {
        var keyboardLayout = KeyboardLayout
            .newKeyboardLayout(ArrayList(), 11, 12, 13, 14)

        Assert.assertEquals(11, keyboardLayout.mMostCommonKeyWidth.toLong())
        Assert.assertEquals(12, keyboardLayout.mMostCommonKeyHeight.toLong())
        Assert.assertEquals(13, keyboardLayout.mKeyboardWidth.toLong())
        Assert.assertEquals(14, keyboardLayout.mKeyboardHeight.toLong())

        Assert.assertEquals(0, keyboardLayout.keyCodes.size.toLong())
        Assert.assertEquals(0, keyboardLayout.keyWidths.size.toLong())
        Assert.assertEquals(0, keyboardLayout.keyHeights.size.toLong())
        Assert.assertEquals(0, keyboardLayout.keyXCoordinates.size.toLong())
        Assert.assertEquals(0, keyboardLayout.keyYCoordinates.size.toLong())

        val key1 = Key(
            code = 101,
            label = "label1",
            hintLabel = "101hint",
            width = 1100,
            height = 1101,
            horizontalGap = 10,
            verticalGap = 10,
            actionFlags = 102,
            labelFlags = 103,
            visualStyle = KeyVisualStyle.Normal,
            x = 105,
            y = 106
        )

        val key2 = Key(
            code = 201,
            label = "label2",
            hintLabel = "201hint",
            width = 2100,
            height = 2101,
            horizontalGap = 10,
            verticalGap = 10,
            actionFlags = 202,
            labelFlags = 203,
            visualStyle = KeyVisualStyle.Normal,
            x = 205,
            y = 206
        )

        //Key key1 = new Key("label1", 101, 102, "101", "101hint", 103, 104, 105, 106, 1100, 1101,
        //        10, 10);
        //Key key2 = new Key("label2", 201, 103, "201", "201hint", 203, 204, 205, 206, 2100, 2101,
        //        10, 10);
        val sortedKeys = ArrayList<Key>(2)
        sortedKeys.add(key1)
        sortedKeys.add(key2)
        keyboardLayout = KeyboardLayout.newKeyboardLayout(sortedKeys, 11, 12, 13, 14)
        Assert.assertEquals(2, keyboardLayout.keyCodes.size.toLong())
        Assert.assertEquals(2, keyboardLayout.keyWidths.size.toLong())
        Assert.assertEquals(2, keyboardLayout.keyHeights.size.toLong())
        Assert.assertEquals(2, keyboardLayout.keyXCoordinates.size.toLong())
        Assert.assertEquals(2, keyboardLayout.keyYCoordinates.size.toLong())

        Assert.assertEquals(102, keyboardLayout.keyCodes[0].toLong())
        // xCo + horizontalGap/2
        Assert.assertEquals((105 + 5).toLong(), keyboardLayout.keyXCoordinates[0].toLong())
        Assert.assertEquals(106, keyboardLayout.keyYCoordinates[0].toLong())
        // width - horizontalGap
        Assert.assertEquals((1100 - 10).toLong(), keyboardLayout.keyWidths[0].toLong())
        // height - verticalGap
        Assert.assertEquals((1101 - 10).toLong(), keyboardLayout.keyHeights[0].toLong())

        Assert.assertEquals(103, keyboardLayout.keyCodes[1].toLong())
        // xCo + horizontalGap/2
        Assert.assertEquals((205 + 5).toLong(), keyboardLayout.keyXCoordinates[1].toLong())
        Assert.assertEquals(206, keyboardLayout.keyYCoordinates[1].toLong())
        // width - horizontalGap
        Assert.assertEquals((2100 - 10).toLong(), keyboardLayout.keyWidths[1].toLong())
        // height - verticalGap
        Assert.assertEquals((2101 - 10).toLong(), keyboardLayout.keyHeights[1].toLong())
    }
}
