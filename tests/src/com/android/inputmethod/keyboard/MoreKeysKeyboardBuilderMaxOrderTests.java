/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.keyboard.MoreKeysKeyboard.MoreKeysKeyboardParams;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class MoreKeysKeyboardBuilderMaxOrderTests {
    private static final int WIDTH = 10;
    private static final int HEIGHT = 10;

    private static final int KEYBOARD_WIDTH = WIDTH * 10;
    private static final int XPOS_L0 = WIDTH * 0 + WIDTH / 2;
    private static final int XPOS_L1 = WIDTH * 1 + WIDTH / 2;
    private static final int XPOS_L2 = WIDTH * 2 + WIDTH / 2;
    private static final int XPOS_L3 = WIDTH * 3 + WIDTH / 2;
    private static final int XPOS_M0 = WIDTH * 4 + WIDTH / 2;
    private static final int XPOS_M1 = WIDTH * 5 + WIDTH / 2;
    private static final int XPOS_R3 = WIDTH * 6 + WIDTH / 2;
    private static final int XPOS_R2 = WIDTH * 7 + WIDTH / 2;
    private static final int XPOS_R1 = WIDTH * 8 + WIDTH / 2;
    private static final int XPOS_R0 = WIDTH * 9 + WIDTH / 2;

    private static MoreKeysKeyboardParams createParams(final int numKeys, final int maxColumns,
            final int coordXInParent) {
        final MoreKeysKeyboardParams params = new MoreKeysKeyboardParams();
        params.setParameters(numKeys, maxColumns, WIDTH, HEIGHT, coordXInParent, KEYBOARD_WIDTH,
                false /* isMoreKeysFixedColumn */, false /* isMoreKeysFixedOrder */,
                0 /* dividerWidth */);
        return params;
    }

    @Test
    public void testLayoutError() {
        MoreKeysKeyboardParams params = null;
        try {
            final int maxColumns = KEYBOARD_WIDTH / WIDTH;
            params = createParams(maxColumns + 1, maxColumns + 1, HEIGHT);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Too small keyboard to hold more keys keyboard.
        }
        assertNull("Too small keyboard to hold more keys keyboard", params);
    }

    // More keys keyboard layout test.
    // "[n]" represents n-th key position in more keys keyboard.
    // "<1>" is the default key.

    // <1>
    @Test
    public void testLayout1KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_M0);
        assertEquals("1 key max 5 M0 columns", 1, params.mNumColumns);
        assertEquals("1 key max 5 M0 rows", 1, params.mNumRows);
        assertEquals("1 key max 5 M0 left", 0, params.mLeftKeys);
        assertEquals("1 key max 5 M0 right", 1, params.mRightKeys);
        assertEquals("1 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key max 5 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |<1>
    @Test
    public void testLayout1KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_L0);
        assertEquals("1 key max 5 L0 columns", 1, params.mNumColumns);
        assertEquals("1 key max 5 L0 rows", 1, params.mNumRows);
        assertEquals("1 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("1 key max 5 L0 right", 1, params.mRightKeys);
        assertEquals("1 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1>
    @Test
    public void testLayout1KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_L1);
        assertEquals("1 key max 5 L1 columns", 1, params.mNumColumns);
        assertEquals("1 key max 5 L1 rows", 1, params.mNumRows);
        assertEquals("1 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("1 key max 5 L1 right", 1, params.mRightKeys);
        assertEquals("1 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key max 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ <1>
    @Test
    public void testLayout1KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_L2);
        assertEquals("1 key max 5 L2 columns", 1, params.mNumColumns);
        assertEquals("1 key max 5 L2 rows", 1, params.mNumRows);
        assertEquals("1 key max 5 L2 left", 0, params.mLeftKeys);
        assertEquals("1 key max 5 L2 right", 1, params.mRightKeys);
        assertEquals("1 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key max 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key max 5 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1>|
    @Test
    public void testLayout1KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_R0);
        assertEquals("1 key max 5 R0 columns", 1, params.mNumColumns);
        assertEquals("1 key max 5 R0 rows", 1, params.mNumRows);
        assertEquals("1 key max 5 R0 left", 0, params.mLeftKeys);
        assertEquals("1 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("1 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key max 5 R0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1> ___|
    @Test
    public void testLayout1KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_R1);
        assertEquals("1 key max 5 R1 columns", 1, params.mNumColumns);
        assertEquals("1 key max 5 R1 rows", 1, params.mNumRows);
        assertEquals("1 key max 5 R1 left", 0, params.mLeftKeys);
        assertEquals("1 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("1 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key max 5 R1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1> ___ ___|
    @Test
    public void testLayout1KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_R2);
        assertEquals("1 key max 5 R2 columns", 1, params.mNumColumns);
        assertEquals("1 key max 5 R2 rows", 1, params.mNumRows);
        assertEquals("1 key max 5 R2 left", 0, params.mLeftKeys);
        assertEquals("1 key max 5 R2 right", 1, params.mRightKeys);
        assertEquals("1 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key max 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key max 5 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1> [2]
    @Test
    public void testLayout2KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_M0);
        assertEquals("2 key max 5 M0 columns", 2, params.mNumColumns);
        assertEquals("2 key max 5 M0 rows", 1, params.mNumRows);
        assertEquals("2 key max 5 M0 left", 0, params.mLeftKeys);
        assertEquals("2 key max 5 M0 right", 2, params.mRightKeys);
        assertEquals("2 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key max 5 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |<1> [2]
    @Test
    public void testLayout2KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_L0);
        assertEquals("2 key max 5 L0 columns", 2, params.mNumColumns);
        assertEquals("2 key max 5 L0 rows", 1, params.mNumRows);
        assertEquals("2 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("2 key max 5 L0 right", 2, params.mRightKeys);
        assertEquals("2 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key max 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2]
    @Test
    public void testLayout2KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_L1);
        assertEquals("2 key max 5 L1 columns", 2, params.mNumColumns);
        assertEquals("2 key max 5 L1 rows", 1, params.mNumRows);
        assertEquals("2 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("2 key max 5 L1 right", 2, params.mRightKeys);
        assertEquals("2 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key max 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key max 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ <1> [2]
    @Test
    public void testLayout2KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_L2);
        assertEquals("2 key max 5 L2 columns", 2, params.mNumColumns);
        assertEquals("2 key max 5 L2 rows", 1, params.mNumRows);
        assertEquals("2 key max 5 L2 left", 0, params.mLeftKeys);
        assertEquals("2 key max 5 L2 right", 2, params.mRightKeys);
        assertEquals("2 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key max 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key max 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key max 5 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [2] <1>|
    @Test
    public void testLayout2KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_R0);
        assertEquals("2 key max 5 R0 columns", 2, params.mNumColumns);
        assertEquals("2 key max 5 R0 rows", 1, params.mNumRows);
        assertEquals("2 key max 5 R0 left", 1, params.mLeftKeys);
        assertEquals("2 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("2 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key max 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("2 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key max 5 R0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [2] <1> ___|
    @Test
    public void testLayout2KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_R1);
        assertEquals("2 key max 5 R1 columns", 2, params.mNumColumns);
        assertEquals("2 key max 5 R1 rows", 1, params.mNumRows);
        assertEquals("2 key max 5 R1 left", 1, params.mLeftKeys);
        assertEquals("2 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("2 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key max 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("2 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key max 5 R1 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // <1> [2] ___|
    @Test
    public void testLayout2KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_R2);
        assertEquals("2 key max 5 R2 columns", 2, params.mNumColumns);
        assertEquals("2 key max 5 R2 rows", 1, params.mNumRows);
        assertEquals("2 key max 5 R2 left", 0, params.mLeftKeys);
        assertEquals("2 key max 5 R2 right", 2, params.mRightKeys);
        assertEquals("2 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key max 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key max 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key max 5 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [3] <1> [2]
    @Test
    public void testLayout3KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_M0);
        assertEquals("3 key max 5 M0 columns", 3, params.mNumColumns);
        assertEquals("3 key max 5 M0 rows", 1, params.mNumRows);
        assertEquals("3 key max 5 M0 left", 1, params.mLeftKeys);
        assertEquals("3 key max 5 M0 right", 2, params.mRightKeys);
        assertEquals("3 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 5 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3]
    @Test
    public void testLayout3KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_L0);
        assertEquals("3 key max 5 L0 columns", 3, params.mNumColumns);
        assertEquals("3 key max 5 L0 rows", 1, params.mNumRows);
        assertEquals("3 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("3 key max 5 L0 right", 3, params.mRightKeys);
        assertEquals("3 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("3 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3]
    @Test
    public void testLayout3KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_L1);
        assertEquals("3 key max 5 L1 columns", 3, params.mNumColumns);
        assertEquals("3 key max 5 L1 rows", 1, params.mNumRows);
        assertEquals("3 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("3 key max 5 L1 right", 3, params.mRightKeys);
        assertEquals("3 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("3 key max 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2]
    @Test
    public void testLayout3KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_L2);
        assertEquals("3 key max 5 L2 columns", 3, params.mNumColumns);
        assertEquals("3 key max 5 L2 rows", 1, params.mNumRows);
        assertEquals("3 key max 5 L2 left", 1, params.mLeftKeys);
        assertEquals("3 key max 5 L2 right", 2, params.mRightKeys);
        assertEquals("3 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key max 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3] [2] <1>|
    @Test
    public void testLayout3KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_R0);
        assertEquals("3 key max 5 R0 columns", 3, params.mNumColumns);
        assertEquals("3 key max 5 R0 rows", 1, params.mNumRows);
        assertEquals("3 key max 5 R0 left", 2, params.mLeftKeys);
        assertEquals("3 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("3 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key max 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("3 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 5 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [3] [2] <1> ___|
    @Test
    public void testLayout3KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_R1);
        assertEquals("3 key max 5 R1 columns", 3, params.mNumColumns);
        assertEquals("3 key max 5 R1 rows", 1, params.mNumRows);
        assertEquals("3 key max 5 R1 left", 2, params.mLeftKeys);
        assertEquals("3 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("3 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key max 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("3 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 5 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [3] <1> [2] ___|
    @Test
    public void testLayout3KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_R2);
        assertEquals("3 key max 5 R2 columns", 3, params.mNumColumns);
        assertEquals("3 key max 5 R2 rows", 1, params.mNumRows);
        assertEquals("3 key max 5 R2 left", 1, params.mLeftKeys);
        assertEquals("3 key max 5 R2 right", 2, params.mRightKeys);
        assertEquals("3 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key max 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 5 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3]
    // <1> [2]
    @Test
    public void testLayout3KeyMax2M0() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_M0);
        assertEquals("3 key max 2 M0 columns", 2, params.mNumColumns);
        assertEquals("3 key max 2 M0 rows", 2, params.mNumRows);
        assertEquals("3 key max 2 M0 left", 0, params.mLeftKeys);
        assertEquals("3 key max 2 M0 right", 2, params.mRightKeys);
        assertEquals("3 key max 2 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 2 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 2 M0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key max 2 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 2 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |[3]
    // |<1> [2]
    @Test
    public void testLayout3KeyMax2L0() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_L0);
        assertEquals("3 key max 2 L0 columns", 2, params.mNumColumns);
        assertEquals("3 key max 2 L0 rows", 2, params.mNumRows);
        assertEquals("3 key max 2 L0 left", 0, params.mLeftKeys);
        assertEquals("3 key max 2 L0 right", 2, params.mRightKeys);
        assertEquals("3 key max 2 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 2 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 2 L0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key max 2 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 2 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3]
    // |___ <1> [2]
    @Test
    public void testLayout3KeyMax2L1() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_L1);
        assertEquals("3 key max 2 L1 columns", 2, params.mNumColumns);
        assertEquals("3 key max 2 L1 rows", 2, params.mNumRows);
        assertEquals("3 key max 2 L1 left", 0, params.mLeftKeys);
        assertEquals("3 key max 2 L1 right", 2, params.mRightKeys);
        assertEquals("3 key max 2 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 2 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 2 L1 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key max 2 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 2 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |        [3]
    // |___ ___ <1> [2]
    @Test
    public void testLayout3KeyMax2L2() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_L2);
        assertEquals("3 key max 2 L2 columns", 2, params.mNumColumns);
        assertEquals("3 key max 2 L2 rows", 2, params.mNumRows);
        assertEquals("3 key max 2 L2 left", 0, params.mLeftKeys);
        assertEquals("3 key max 2 L2 right", 2, params.mRightKeys);
        assertEquals("3 key max 2 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 2 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 2 L2 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key max 2 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 2 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    //     [3]|
    // [2] <1>|
    @Test
    public void testLayout3KeyMax2R0() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_R0);
        assertEquals("3 key max 2 R0 columns", 2, params.mNumColumns);
        assertEquals("3 key max 2 R0 rows", 2, params.mNumRows);
        assertEquals("3 key max 2 R0 left", 1, params.mLeftKeys);
        assertEquals("3 key max 2 R0 right", 1, params.mRightKeys);
        assertEquals("3 key max 2 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 2 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key max 2 R0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key max 2 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 2 R0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [3]    |
    // [2] <1> ___|
    @Test
    public void testLayout3KeyMax2R1() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_R1);
        assertEquals("3 key max 2 R1 columns", 2, params.mNumColumns);
        assertEquals("3 key max 2 R1 rows", 2, params.mNumRows);
        assertEquals("3 key max 2 R1 left", 1, params.mLeftKeys);
        assertEquals("3 key max 2 R1 right", 1, params.mRightKeys);
        assertEquals("3 key max 2 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 2 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key max 2 R1 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key max 2 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 2 R1 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3]        |
    // <1> [2] ___|
    @Test
    public void testLayout3KeyMax2R2() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_R2);
        assertEquals("3 key max 2 R2 columns", 2, params.mNumColumns);
        assertEquals("3 key max 2 R2 rows", 2, params.mNumRows);
        assertEquals("3 key max 2 R2 left", 0, params.mLeftKeys);
        assertEquals("3 key max 2 R2 right", 2, params.mRightKeys);
        assertEquals("3 key max 2 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key max 2 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key max 2 R2 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key max 2 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key max 2 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [3] [4]
    // <1> [2]
    @Test
    public void testLayout4KeyMax3M0() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_M0);
        assertEquals("4 key max 3 M0 columns", 2, params.mNumColumns);
        assertEquals("4 key max 3 M0 rows", 2, params.mNumRows);
        assertEquals("4 key max 3 M0 left", 0, params.mLeftKeys);
        assertEquals("4 key max 3 M0 right", 2, params.mRightKeys);
        assertEquals("4 key max 3 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 3 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 3 M0 [3]", 0, params.getColumnPos(2));
        assertEquals("4 key max 3 M0 [4]", 1, params.getColumnPos(3));
        assertEquals("4 key max 3 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 3 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |[3] [4]
    // |<1> [2]
    @Test
    public void testLayout4KeyMax3L0() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_L0);
        assertEquals("4 key max 3 L0 columns", 2, params.mNumColumns);
        assertEquals("4 key max 3 L0 rows", 2, params.mNumRows);
        assertEquals("4 key max 3 L0 left", 0, params.mLeftKeys);
        assertEquals("4 key max 3 L0 right", 2, params.mRightKeys);
        assertEquals("4 key max 3 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 3 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 3 L0 [3]", 0, params.getColumnPos(2));
        assertEquals("4 key max 3 L0 [4]", 1, params.getColumnPos(3));
        assertEquals("4 key max 3 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 3 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] [4]
    // |___ <1> [2]
    @Test
    public void testLayout4KeyMax3L1() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_L1);
        assertEquals("4 key max 3 L1 columns", 2, params.mNumColumns);
        assertEquals("4 key max 3 L1 rows", 2, params.mNumRows);
        assertEquals("4 key max 3 L1 left", 0, params.mLeftKeys);
        assertEquals("4 key max 3 L1 right", 2, params.mRightKeys);
        assertEquals("4 key max 3 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 3 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 3 L1 [3]", 0, params.getColumnPos(2));
        assertEquals("4 key max 3 L1 [4]", 1, params.getColumnPos(3));
        assertEquals("4 key max 3 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 3 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [3] [4]
    // |___ ___ <1> [2]
    @Test
    public void testLayout4KeyMax3L2() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_L2);
        assertEquals("4 key max 3 L2 columns", 2, params.mNumColumns);
        assertEquals("4 key max 3 L2 rows", 2, params.mNumRows);
        assertEquals("4 key max 3 L2 left", 0, params.mLeftKeys);
        assertEquals("4 key max 3 L2 right", 2, params.mRightKeys);
        assertEquals("4 key max 3 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 3 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 3 L2 [3]", 0, params.getColumnPos(2));
        assertEquals("4 key max 3 L2 [4]", 1, params.getColumnPos(3));
        assertEquals("4 key max 3 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 3 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [4] [3]|
    // [2] <1>|
    @Test
    public void testLayout4KeyMax3R0() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_R0);
        assertEquals("4 key max 3 R0 columns", 2, params.mNumColumns);
        assertEquals("4 key max 3 R0 rows", 2, params.mNumRows);
        assertEquals("4 key max 3 R0 left", 1, params.mLeftKeys);
        assertEquals("4 key max 3 R0 right", 1, params.mRightKeys);
        assertEquals("4 key max 3 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 3 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key max 3 R0 [3]", 0, params.getColumnPos(2));
        assertEquals("4 key max 3 R0 [4]", -1, params.getColumnPos(3));
        assertEquals("4 key max 3 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 3 R0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [4] [3] ___|
    // [2] <1> ___|
    @Test
    public void testLayout4KeyMax3R1() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_R1);
        assertEquals("4 key max 3 R1 columns", 2, params.mNumColumns);
        assertEquals("4 key max 3 R1 rows", 2, params.mNumRows);
        assertEquals("4 key max 3 R1 left", 1, params.mLeftKeys);
        assertEquals("4 key max 3 R1 right", 1, params.mRightKeys);
        assertEquals("4 key max 3 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 3 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key max 3 R1 [3]", 0, params.getColumnPos(2));
        assertEquals("4 key max 3 R1 [4]", -1, params.getColumnPos(3));
        assertEquals("4 key max 3 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 3 R1 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3] [4] ___|
    // <1> [2] ___|
    @Test
    public void testLayout4KeyMax3R2() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_R2);
        assertEquals("4 key max 3 R2 columns", 2, params.mNumColumns);
        assertEquals("4 key max 3 R2 rows", 2, params.mNumRows);
        assertEquals("4 key max 3 R2 left", 0, params.mLeftKeys);
        assertEquals("4 key max 3 R2 right", 2, params.mRightKeys);
        assertEquals("4 key max 3 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 3 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 3 R2 [3]", 0, params.getColumnPos(2));
        assertEquals("4 key max 3 R2 [4]", 1, params.getColumnPos(3));
        assertEquals("4 key max 3 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 3 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [3] <1> [2] [4]
    @Test
    public void testLayout4KeyMax4M0() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_M0);
        assertEquals("4 key max 4 M0 columns", 4, params.mNumColumns);
        assertEquals("4 key max 4 M0 rows", 1, params.mNumRows);
        assertEquals("4 key max 4 M0 left", 1, params.mLeftKeys);
        assertEquals("4 key max 4 M0 right", 3, params.mRightKeys);
        assertEquals("4 key max 4 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 4 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 4 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key max 4 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key max 4 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4]
    @Test
    public void testLayout4KeyMax4L0() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_L0);
        assertEquals("4 key max 4 L0 columns", 4, params.mNumColumns);
        assertEquals("4 key max 4 L0 rows", 1, params.mNumRows);
        assertEquals("4 key max 4 L0 left", 0, params.mLeftKeys);
        assertEquals("4 key max 4 L0 right", 4, params.mRightKeys);
        assertEquals("4 key max 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key max 4 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key max 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout4KeyMax4L1() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_L1);
        assertEquals("4 key max 4 L1 columns", 4, params.mNumColumns);
        assertEquals("4 key max 4 L1 rows", 1, params.mNumRows);
        assertEquals("4 key max 4 L1 left", 0, params.mLeftKeys);
        assertEquals("4 key max 4 L1 right", 4, params.mRightKeys);
        assertEquals("4 key max 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key max 4 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key max 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2] [4]
    @Test
    public void testLayout4KeyMax4L2() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_L2);
        assertEquals("4 key max 4 L2 columns", 4, params.mNumColumns);
        assertEquals("4 key max 4 L2 rows", 1, params.mNumRows);
        assertEquals("4 key max 4 L2 left", 1, params.mLeftKeys);
        assertEquals("4 key max 4 L2 right", 3, params.mRightKeys);
        assertEquals("4 key max 4 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 4 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 4 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key max 4 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key max 4 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] <1>|
    @Test
    public void testLayout4KeyMax4R0() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_R0);
        assertEquals("4 key max 4 R0 columns", 4, params.mNumColumns);
        assertEquals("4 key max 4 R0 rows", 1, params.mNumRows);
        assertEquals("4 key max 4 R0 left", 3, params.mLeftKeys);
        assertEquals("4 key max 4 R0 right", 1, params.mRightKeys);
        assertEquals("4 key max 4 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 4 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key max 4 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key max 4 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key max 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 4 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] <1> ___|
    @Test
    public void testLayout4KeyMax4R1() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_R1);
        assertEquals("4 key max 4 R1 columns", 4, params.mNumColumns);
        assertEquals("4 key max 4 R1 rows", 1, params.mNumRows);
        assertEquals("4 key max 4 R1 left", 3, params.mLeftKeys);
        assertEquals("4 key max 4 R1 right", 1, params.mRightKeys);
        assertEquals("4 key max 4 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 4 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key max 4 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key max 4 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key max 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 4 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] <1> [2] ___|
    @Test
    public void testLayout4KeyMax4R2() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_R2);
        assertEquals("4 key max 4 R2 columns", 4, params.mNumColumns);
        assertEquals("4 key max 4 R2 rows", 1, params.mNumRows);
        assertEquals("4 key max 4 R2 left", 2, params.mLeftKeys);
        assertEquals("4 key max 4 R2 right", 2, params.mRightKeys);
        assertEquals("4 key max 4 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 4 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 4 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key max 4 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("4 key max 4 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 4 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [3] <1> [2] [4]
    @Test
    public void testLayout4KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_M0);
        assertEquals("4 key max 5 M0 columns", 4, params.mNumColumns);
        assertEquals("4 key max 5 M0 rows", 1, params.mNumRows);
        assertEquals("4 key max 5 M0 left", 1, params.mLeftKeys);
        assertEquals("4 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("4 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 5 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4]
    @Test
    public void testLayout4KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_L0);
        assertEquals("4 key max 5 L0 columns", 4, params.mNumColumns);
        assertEquals("4 key max 5 L0 rows", 1, params.mNumRows);
        assertEquals("4 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("4 key max 5 L0 right", 4, params.mRightKeys);
        assertEquals("4 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key max 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout4KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_L1);
        assertEquals("4 key max 5 L1 columns", 4, params.mNumColumns);
        assertEquals("4 key max 5 L1 rows", 1, params.mNumRows);
        assertEquals("4 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("4 key max 5 L1 right", 4, params.mRightKeys);
        assertEquals("4 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key max 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key max 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2] [4]
    @Test
    public void testLayout4KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_L2);
        assertEquals("4 key max 5 L2 columns", 4, params.mNumColumns);
        assertEquals("4 key max 5 L2 rows", 1, params.mNumRows);
        assertEquals("4 key max 5 L2 left", 1, params.mLeftKeys);
        assertEquals("4 key max 5 L2 right", 3, params.mRightKeys);
        assertEquals("4 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key max 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key max 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] <1>|
    @Test
    public void testLayout4KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_R0);
        assertEquals("4 key max 5 R0 columns", 4, params.mNumColumns);
        assertEquals("4 key max 5 R0 rows", 1, params.mNumRows);
        assertEquals("4 key max 5 R0 left", 3, params.mLeftKeys);
        assertEquals("4 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("4 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key max 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key max 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 5 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] <1> ___|
    @Test
    public void testLayout4KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_R1);
        assertEquals("4 key max 5 R1 columns", 4, params.mNumColumns);
        assertEquals("4 key max 5 R1 rows", 1, params.mNumRows);
        assertEquals("4 key max 5 R1 left", 3, params.mLeftKeys);
        assertEquals("4 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("4 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key max 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key max 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 5 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] <1> [2] ___|
    @Test
    public void testLayout4KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_R2);
        assertEquals("4 key max 5 R2 columns", 4, params.mNumColumns);
        assertEquals("4 key max 5 R2 rows", 1, params.mNumRows);
        assertEquals("4 key max 5 R2 left", 2, params.mLeftKeys);
        assertEquals("4 key max 5 R2 right", 2, params.mRightKeys);
        assertEquals("4 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key max 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key max 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key max 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("4 key max 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key max 5 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [4] [5]
    // [3] <1> [2]
    @Test
    public void testLayout5KeyMax3M0() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_M0);
        assertEquals("5 key max 3 M0 columns", 3, params.mNumColumns);
        assertEquals("5 key max 3 M0 rows", 2, params.mNumRows);
        assertEquals("5 key max 3 M0 left", 1, params.mLeftKeys);
        assertEquals("5 key max 3 M0 right", 2, params.mRightKeys);
        assertEquals("5 key max 3 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 3 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 3 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key max 3 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 3 M0 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 3 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key max 3 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4] [5]
    // |<1> [2] [3]
    @Test
    public void testLayout5KeyMax3L0() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_L0);
        assertEquals("5 key max 3 L0 columns", 3, params.mNumColumns);
        assertEquals("5 key max 3 L0 rows", 2, params.mNumRows);
        assertEquals("5 key max 3 L0 left", 0, params.mLeftKeys);
        assertEquals("5 key max 3 L0 right", 3, params.mRightKeys);
        assertEquals("5 key max 3 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 3 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 3 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key max 3 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 3 L0 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 3 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 3 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4] [5]
    // |___ <1> [2] [3]
    @Test
    public void testLayout5KeyMax3L1() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_L1);
        assertEquals("5 key max 3 L1 columns", 3, params.mNumColumns);
        assertEquals("5 key max 3 L1 rows", 2, params.mNumRows);
        assertEquals("5 key max 3 L1 left", 0, params.mLeftKeys);
        assertEquals("5 key max 3 L1 right", 3, params.mRightKeys);
        assertEquals("5 key max 3 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 3 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 3 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key max 3 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 3 L1 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 3 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 3 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [4] [5]
    // |___ [3] <1> [2]
    @Test
    public void testLayout5KeyMax3L2() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_L2);
        assertEquals("5 key max 3 L2 columns", 3, params.mNumColumns);
        assertEquals("5 key max 3 L2 rows", 2, params.mNumRows);
        assertEquals("5 key max 3 L2 left", 1, params.mLeftKeys);
        assertEquals("5 key max 3 L2 right", 2, params.mRightKeys);
        assertEquals("5 key max 3 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 3 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 3 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key max 3 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 3 L2 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 3 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key max 3 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [5] [4]|
    // [3] [2] <1>|
    @Test
    public void testLayout5KeyMax3R0() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_R0);
        assertEquals("5 key max 3 R0 columns", 3, params.mNumColumns);
        assertEquals("5 key max 3 R0 rows", 2, params.mNumRows);
        assertEquals("5 key max 3 R0 left", 2, params.mLeftKeys);
        assertEquals("5 key max 3 R0 right", 1, params.mRightKeys);
        assertEquals("5 key max 3 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 3 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key max 3 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key max 3 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 3 R0 [5]", -1, params.getColumnPos(4));
        assertEquals("5 key max 3 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 3 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [5] [4] ___|
    // [3] [2] <1> ___|
    @Test
    public void testLayout5KeyMax3R1() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_R1);
        assertEquals("5 key max 3 R1 columns", 3, params.mNumColumns);
        assertEquals("5 key max 3 R1 rows", 2, params.mNumRows);
        assertEquals("5 key max 3 R1 left", 2, params.mLeftKeys);
        assertEquals("5 key max 3 R1 right", 1, params.mRightKeys);
        assertEquals("5 key max 3 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 3 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key max 3 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key max 3 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 3 R1 [5]", -1, params.getColumnPos(4));
        assertEquals("5 key max 3 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 3 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [4] [5]   ___|
    // [3] <1> [2] ___|
    @Test
    public void testLayout5KeyMax3R2() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_R2);
        assertEquals("5 key max 3 R2 columns", 3, params.mNumColumns);
        assertEquals("5 key max 3 R2 rows", 2, params.mNumRows);
        assertEquals("5 key max 3 R2 left", 1, params.mLeftKeys);
        assertEquals("5 key max 3 R2 right", 2, params.mRightKeys);
        assertEquals("5 key max 3 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 3 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 3 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key max 3 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 3 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 3 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key max 3 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //   [4] [5]
    // [3] <1> [2]
    @Test
    public void testLayout5KeyMax4M0() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_M0);
        assertEquals("5 key max 4 M0 columns", 3, params.mNumColumns);
        assertEquals("5 key max 4 M0 rows", 2, params.mNumRows);
        assertEquals("5 key max 4 M0 left", 1, params.mLeftKeys);
        assertEquals("5 key max 4 M0 right", 2, params.mRightKeys);
        assertEquals("5 key max 4 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 4 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 4 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key max 4 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 4 M0 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 4 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key max 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4] [5]
    // |<1> [2] [3]
    @Test
    public void testLayout5KeyMax4L0() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_L0);
        assertEquals("5 key max 4 L0 columns", 3, params.mNumColumns);
        assertEquals("5 key max 4 L0 rows", 2, params.mNumRows);
        assertEquals("5 key max 4 L0 left", 0, params.mLeftKeys);
        assertEquals("5 key max 4 L0 right", 3, params.mRightKeys);
        assertEquals("5 key max 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key max 4 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 4 L0 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4] [5]
    // |___ <1> [2] [3]
    @Test
    public void testLayout5KeyMax4L1() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_L1);
        assertEquals("5 key max 4 L1 columns", 3, params.mNumColumns);
        assertEquals("5 key max 4 L1 rows", 2, params.mNumRows);
        assertEquals("5 key max 4 L1 left", 0, params.mLeftKeys);
        assertEquals("5 key max 4 L1 right", 3, params.mRightKeys);
        assertEquals("5 key max 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key max 4 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 4 L1 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [4] [5]
    // |___ [3] <1> [2]
    @Test
    public void testLayout5KeyMax4L2() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_L2);
        assertEquals("5 key max 4 L2 columns", 3, params.mNumColumns);
        assertEquals("5 key max 4 L2 rows", 2, params.mNumRows);
        assertEquals("5 key max 4 L2 left", 1, params.mLeftKeys);
        assertEquals("5 key max 4 L2 right", 2, params.mRightKeys);
        assertEquals("5 key max 4 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 4 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 4 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key max 4 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 4 L2 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 4 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key max 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [5] [4]|
    // [3] [2] <1>|
    @Test
    public void testLayout5KeyMax4R0() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_R0);
        assertEquals("5 key max 4 R0 columns", 3, params.mNumColumns);
        assertEquals("5 key max 4 R0 rows", 2, params.mNumRows);
        assertEquals("5 key max 4 R0 left", 2, params.mLeftKeys);
        assertEquals("5 key max 4 R0 right", 1, params.mRightKeys);
        assertEquals("5 key max 4 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 4 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key max 4 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key max 4 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 4 R0 [5]", -1, params.getColumnPos(4));
        assertEquals("5 key max 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 4 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [5] [4] ___|
    // [3] [2] <1> ___|
    @Test
    public void testLayout5KeyMax4R1() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_R1);
        assertEquals("5 key max 4 R1 columns", 3, params.mNumColumns);
        assertEquals("5 key max 4 R1 rows", 2, params.mNumRows);
        assertEquals("5 key max 4 R1 left", 2, params.mLeftKeys);
        assertEquals("5 key max 4 R1 right", 1, params.mRightKeys);
        assertEquals("5 key max 4 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 4 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key max 4 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key max 4 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 4 R1 [5]", -1, params.getColumnPos(4));
        assertEquals("5 key max 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 4 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [4] [5]   ___|
    // [3] <1> [2] ___|
    @Test
    public void testLayout5KeyMax4R2() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_R2);
        assertEquals("5 key max 4 R2 columns", 3, params.mNumColumns);
        assertEquals("5 key max 4 R2 rows", 2, params.mNumRows);
        assertEquals("5 key max 4 R2 left", 1, params.mLeftKeys);
        assertEquals("5 key max 4 R2 right", 2, params.mRightKeys);
        assertEquals("5 key max 4 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 4 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 4 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key max 4 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key max 4 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key max 4 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key max 4 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout5KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_M0);
        assertEquals("5 key max 5 M0 columns", 5, params.mNumColumns);
        assertEquals("5 key max 5 M0 rows", 1, params.mNumRows);
        assertEquals("5 key max 5 M0 left", 2, params.mLeftKeys);
        assertEquals("5 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("5 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key max 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("5 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout5KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_L0);
        assertEquals("5 key max 5 L0 columns", 5, params.mNumColumns);
        assertEquals("5 key max 5 L0 rows", 1, params.mNumRows);
        assertEquals("5 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("5 key max 5 L0 right", 5, params.mRightKeys);
        assertEquals("5 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key max 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key max 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("5 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout5KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_L1);
        assertEquals("5 key max 5 L1 columns", 5, params.mNumColumns);
        assertEquals("5 key max 5 L1 rows", 1, params.mNumRows);
        assertEquals("5 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("5 key max 5 L1 right", 5, params.mRightKeys);
        assertEquals("5 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key max 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key max 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("5 key max 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2] [4] [5]
    @Test
    public void testLayout5KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_L2);
        assertEquals("5 key max 5 L2 columns", 5, params.mNumColumns);
        assertEquals("5 key max 5 L2 rows", 1, params.mNumRows);
        assertEquals("5 key max 5 L2 left", 1, params.mLeftKeys);
        assertEquals("5 key max 5 L2 right", 4, params.mRightKeys);
        assertEquals("5 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key max 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key max 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("5 key max 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [5] [4] [3] [2] <1>|
    @Test
    public void testLayout5KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_R0);
        assertEquals("5 key max 5 R0 columns", 5, params.mNumColumns);
        assertEquals("5 key max 5 R0 rows", 1, params.mNumRows);
        assertEquals("5 key max 5 R0 left", 4, params.mLeftKeys);
        assertEquals("5 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("5 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key max 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key max 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("5 key max 5 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("5 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout5KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_R1);
        assertEquals("5 key max 5 R1 columns", 5, params.mNumColumns);
        assertEquals("5 key max 5 R1 rows", 1, params.mNumRows);
        assertEquals("5 key max 5 R1 left", 4, params.mLeftKeys);
        assertEquals("5 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("5 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key max 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key max 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("5 key max 5 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("5 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout5KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_R2);
        assertEquals("5 key max 5 R2 columns", 5, params.mNumColumns);
        assertEquals("5 key max 5 R2 rows", 1, params.mNumRows);
        assertEquals("5 key max 5 R2 left", 3, params.mLeftKeys);
        assertEquals("5 key max 5 R2 right", 2, params.mRightKeys);
        assertEquals("5 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key max 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key max 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key max 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("5 key max 5 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("5 key max 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key max 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [6] [4] [5]
    // [3] <1> [2]
    @Test
    public void testLayout6KeyMax4M0() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_M0);
        assertEquals("6 key max 4 M0 columns", 3, params.mNumColumns);
        assertEquals("6 key max 4 M0 rows", 2, params.mNumRows);
        assertEquals("6 key max 4 M0 left", 1, params.mLeftKeys);
        assertEquals("6 key max 4 M0 right", 2, params.mRightKeys);
        assertEquals("6 key max 4 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 4 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 4 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key max 4 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 4 M0 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 4 M0 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key max 4 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4] [5] [6]
    // |<1> [2] [3]
    @Test
    public void testLayout6KeyMax4L0() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_L0);
        assertEquals("6 key max 4 L0 columns", 3, params.mNumColumns);
        assertEquals("6 key max 4 L0 rows", 2, params.mNumRows);
        assertEquals("6 key max 4 L0 left", 0, params.mLeftKeys);
        assertEquals("6 key max 4 L0 right", 3, params.mRightKeys);
        assertEquals("6 key max 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key max 4 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 4 L0 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 4 L0 [6]", 2, params.getColumnPos(5));
        assertEquals("6 key max 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4] [5] [6]
    // |___ <1> [2] [3]
    @Test
    public void testLayout6KeyMax4L1() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_L1);
        assertEquals("6 key max 4 L1 columns", 3, params.mNumColumns);
        assertEquals("6 key max 4 L1 rows", 2, params.mNumRows);
        assertEquals("6 key max 4 L1 left", 0, params.mLeftKeys);
        assertEquals("6 key max 4 L1 right", 3, params.mRightKeys);
        assertEquals("6 key max 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key max 4 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 4 L1 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 4 L1 [6]", 2, params.getColumnPos(5));
        assertEquals("6 key max 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [4] [5]
    // |___ [3] <1> [2]
    @Test
    public void testLayout6KeyMax4L2() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_L2);
        assertEquals("6 key max 4 L2 columns", 3, params.mNumColumns);
        assertEquals("6 key max 4 L2 rows", 2, params.mNumRows);
        assertEquals("6 key max 4 L2 left", 1, params.mLeftKeys);
        assertEquals("6 key max 4 L2 right", 2, params.mRightKeys);
        assertEquals("6 key max 4 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 4 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 4 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key max 4 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 4 L2 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 4 L2 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key max 4 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [6] [5] [4]|
    // [3] [2] <1>|
    @Test
    public void testLayout6KeyMax4R0() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_R0);
        assertEquals("6 key max 4 R0 columns", 3, params.mNumColumns);
        assertEquals("6 key max 4 R0 rows", 2, params.mNumRows);
        assertEquals("6 key max 4 R0 left", 2, params.mLeftKeys);
        assertEquals("6 key max 4 R0 right", 1, params.mRightKeys);
        assertEquals("6 key max 4 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 4 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key max 4 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key max 4 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 4 R0 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key max 4 R0 [6]", -2, params.getColumnPos(5));
        assertEquals("6 key max 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 4 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [6] [5] [4] ___|
    // [3] [2] <1> ___|
    @Test
    public void testLayout6KeyMax4R1() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_R1);
        assertEquals("6 key max 4 R1 columns", 3, params.mNumColumns);
        assertEquals("6 key max 4 R1 rows", 2, params.mNumRows);
        assertEquals("6 key max 4 R1 left", 2, params.mLeftKeys);
        assertEquals("6 key max 4 R1 right", 1, params.mRightKeys);
        assertEquals("6 key max 4 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 4 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key max 4 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key max 4 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 4 R1 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key max 4 R1 [6]", -2, params.getColumnPos(5));
        assertEquals("6 key max 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 4 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [6] [4] [5] ___|
    // [3] <1> [2] ___|
    @Test
    public void testLayout6KeyMax4R2() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_R2);
        assertEquals("6 key max 4 R2 columns", 3, params.mNumColumns);
        assertEquals("6 key max 4 R2 rows", 2, params.mNumRows);
        assertEquals("6 key max 4 R2 left", 1, params.mLeftKeys);
        assertEquals("6 key max 4 R2 right", 2, params.mRightKeys);
        assertEquals("6 key max 4 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 4 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 4 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key max 4 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 4 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 4 R2 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key max 4 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 4 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [6] [4] [5]
    // [3] <1> [2]
    @Test
    public void testLayout6KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_M0);
        assertEquals("6 key max 5 M0 columns", 3, params.mNumColumns);
        assertEquals("6 key max 5 M0 rows", 2, params.mNumRows);
        assertEquals("6 key max 5 M0 left", 1, params.mLeftKeys);
        assertEquals("6 key max 5 M0 right", 2, params.mRightKeys);
        assertEquals("6 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key max 5 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 5 M0 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 5 M0 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 5 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4] [5] [6]
    // |<1> [2] [3]
    @Test
    public void testLayout6KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_L0);
        assertEquals("6 key max 5 L0 columns", 3, params.mNumColumns);
        assertEquals("6 key max 5 L0 rows", 2, params.mNumRows);
        assertEquals("6 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("6 key max 5 L0 right", 3, params.mRightKeys);
        assertEquals("6 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key max 5 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 5 L0 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 5 L0 [6]", 2, params.getColumnPos(5));
        assertEquals("6 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4] [5] [6]
    // |___ <1> [2] [3]
    @Test
    public void testLayout6KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_L1);
        assertEquals("6 key max 5 L1 columns", 3, params.mNumColumns);
        assertEquals("6 key max 5 L1 rows", 2, params.mNumRows);
        assertEquals("6 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("6 key max 5 L1 right", 3, params.mRightKeys);
        assertEquals("6 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key max 5 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 5 L1 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 5 L1 [6]", 2, params.getColumnPos(5));
        assertEquals("6 key max 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [4] [5]
    // |___ [3] <1> [2]
    @Test
    public void testLayout6KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_L2);
        assertEquals("6 key max 5 L2 columns", 3, params.mNumColumns);
        assertEquals("6 key max 5 L2 rows", 2, params.mNumRows);
        assertEquals("6 key max 5 L2 left", 1, params.mLeftKeys);
        assertEquals("6 key max 5 L2 right", 2, params.mRightKeys);
        assertEquals("6 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key max 5 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 5 L2 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 5 L2 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key max 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [6] [5] [4]|
    // [3] [2] <1>|
    @Test
    public void testLayout6KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_R0);
        assertEquals("6 key max 5 R0 columns", 3, params.mNumColumns);
        assertEquals("6 key max 5 R0 rows", 2, params.mNumRows);
        assertEquals("6 key max 5 R0 left", 2, params.mLeftKeys);
        assertEquals("6 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("6 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key max 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key max 5 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 5 R0 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key max 5 R0 [6]", -2, params.getColumnPos(5));
        assertEquals("6 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 5 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [6] [5] [4] ___|
    // [3] [2] <1> ___|
    @Test
    public void testLayout6KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_R1);
        assertEquals("6 key max 5 R1 columns", 3, params.mNumColumns);
        assertEquals("6 key max 5 R1 rows", 2, params.mNumRows);
        assertEquals("6 key max 5 R1 left", 2, params.mLeftKeys);
        assertEquals("6 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("6 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key max 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key max 5 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 5 R1 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key max 5 R1 [6]", -2, params.getColumnPos(5));
        assertEquals("6 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 5 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [6] [4] [5] ___|
    // [3] <1> [2] ___|
    @Test
    public void testLayout6KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_R2);
        assertEquals("6 key max 5 R2 columns", 3, params.mNumColumns);
        assertEquals("6 key max 5 R2 rows", 2, params.mNumRows);
        assertEquals("6 key max 5 R2 left", 1, params.mLeftKeys);
        assertEquals("6 key max 5 R2 right", 2, params.mRightKeys);
        assertEquals("6 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key max 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key max 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key max 5 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key max 5 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key max 5 R2 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key max 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key max 5 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4] [5] [6] [7] ___ ___ ___|
    @Test
    public void testLayout7KeyMax7L0() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L0);
        assertEquals("7 key max 7 L0 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 L0 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 L0 left", 0, params.mLeftKeys);
        assertEquals("7 key max 7 L0 right", 7, params.mRightKeys);
        assertEquals("7 key max 7 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 7 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key max 7 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key max 7 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key max 7 L0 [6]", 5, params.getColumnPos(5));
        assertEquals("7 key max 7 L0 [7]", 6, params.getColumnPos(6));
        assertEquals("7 key max 7 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4] [5] [6] [7] ___ ___|
    @Test
    public void testLayout7KeyMax7L1() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L1);
        assertEquals("7 key max 7 L1 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 L1 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 L1 left", 0, params.mLeftKeys);
        assertEquals("7 key max 7 L1 right", 7, params.mRightKeys);
        assertEquals("7 key max 7 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 7 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key max 7 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key max 7 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key max 7 L1 [6]", 5, params.getColumnPos(5));
        assertEquals("7 key max 7 L1 [7]", 6, params.getColumnPos(6));
        assertEquals("7 key max 7 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2] [4] [5] [6] [7] ___ ___|
    @Test
    public void testLayout7KeyMax7L2() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L2);
        assertEquals("7 key max 7 L2 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 L2 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 L2 left", 1, params.mLeftKeys);
        assertEquals("7 key max 7 L2 right", 6, params.mRightKeys);
        assertEquals("7 key max 7 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 7 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 7 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key max 7 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("7 key max 7 L2 [6]", 4, params.getColumnPos(5));
        assertEquals("7 key max 7 L2 [7]", 5, params.getColumnPos(6));
        assertEquals("7 key max 7 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |___ [5] [3] <1> [2] [4] [6] [7] ___ ___|
    @Test
    public void testLayout7KeyMax7L3() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L3);
        assertEquals("7 key max 7 L3 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 L3 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 L3 left", 2, params.mLeftKeys);
        assertEquals("7 key max 7 L3 right", 5, params.mRightKeys);
        assertEquals("7 key max 7 L3 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 L3 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 7 L3 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 7 L3 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key max 7 L3 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key max 7 L3 [6]", 3, params.getColumnPos(5));
        assertEquals("7 key max 7 L3 [7]", 4, params.getColumnPos(6));
        assertEquals("7 key max 7 L3 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 L3 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |___ [7] [5] [3] <1> [2] [4] [6] ___ ___|
    @Test
    public void testLayout7KeyMax7M0() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_M0);
        assertEquals("7 key max 7 M0 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 M0 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 M0 left", 3, params.mLeftKeys);
        assertEquals("7 key max 7 M0 right", 4, params.mRightKeys);
        assertEquals("7 key max 7 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 7 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 7 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key max 7 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key max 7 M0 [6]", 3, params.getColumnPos(5));
        assertEquals("7 key max 7 M0 [7]", -3, params.getColumnPos(6));
        assertEquals("7 key max 7 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 M0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [5] [3] <1> [2] [4] [6] ___|
    @Test
    public void testLayout7KeyMax7M1() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_M1);
        assertEquals("7 key max 7 M1 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 M1 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 M1 left", 3, params.mLeftKeys);
        assertEquals("7 key max 7 M1 right", 4, params.mRightKeys);
        assertEquals("7 key max 7 M1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 M1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 7 M1 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 7 M1 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key max 7 M1 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key max 7 M1 [6]", 3, params.getColumnPos(5));
        assertEquals("7 key max 7 M1 [7]", -3, params.getColumnPos(6));
        assertEquals("7 key max 7 M1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 M1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [6] [5] [3] <1> [2] [4] ___|
    @Test
    public void testLayout7KeyMax7R3() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R3);
        assertEquals("7 key max 7 R3 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 R3 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 R3 left", 4, params.mLeftKeys);
        assertEquals("7 key max 7 R3 right", 3, params.mRightKeys);
        assertEquals("7 key max 7 R3 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 R3 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 7 R3 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 7 R3 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key max 7 R3 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key max 7 R3 [6]", -3, params.getColumnPos(5));
        assertEquals("7 key max 7 R3 [7]", -4, params.getColumnPos(6));
        assertEquals("7 key max 7 R3 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 R3 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [6] [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout7KeyMax7R2() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R2);
        assertEquals("7 key max 7 R2 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 R2 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 R2 left", 5, params.mLeftKeys);
        assertEquals("7 key max 7 R2 right", 2, params.mRightKeys);
        assertEquals("7 key max 7 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 7 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 7 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("7 key max 7 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("7 key max 7 R2 [6]", -4, params.getColumnPos(5));
        assertEquals("7 key max 7 R2 [7]", -5, params.getColumnPos(6));
        assertEquals("7 key max 7 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 R2 default", WIDTH * 5, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [6] [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout7KeyMax7R1() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R1);
        assertEquals("7 key max 7 R1 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 R1 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 R1 left", 6, params.mLeftKeys);
        assertEquals("7 key max 7 R1 right", 1, params.mRightKeys);
        assertEquals("7 key max 7 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key max 7 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key max 7 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key max 7 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("7 key max 7 R1 [6]", -5, params.getColumnPos(5));
        assertEquals("7 key max 7 R1 [7]", -6, params.getColumnPos(6));
        assertEquals("7 key max 7 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 R1 default", WIDTH * 6, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [6] [5] [4] [3] [2] <1>|
    @Test
    public void testLayout7KeyMax7R0() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R0);
        assertEquals("7 key max 7 R0 columns", 7, params.mNumColumns);
        assertEquals("7 key max 7 R0 rows", 1, params.mNumRows);
        assertEquals("7 key max 7 R0 left", 6, params.mLeftKeys);
        assertEquals("7 key max 7 R0 right", 1, params.mRightKeys);
        assertEquals("7 key max 7 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 7 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key max 7 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key max 7 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key max 7 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("7 key max 7 R0 [6]", -5, params.getColumnPos(5));
        assertEquals("7 key max 7 R0 [7]", -6, params.getColumnPos(6));
        assertEquals("7 key max 7 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 7 R0 default", WIDTH * 6, params.getDefaultKeyCoordX());
    }

    //   [5] [6] [7]
    // [3] <1> [2] [4]
    @Test
    public void testLayout7KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_M0);
        assertEquals("7 key max 5 M0 columns", 4, params.mNumColumns);
        assertEquals("7 key max 5 M0 rows", 2, params.mNumRows);
        assertEquals("7 key max 5 M0 left", 1, params.mLeftKeys);
        assertEquals("7 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("7 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key max 5 M0 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key max 5 M0 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key max 5 M0 [7]", 2, params.getColumnPos(6));
        assertEquals("7 key max 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key max 5 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5] [6] [7]
    // |<1> [2] [3] [4]
    @Test
    public void testLayout7KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_L0);
        assertEquals("7 key max 5 L0 columns", 4, params.mNumColumns);
        assertEquals("7 key max 5 L0 rows", 2, params.mNumRows);
        assertEquals("7 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("7 key max 5 L0 right", 4, params.mRightKeys);
        assertEquals("7 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key max 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key max 5 L0 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key max 5 L0 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key max 5 L0 [7]", 2, params.getColumnPos(6));
        assertEquals("7 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6] [7]
    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout7KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_L1);
        assertEquals("7 key max 5 L1 columns", 4, params.mNumColumns);
        assertEquals("7 key max 5 L1 rows", 2, params.mNumRows);
        assertEquals("7 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("7 key max 5 L1 right", 4, params.mRightKeys);
        assertEquals("7 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key max 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key max 5 L1 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key max 5 L1 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key max 5 L1 [7]", 2, params.getColumnPos(6));
        assertEquals("7 key max 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [5] [6] [7]
    // |___ [3] <1> [2] [4]
    @Test
    public void testLayout7KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_L2);
        assertEquals("7 key max 5 L2 columns", 4, params.mNumColumns);
        assertEquals("7 key max 5 L2 rows", 2, params.mNumRows);
        assertEquals("7 key max 5 L2 left", 1, params.mLeftKeys);
        assertEquals("7 key max 5 L2 right", 3, params.mRightKeys);
        assertEquals("7 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key max 5 L2 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key max 5 L2 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key max 5 L2 [7]", 2, params.getColumnPos(6));
        assertEquals("7 key max 5 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key max 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [7] [6] [5]|
    // [4] [3] [2] <1>|
    @Test
    public void testLayout7KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_R0);
        assertEquals("7 key max 5 R0 columns", 4, params.mNumColumns);
        assertEquals("7 key max 5 R0 rows", 2, params.mNumRows);
        assertEquals("7 key max 5 R0 left", 3, params.mLeftKeys);
        assertEquals("7 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("7 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key max 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key max 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key max 5 R0 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key max 5 R0 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key max 5 R0 [7]", -2, params.getColumnPos(6));
        assertEquals("7 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 5 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //     [7] [6] [5] ___|
    // [4] [3] [2] <1> ___|
    @Test
    public void testLayout7KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_R1);
        assertEquals("7 key max 5 R1 columns", 4, params.mNumColumns);
        assertEquals("7 key max 5 R1 rows", 2, params.mNumRows);
        assertEquals("7 key max 5 R1 left", 3, params.mLeftKeys);
        assertEquals("7 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("7 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key max 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key max 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key max 5 R1 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key max 5 R1 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key max 5 R1 [7]", -2, params.getColumnPos(6));
        assertEquals("7 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 5 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //   [7] [5] [6]   ___|
    // [4] [3] <1> [2] ___|
    @Test
    public void testLayout7KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_R2);
        assertEquals("7 key max 5 R2 columns", 4, params.mNumColumns);
        assertEquals("7 key max 5 R2 rows", 2, params.mNumRows);
        assertEquals("7 key max 5 R2 left", 2, params.mLeftKeys);
        assertEquals("7 key max 5 R2 right", 2, params.mRightKeys);
        assertEquals("7 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("7 key max 5 R2 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key max 5 R2 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key max 5 R2 [7]", -1, params.getColumnPos(6));
        assertEquals("7 key max 5 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key max 5 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [7]
    // [6] [4] [5]
    // [3] <1> [2]
    @Test
    public void testLayout7KeyMax3M0() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_M0);
        assertEquals("7 key max 3 M0 columns", 3, params.mNumColumns);
        assertEquals("7 key max 3 M0 rows", 3, params.mNumRows);
        assertEquals("7 key max 3 M0 left", 1, params.mLeftKeys);
        assertEquals("7 key max 3 M0 right", 2, params.mRightKeys);
        assertEquals("7 key max 3 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 3 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 3 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 3 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key max 3 M0 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key max 3 M0 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key max 3 M0 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key max 3 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 3 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[7]
    // |[4] [5] [6]
    // |<1> [2] [3]
    @Test
    public void testLayout7KeyMax3L0() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_L0);
        assertEquals("7 key max 3 L0 columns", 3, params.mNumColumns);
        assertEquals("7 key max 3 L0 rows", 3, params.mNumRows);
        assertEquals("7 key max 3 L0 left", 0, params.mLeftKeys);
        assertEquals("7 key max 3 L0 right", 3, params.mRightKeys);
        assertEquals("7 key max 3 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 3 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 3 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key max 3 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key max 3 L0 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key max 3 L0 [6]", 2, params.getColumnPos(5));
        assertEquals("7 key max 3 L0 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key max 3 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 3 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [7]
    // |___ [4] [5] [6]
    // |___ <1> [2] [3]
    @Test
    public void testLayout7KeyMax3L1() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_L1);
        assertEquals("7 key max 3 L1 columns", 3, params.mNumColumns);
        assertEquals("7 key max 3 L1 rows", 3, params.mNumRows);
        assertEquals("7 key max 3 L1 left", 0, params.mLeftKeys);
        assertEquals("7 key max 3 L1 right", 3, params.mRightKeys);
        assertEquals("7 key max 3 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 3 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 3 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key max 3 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key max 3 L1 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key max 3 L1 [6]", 2, params.getColumnPos(5));
        assertEquals("7 key max 3 L1 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key max 3 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 3 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___     [7]
    // |___ [6] [4] [5]
    // |___ [3] <1> [2]
    @Test
    public void testLayout7KeyMax3L2() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_L2);
        assertEquals("7 key max 3 L2 columns", 3, params.mNumColumns);
        assertEquals("7 key max 3 L2 rows", 3, params.mNumRows);
        assertEquals("7 key max 3 L2 left", 1, params.mLeftKeys);
        assertEquals("7 key max 3 L2 right", 2, params.mRightKeys);
        assertEquals("7 key max 3 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 3 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 3 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 3 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key max 3 L2 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key max 3 L2 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key max 3 L2 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key max 3 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 3 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //         [7]|
    // [6] [5] [4]|
    // [3] [2] <1>|
    @Test
    public void testLayout7KeyMax3R0() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_R0);
        assertEquals("7 key max 3 R0 columns", 3, params.mNumColumns);
        assertEquals("7 key max 3 R0 rows", 3, params.mNumRows);
        assertEquals("7 key max 3 R0 left", 2, params.mLeftKeys);
        assertEquals("7 key max 3 R0 right", 1, params.mRightKeys);
        assertEquals("7 key max 3 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 3 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key max 3 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key max 3 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key max 3 R0 [5]", -1, params.getColumnPos(4));
        assertEquals("7 key max 3 R0 [6]", -2, params.getColumnPos(5));
        assertEquals("7 key max 3 R0 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key max 3 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 3 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //         [7] ___|
    // [6] [5] [4] ___|
    // [3] [2] <1> ___|
    @Test
    public void testLayout7KeyMax3R1() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_R1);
        assertEquals("7 key max 3 R1 columns", 3, params.mNumColumns);
        assertEquals("7 key max 3 R1 rows", 3, params.mNumRows);
        assertEquals("7 key max 3 R1 left", 2, params.mLeftKeys);
        assertEquals("7 key max 3 R1 right", 1, params.mRightKeys);
        assertEquals("7 key max 3 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 3 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key max 3 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key max 3 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key max 3 R1 [5]", -1, params.getColumnPos(4));
        assertEquals("7 key max 3 R1 [6]", -2, params.getColumnPos(5));
        assertEquals("7 key max 3 R1 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key max 3 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 3 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [7]     ___|
    // [6] [4] [5] ___|
    // [3] <1> [2] ___|
    @Test
    public void testLayout7KeyMax3R2() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_R2);
        assertEquals("7 key max 3 R2 columns", 3, params.mNumColumns);
        assertEquals("7 key max 3 R2 rows", 3, params.mNumRows);
        assertEquals("7 key max 3 R2 left", 1, params.mLeftKeys);
        assertEquals("7 key max 3 R2 right", 2, params.mRightKeys);
        assertEquals("7 key max 3 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key max 3 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key max 3 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key max 3 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key max 3 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key max 3 R2 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key max 3 R2 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key max 3 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key max 3 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [7] [5] [6] [8]
    // [3] <1> [2] [4]
    @Test
    public void testLayout8KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_M0);
        assertEquals("8 key max 5 M0 columns", 4, params.mNumColumns);
        assertEquals("8 key max 5 M0 rows", 2, params.mNumRows);
        assertEquals("8 key max 5 M0 left", 1, params.mLeftKeys);
        assertEquals("8 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("8 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("8 key max 5 M0 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key max 5 M0 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key max 5 M0 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key max 5 M0 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key max 5 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5] [6] [7] [8]
    // |<1> [2] [3] [4]
    @Test
    public void testLayout8KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_L0);
        assertEquals("8 key max 5 L0 columns", 4, params.mNumColumns);
        assertEquals("8 key max 5 L0 rows", 2, params.mNumRows);
        assertEquals("8 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("8 key max 5 L0 right", 4, params.mRightKeys);
        assertEquals("8 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key max 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key max 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key max 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key max 5 L0 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key max 5 L0 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key max 5 L0 [7]", 2, params.getColumnPos(6));
        assertEquals("8 key max 5 L0 [8]", 3, params.getColumnPos(7));
        assertEquals("8 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6] [7] [8]
    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout8KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_L1);
        assertEquals("8 key max 5 L1 columns", 4, params.mNumColumns);
        assertEquals("8 key max 5 L1 rows", 2, params.mNumRows);
        assertEquals("8 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("8 key max 5 L1 right", 4, params.mRightKeys);
        assertEquals("8 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key max 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key max 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key max 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key max 5 L1 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key max 5 L1 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key max 5 L1 [7]", 2, params.getColumnPos(6));
        assertEquals("8 key max 5 L1 [8]", 3, params.getColumnPos(7));
        assertEquals("8 key max 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [7] [5] [6] [8]
    // |___ [3] <1> [2] [4]
    @Test
    public void testLayout8KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_L2);
        assertEquals("8 key max 5 L2 columns", 4, params.mNumColumns);
        assertEquals("8 key max 5 L2 rows", 2, params.mNumRows);
        assertEquals("8 key max 5 L2 left", 1, params.mLeftKeys);
        assertEquals("8 key max 5 L2 right", 3, params.mRightKeys);
        assertEquals("8 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key max 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key max 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key max 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("8 key max 5 L2 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key max 5 L2 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key max 5 L2 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key max 5 L2 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key max 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key max 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [8] [7] [6] [5]|
    // [4] [3] [2] <1>|
    @Test
    public void testLayout8KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_R0);
        assertEquals("8 key max 5 R0 columns", 4, params.mNumColumns);
        assertEquals("8 key max 5 R0 rows", 2, params.mNumRows);
        assertEquals("8 key max 5 R0 left", 3, params.mLeftKeys);
        assertEquals("8 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("8 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key max 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("8 key max 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("8 key max 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("8 key max 5 R0 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key max 5 R0 [6]", -1, params.getColumnPos(5));
        assertEquals("8 key max 5 R0 [7]", -2, params.getColumnPos(6));
        assertEquals("8 key max 5 R0 [8]", -3, params.getColumnPos(7));
        assertEquals("8 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key max 5 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [8] [7] [6] [5] ___|
    // [4] [3] [2] <1> ___|
    @Test
    public void testLayout8KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_R1);
        assertEquals("8 key max 5 R1 columns", 4, params.mNumColumns);
        assertEquals("8 key max 5 R1 rows", 2, params.mNumRows);
        assertEquals("8 key max 5 R1 left", 3, params.mLeftKeys);
        assertEquals("8 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("8 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key max 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("8 key max 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("8 key max 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("8 key max 5 R1 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key max 5 R1 [6]", -1, params.getColumnPos(5));
        assertEquals("8 key max 5 R1 [7]", -2, params.getColumnPos(6));
        assertEquals("8 key max 5 R1 [8]", -3, params.getColumnPos(7));
        assertEquals("8 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key max 5 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [8] [7] [5] [6] ___|
    // [4] [3] <1> [2] ___|
    @Test
    public void testLayout8KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_R2);
        assertEquals("8 key max 5 R2 columns", 4, params.mNumColumns);
        assertEquals("8 key max 5 R2 rows", 2, params.mNumRows);
        assertEquals("8 key max 5 R2 left", 2, params.mLeftKeys);
        assertEquals("8 key max 5 R2 right", 2, params.mRightKeys);
        assertEquals("8 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key max 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key max 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key max 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("8 key max 5 R2 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key max 5 R2 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key max 5 R2 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key max 5 R2 [8]", -2, params.getColumnPos(7));
        assertEquals("8 key max 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key max 5 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout9KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_M0);
        assertEquals("9 key max 5 M0 columns", 5, params.mNumColumns);
        assertEquals("9 key max 5 M0 rows", 2, params.mNumRows);
        assertEquals("9 key max 5 M0 left", 2, params.mLeftKeys);
        assertEquals("9 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("9 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("9 key max 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("9 key max 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key max 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key max 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("9 key max 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("9 key max 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key max 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8] [9]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout9KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_L0);
        assertEquals("9 key max 5 L0 columns", 5, params.mNumColumns);
        assertEquals("9 key max 5 L0 rows", 2, params.mNumRows);
        assertEquals("9 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("9 key max 5 L0 right", 5, params.mRightKeys);
        assertEquals("9 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key max 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key max 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("9 key max 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("9 key max 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("9 key max 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key max 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key max 5 L0 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key max 5 L0 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8] [9]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout9KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_L1);
        assertEquals("9 key max 5 L1 columns", 5, params.mNumColumns);
        assertEquals("9 key max 5 L1 rows", 2, params.mNumRows);
        assertEquals("9 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("9 key max 5 L1 right", 5, params.mRightKeys);
        assertEquals("9 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key max 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key max 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("9 key max 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("9 key max 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("9 key max 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key max 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key max 5 L1 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key max 5 L1 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key max 5 L1 adjust",0, params.mTopRowAdjustment);
        assertEquals("9 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [6] [7] [8] [9]
    // |___ [3] <1> [2] [4] [5]
    @Test
    public void testLayout9KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_L2);
        assertEquals("9 key max 5 L2 columns", 5, params.mNumColumns);
        assertEquals("9 key max 5 L2 rows", 2, params.mNumRows);
        assertEquals("9 key max 5 L2 left", 1, params.mLeftKeys);
        assertEquals("9 key max 5 L2 right", 4, params.mRightKeys);
        assertEquals("9 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key max 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key max 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key max 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("9 key max 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("9 key max 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key max 5 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key max 5 L2 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key max 5 L2 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key max 5 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key max 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [9] [8] [7] [6]|
    // [5] [4] [3] [2] <1>|
    @Test
    public void testLayout9KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_R0);
        assertEquals("9 key max 5 R0 columns", 5, params.mNumColumns);
        assertEquals("9 key max 5 R0 rows", 2, params.mNumRows);
        assertEquals("9 key max 5 R0 left", 4, params.mLeftKeys);
        assertEquals("9 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("9 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key max 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("9 key max 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("9 key max 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("9 key max 5 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("9 key max 5 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key max 5 R0 [7]", -1, params.getColumnPos(6));
        assertEquals("9 key max 5 R0 [8]", -2, params.getColumnPos(7));
        assertEquals("9 key max 5 R0 [9]", -3, params.getColumnPos(8));
        assertEquals("9 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key max 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //     [9] [8] [7] [6] ___|
    // [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout9KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_R1);
        assertEquals("9 key max 5 R1 columns", 5, params.mNumColumns);
        assertEquals("9 key max 5 R1 rows", 2, params.mNumRows);
        assertEquals("9 key max 5 R1 left", 4, params.mLeftKeys);
        assertEquals("9 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("9 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key max 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("9 key max 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("9 key max 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("9 key max 5 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("9 key max 5 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key max 5 R1 [7]", -1, params.getColumnPos(6));
        assertEquals("9 key max 5 R1 [8]", -2, params.getColumnPos(7));
        assertEquals("9 key max 5 R1 [9]", -3, params.getColumnPos(8));
        assertEquals("9 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key max 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //   [9] [8] [6] [7]   ___|
    // [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout9KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_R2);
        assertEquals("9 key max 5 R2 columns", 5, params.mNumColumns);
        assertEquals("9 key max 5 R2 rows", 2, params.mNumRows);
        assertEquals("9 key max 5 R2 left", 3, params.mLeftKeys);
        assertEquals("9 key max 5 R2 right", 2, params.mRightKeys);
        assertEquals("9 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key max 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key max 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key max 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("9 key max 5 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("9 key max 5 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key max 5 R2 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key max 5 R2 [8]", -1, params.getColumnPos(7));
        assertEquals("9 key max 5 R2 [9]", -2, params.getColumnPos(8));
        assertEquals("9 key max 5 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key max 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [A] [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout10KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_M0);
        assertEquals("10 key max 5 M0 columns", 5, params.mNumColumns);
        assertEquals("10 key max 5 M0 rows", 2, params.mNumRows);
        assertEquals("10 key max 5 M0 left", 2, params.mLeftKeys);
        assertEquals("10 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("10 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("10 key max 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("10 key max 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key max 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key max 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key max 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("10 key max 5 M0 [A]", -2, params.getColumnPos(9));
        assertEquals("10 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key max 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8] [9] [A]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout10KeyMax5L0() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_L0);
        assertEquals("10 key max 5 L0 columns", 5, params.mNumColumns);
        assertEquals("10 key max 5 L0 rows", 2, params.mNumRows);
        assertEquals("10 key max 5 L0 left", 0, params.mLeftKeys);
        assertEquals("10 key max 5 L0 right", 5, params.mRightKeys);
        assertEquals("10 key max 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key max 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key max 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("10 key max 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("10 key max 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("10 key max 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key max 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key max 5 L0 [8]", 2, params.getColumnPos(7));
        assertEquals("10 key max 5 L0 [9]", 3, params.getColumnPos(8));
        assertEquals("10 key max 5 L0 [A]", 4, params.getColumnPos(9));
        assertEquals("10 key max 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key max 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8] [9] [A]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout10KeyMax5L1() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_L1);
        assertEquals("10 key max 5 L1 columns", 5, params.mNumColumns);
        assertEquals("10 key max 5 L1 rows", 2, params.mNumRows);
        assertEquals("10 key max 5 L1 left", 0, params.mLeftKeys);
        assertEquals("10 key max 5 L1 right", 5, params.mRightKeys);
        assertEquals("10 key max 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key max 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key max 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("10 key max 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("10 key max 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("10 key max 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key max 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key max 5 L1 [8]", 2, params.getColumnPos(7));
        assertEquals("10 key max 5 L1 [9]", 3, params.getColumnPos(8));
        assertEquals("10 key max 5 L1 [A]", 4, params.getColumnPos(9));
        assertEquals("10 key max 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key max 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [8] [6] [7] [9] [A]
    // |___ [3] <1> [2] [4] [5]
    @Test
    public void testLayout10KeyMax5L2() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_L2);
        assertEquals("10 key max 5 L2 columns", 5, params.mNumColumns);
        assertEquals("10 key max 5 L2 rows", 2, params.mNumRows);
        assertEquals("10 key max 5 L2 left", 1, params.mLeftKeys);
        assertEquals("10 key max 5 L2 right", 4, params.mRightKeys);
        assertEquals("10 key max 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key max 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key max 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key max 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("10 key max 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("10 key max 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key max 5 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key max 5 L2 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key max 5 L2 [9]", 2, params.getColumnPos(8));
        assertEquals("10 key max 5 L2 [A]", 3, params.getColumnPos(9));
        assertEquals("10 key max 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key max 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [A] [9] [8] [7] [6]|
    // [5] [4] [3] [2] <1>|
    @Test
    public void testLayout10KeyMax5R0() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_R0);
        assertEquals("10 key max 5 R0 columns", 5, params.mNumColumns);
        assertEquals("10 key max 5 R0 rows", 2, params.mNumRows);
        assertEquals("10 key max 5 R0 left", 4, params.mLeftKeys);
        assertEquals("10 key max 5 R0 right", 1, params.mRightKeys);
        assertEquals("10 key max 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key max 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("10 key max 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("10 key max 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("10 key max 5 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("10 key max 5 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key max 5 R0 [7]", -1, params.getColumnPos(6));
        assertEquals("10 key max 5 R0 [8]", -2, params.getColumnPos(7));
        assertEquals("10 key max 5 R0 [9]", -3, params.getColumnPos(8));
        assertEquals("10 key max 5 R0 [A]", -4, params.getColumnPos(9));
        assertEquals("10 key max 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key max 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [A] [9] [8] [7] [6] ___|
    // [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout10KeyMax5R1() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_R1);
        assertEquals("10 key max 5 R1 columns", 5, params.mNumColumns);
        assertEquals("10 key max 5 R1 rows", 2, params.mNumRows);
        assertEquals("10 key max 5 R1 left", 4, params.mLeftKeys);
        assertEquals("10 key max 5 R1 right", 1, params.mRightKeys);
        assertEquals("10 key max 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key max 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("10 key max 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("10 key max 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("10 key max 5 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("10 key max 5 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key max 5 R1 [7]", -1, params.getColumnPos(6));
        assertEquals("10 key max 5 R1 [8]", -2, params.getColumnPos(7));
        assertEquals("10 key max 5 R1 [9]", -3, params.getColumnPos(8));
        assertEquals("10 key max 5 R1 [A]", -4, params.getColumnPos(9));
        assertEquals("10 key max 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key max 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [A] [9] [8] [6] [7] ___|
    // [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout10KeyMax5R2() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_R2);
        assertEquals("10 key max 5 R2 columns", 5, params.mNumColumns);
        assertEquals("10 key max 5 R2 rows", 2, params.mNumRows);
        assertEquals("10 key max 5 R2 left", 3, params.mLeftKeys);
        assertEquals("10 key max 5 R2 right", 2, params.mRightKeys);
        assertEquals("10 key max 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key max 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key max 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key max 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("10 key max 5 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("10 key max 5 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key max 5 R2 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key max 5 R2 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key max 5 R2 [9]", -2, params.getColumnPos(8));
        assertEquals("10 key max 5 R2 [A]", -3, params.getColumnPos(9));
        assertEquals("10 key max 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key max 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //   [9] [A] [B]
    // [7] [5] [6] [8]
    // [3] <1> [2] [4]
    @Test
    public void testLayout11KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(11, 5, XPOS_M0);
        assertEquals("11 key max 5 M0 columns", 4, params.mNumColumns);
        assertEquals("11 key max 5 M0 rows", 3, params.mNumRows);
        assertEquals("11 key max 5 M0 left", 1, params.mLeftKeys);
        assertEquals("11 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("11 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("11 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("11 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("11 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("11 key max 5 M0 [5]", 0, params.getColumnPos(4));
        assertEquals("11 key max 5 M0 [6]", 1, params.getColumnPos(5));
        assertEquals("11 key max 5 M0 [7]", -1, params.getColumnPos(6));
        assertEquals("11 key max 5 M0 [8]", 2, params.getColumnPos(7));
        assertEquals("11 key max 5 M0 [9]", 0, params.getColumnPos(8));
        assertEquals("11 key max 5 M0 [A]", 1, params.getColumnPos(9));
        assertEquals("11 key max 5 M0 [B]", 2, params.getColumnPos(10));
        assertEquals("11 key max 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("11 key max 5 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [B] [9] [A] [C]
    // [7] [5] [6] [8]
    // [3] <1> [2] [4]
    @Test
    public void testLayout12KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(12, 5, XPOS_M0);
        assertEquals("12 key max 5 M0 columns", 4, params.mNumColumns);
        assertEquals("12 key max 5 M0 rows", 3, params.mNumRows);
        assertEquals("12 key max 5 M0 left", 1, params.mLeftKeys);
        assertEquals("12 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("12 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("12 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("12 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("12 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("12 key max 5 M0 [5]", 0, params.getColumnPos(4));
        assertEquals("12 key max 5 M0 [6]", 1, params.getColumnPos(5));
        assertEquals("12 key max 5 M0 [7]", -1, params.getColumnPos(6));
        assertEquals("12 key max 5 M0 [8]", 2, params.getColumnPos(7));
        assertEquals("12 key max 5 M0 [9]", 0, params.getColumnPos(8));
        assertEquals("12 key max 5 M0 [A]", 1, params.getColumnPos(9));
        assertEquals("12 key max 5 M0 [B]", -1, params.getColumnPos(10));
        assertEquals("12 key max 5 M0 [C]", 2, params.getColumnPos(11));
        assertEquals("12 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("12 key max 5 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [D] [B] [C]
    // [A] [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout13KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(13, 5, XPOS_M0);
        assertEquals("13 key max 5 M0 columns", 5, params.mNumColumns);
        assertEquals("13 key max 5 M0 rows", 3, params.mNumRows);
        assertEquals("13 key max 5 M0 left", 2, params.mLeftKeys);
        assertEquals("13 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("13 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("13 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("13 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("13 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("13 key max 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("13 key max 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("13 key max 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("13 key max 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("13 key max 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("13 key max 5 M0 [A]", -2, params.getColumnPos(9));
        assertEquals("13 key max 5 M0 [B]", 0, params.getColumnPos(10));
        assertEquals("13 key max 5 M0 [C]", 1, params.getColumnPos(11));
        assertEquals("13 key max 5 M0 [D]", -1, params.getColumnPos(12));
        assertEquals("13 key max 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("13 key max 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [D] [B] [C] [E]
    // [A] [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout14KeyMax5M0() {
        MoreKeysKeyboardParams params = createParams(14, 5, XPOS_M0);
        assertEquals("13 key max 5 M0 columns", 5, params.mNumColumns);
        assertEquals("13 key max 5 M0 rows", 3, params.mNumRows);
        assertEquals("13 key max 5 M0 left", 2, params.mLeftKeys);
        assertEquals("13 key max 5 M0 right", 3, params.mRightKeys);
        assertEquals("13 key max 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("13 key max 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("13 key max 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("13 key max 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("13 key max 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("13 key max 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("13 key max 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("13 key max 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("13 key max 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("13 key max 5 M0 [A]", -2, params.getColumnPos(9));
        assertEquals("13 key max 5 M0 [B]", 0, params.getColumnPos(10));
        assertEquals("13 key max 5 M0 [C]", 1, params.getColumnPos(11));
        assertEquals("13 key max 5 M0 [D]", -1, params.getColumnPos(12));
        assertEquals("13 key max 5 M0 [E]", 2, params.getColumnPos(13));
        assertEquals("13 key max 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("13 key max 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }
}
