/*
 * Copyright (C) 2012 The Android Open Source Project
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
public class MoreKeysKeyboardBuilderFixedOrderTests {
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

    private static MoreKeysKeyboardParams createParams(final int numKeys, final int columnNum,
            final int coordXInParent) {
        final MoreKeysKeyboardParams params = new MoreKeysKeyboardParams();
        params.setParameters(numKeys, columnNum, WIDTH, HEIGHT, coordXInParent, KEYBOARD_WIDTH,
                true /* isMoreKeysFixedColumn */, true /* isMoreKeysFixedOrder */,
                0 /* dividerWidth */);
        return params;
    }

    @Test
    public void testLayoutError() {
        MoreKeysKeyboardParams params = null;
        try {
            final int fixColumns = KEYBOARD_WIDTH / WIDTH;
            params = createParams(fixColumns + 1, fixColumns + 1, HEIGHT);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Too small keyboard to hold more keys keyboard.
        }
        assertNull("Too small keyboard to hold more keys keyboard", params);
    }

    // More keys keyboard layout test.
    // "[n]" represents n-th key position in more keys keyboard.
    // "<m>" is the default key.

    // <1>
    @Test
    public void testLayout1KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_M0);
        assertEquals("1 key fix 5 M0 columns", 1, params.mNumColumns);
        assertEquals("1 key fix 5 M0 rows", 1, params.mNumRows);
        assertEquals("1 key fix 5 M0 left", 0, params.mLeftKeys);
        assertEquals("1 key fix 5 M0 right", 1, params.mRightKeys);
        assertEquals("1 key fix 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key fix 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key fix 5 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |<1>
    @Test
    public void testLayout1KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_L0);
        assertEquals("1 key fix 5 L0 columns", 1, params.mNumColumns);
        assertEquals("1 key fix 5 L0 rows", 1, params.mNumRows);
        assertEquals("1 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("1 key fix 5 L0 right", 1, params.mRightKeys);
        assertEquals("1 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1>
    @Test
    public void testLayout1KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_L1);
        assertEquals("1 key fix 5 L1 columns", 1, params.mNumColumns);
        assertEquals("1 key fix 5 L1 rows", 1, params.mNumRows);
        assertEquals("1 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("1 key fix 5 L1 right", 1, params.mRightKeys);
        assertEquals("1 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key fix 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ <1>
    @Test
    public void testLayout1KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_L2);
        assertEquals("1 key fix 5 L2 columns", 1, params.mNumColumns);
        assertEquals("1 key fix 5 L2 rows", 1, params.mNumRows);
        assertEquals("1 key fix 5 L2 left", 0, params.mLeftKeys);
        assertEquals("1 key fix 5 L2 right", 1, params.mRightKeys);
        assertEquals("1 key fix 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key fix 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key fix 5 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1>|
    @Test
    public void testLayout1KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_R0);
        assertEquals("1 key fix 5 R0 columns", 1, params.mNumColumns);
        assertEquals("1 key fix 5 R0 rows", 1, params.mNumRows);
        assertEquals("1 key fix 5 R0 left", 0, params.mLeftKeys);
        assertEquals("1 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("1 key fix 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key fix 5 R0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1> ___|
    @Test
    public void testLayout1KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_R1);
        assertEquals("1 key fix 5 R1 columns", 1, params.mNumColumns);
        assertEquals("1 key fix 5 R1 rows", 1, params.mNumRows);
        assertEquals("1 key fix 5 R1 left", 0, params.mLeftKeys);
        assertEquals("1 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("1 key fix 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key fix 5 R1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1> ___ ___|
    @Test
    public void testLayout1KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_R2);
        assertEquals("1 key fix 5 R2 columns", 1, params.mNumColumns);
        assertEquals("1 key fix 5 R2 rows", 1, params.mNumRows);
        assertEquals("1 key fix 5 R2 left", 0, params.mLeftKeys);
        assertEquals("1 key fix 5 R2 right", 1, params.mRightKeys);
        assertEquals("1 key fix 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key fix 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key fix 5 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1> [2]
    @Test
    public void testLayout2KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_M0);
        assertEquals("2 key fix 5 M0 columns", 2, params.mNumColumns);
        assertEquals("2 key fix 5 M0 rows", 1, params.mNumRows);
        assertEquals("2 key fix 5 M0 left", 0, params.mLeftKeys);
        assertEquals("2 key fix 5 M0 right", 2, params.mRightKeys);
        assertEquals("2 key fix 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key fix 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key fix 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key fix 5 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |<1> [2]
    @Test
    public void testLayout2KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_L0);
        assertEquals("2 key fix 5 L0 columns", 2, params.mNumColumns);
        assertEquals("2 key fix 5 L0 rows", 1, params.mNumRows);
        assertEquals("2 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("2 key fix 5 L0 right", 2, params.mRightKeys);
        assertEquals("2 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key fix 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2]
    @Test
    public void testLayout2KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_L1);
        assertEquals("2 key fix 5 L1 columns", 2, params.mNumColumns);
        assertEquals("2 key fix 5 L1 rows", 1, params.mNumRows);
        assertEquals("2 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("2 key fix 5 L1 right", 2, params.mRightKeys);
        assertEquals("2 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key fix 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key fix 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ <1> [2]
    @Test
    public void testLayout2KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_L2);
        assertEquals("2 key fix 5 L2 columns", 2, params.mNumColumns);
        assertEquals("2 key fix 5 L2 rows", 1, params.mNumRows);
        assertEquals("2 key fix 5 L2 left", 0, params.mLeftKeys);
        assertEquals("2 key fix 5 L2 right", 2, params.mRightKeys);
        assertEquals("2 key fix 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key fix 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key fix 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key fix 5 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [1] <2>|
    @Test
    public void testLayout2KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_R0);
        assertEquals("2 key fix 5 R0 columns", 2, params.mNumColumns);
        assertEquals("2 key fix 5 R0 rows", 1, params.mNumRows);
        assertEquals("2 key fix 5 R0 left", 1, params.mLeftKeys);
        assertEquals("2 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("2 key fix 5 R0 [1]", -1, params.getColumnPos(0));
        assertEquals("2 key fix 5 R0 <2>", 0, params.getColumnPos(1));
        assertEquals("2 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key fix 5 R0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [1] <2> ___|
    @Test
    public void testLayout2KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_R1);
        assertEquals("2 key fix 5 R1 columns", 2, params.mNumColumns);
        assertEquals("2 key fix 5 R1 rows", 1, params.mNumRows);
        assertEquals("2 key fix 5 R1 left", 1, params.mLeftKeys);
        assertEquals("2 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("2 key fix 5 R1 [1]", -1, params.getColumnPos(0));
        assertEquals("2 key fix 5 R1 <2>", 0, params.getColumnPos(1));
        assertEquals("2 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key fix 5 R1 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // <1> [2] ___|
    @Test
    public void testLayout2KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_R2);
        assertEquals("2 key fix 5 R2 columns", 2, params.mNumColumns);
        assertEquals("2 key fix 5 R2 rows", 1, params.mNumRows);
        assertEquals("2 key fix 5 R2 left", 0, params.mLeftKeys);
        assertEquals("2 key fix 5 R2 right", 2, params.mRightKeys);
        assertEquals("2 key fix 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key fix 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key fix 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key fix 5 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [3]
    // <1> [2]
    @Test
    public void testLayout3KeyFix2M0() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_M0);
        assertEquals("3 key fix 2 M0 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 M0 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 M0 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 M0 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 M0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |[3]
    // |<1> [2]
    @Test
    public void testLayout3KeyFix2L0() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_L0);
        assertEquals("3 key fix 2 L0 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 L0 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 L0 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 L0 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 L0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3]
    // |___ <1> [2]
    @Test
    public void testLayout3KeyFix2L1() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_L1);
        assertEquals("3 key fix 2 L1 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 L1 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 L1 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 L1 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 L1 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |        [3]
    // |___ ___ <1> [2]
    @Test
    public void testLayout3KeyFix2L2() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_L2);
        assertEquals("3 key fix 2 L2 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 L2 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 L2 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 L2 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 L2 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    //     [3]|
    // [1] <2>|
    @Test
    public void testLayout3KeyFix2R0() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_R0);
        assertEquals("3 key fix 2 R0 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 R0 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 R0 left", 1, params.mLeftKeys);
        assertEquals("3 key fix 2 R0 right", 1, params.mRightKeys);
        assertEquals("3 key fix 2 R0 [1]", -1, params.getColumnPos(0));
        assertEquals("3 key fix 2 R0 <2>", 0, params.getColumnPos(1));
        assertEquals("3 key fix 2 R0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 R0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [3] ___|
    // [1] <2> ___|
    @Test
    public void testLayout3KeyFix2R1() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_R1);
        assertEquals("3 key fix 2 R1 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 R1 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 R1 left", 1, params.mLeftKeys);
        assertEquals("3 key fix 2 R1 right", 1, params.mRightKeys);
        assertEquals("3 key fix 2 R1 [1]", -1, params.getColumnPos(0));
        assertEquals("3 key fix 2 R1 <2>", 0, params.getColumnPos(1));
        assertEquals("3 key fix 2 R1 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 R1 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3]     ___|
    // <1> [2] ___|
    @Test
    public void testLayout3KeyFix2R2() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_R2);
        assertEquals("3 key fix 2 R2 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 R2 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 R2 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 R2 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 R2 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [3] [4]
    // <1> [2]
    @Test
    public void testLayout4KeyFix2M0() {
        MoreKeysKeyboardParams params = createParams(4, 2, XPOS_M0);
        assertEquals("3 key fix 2 M0 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 M0 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 M0 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 M0 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 M0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 M0 [4]", 1, params.getColumnPos(3));
        assertEquals("3 key fix 2 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |[3] [4]
    // |<1> [2]
    @Test
    public void testLayout4KeyFix2L0() {
        MoreKeysKeyboardParams params = createParams(4, 2, XPOS_L0);
        assertEquals("3 key fix 2 L0 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 L0 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 L0 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 L0 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 L0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 L0 [4]", 1, params.getColumnPos(3));
        assertEquals("3 key fix 2 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] [4]
    // |___ <1> [2]
    @Test
    public void testLayout4KeyFix2L1() {
        MoreKeysKeyboardParams params = createParams(4, 2, XPOS_L1);
        assertEquals("3 key fix 2 L1 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 L1 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 L1 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 L1 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 L1 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 L1 [4]", 1, params.getColumnPos(3));
        assertEquals("3 key fix 2 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |        [3] [4]
    // |___ ___ <1> [2]
    @Test
    public void testLayout4KeyFix2L2() {
        MoreKeysKeyboardParams params = createParams(4, 2, XPOS_L2);
        assertEquals("3 key fix 2 L2 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 L2 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 L2 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 L2 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 L2 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 L2 [4]", 1, params.getColumnPos(3));
        assertEquals("3 key fix 2 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [3] [4]|
    // [1] <2>|
    @Test
    public void testLayout4KeyFix2R0() {
        MoreKeysKeyboardParams params = createParams(4, 2, XPOS_R0);
        assertEquals("3 key fix 2 R0 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 R0 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 R0 left", 1, params.mLeftKeys);
        assertEquals("3 key fix 2 R0 right", 1, params.mRightKeys);
        assertEquals("3 key fix 2 R0 [1]", -1, params.getColumnPos(0));
        assertEquals("3 key fix 2 R0 <2>", 0, params.getColumnPos(1));
        assertEquals("3 key fix 2 R0 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key fix 2 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("3 key fix 2 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 R0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3] [4] ___|
    // [1] <2> ___|
    @Test
    public void testLayout4KeyFix2R1() {
        MoreKeysKeyboardParams params = createParams(4, 2, XPOS_R1);
        assertEquals("3 key fix 2 R1 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 R1 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 R1 left", 1, params.mLeftKeys);
        assertEquals("3 key fix 2 R1 right", 1, params.mRightKeys);
        assertEquals("3 key fix 2 R1 [1]", -1, params.getColumnPos(0));
        assertEquals("3 key fix 2 R1 <2>", 0, params.getColumnPos(1));
        assertEquals("3 key fix 2 R1 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key fix 2 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("3 key fix 2 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 R1 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3] [4] ___|
    // <1> [2] ___|
    @Test
    public void testLayout4KeyFix2R2() {
        MoreKeysKeyboardParams params = createParams(4, 2, XPOS_R2);
        assertEquals("3 key fix 2 R2 columns", 2, params.mNumColumns);
        assertEquals("3 key fix 2 R2 rows", 2, params.mNumRows);
        assertEquals("3 key fix 2 R2 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 2 R2 right", 2, params.mRightKeys);
        assertEquals("3 key fix 2 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 2 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 2 R2 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key fix 2 R2 [4]", 1, params.getColumnPos(3));
        assertEquals("3 key fix 2 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 2 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [1] <2> [3]
    @Test
    public void testLayout3KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_M0);
        assertEquals("3 key fix 5 columns", 3, params.mNumColumns);
        assertEquals("3 key fix 5 rows", 1, params.mNumRows);
        assertEquals("3 key fix 5 left", 1, params.mLeftKeys);
        assertEquals("3 key fix 5 right", 2, params.mRightKeys);
        assertEquals("3 key fix 5 [1]", -1, params.getColumnPos(0));
        assertEquals("3 key fix 5 <2>", 0, params.getColumnPos(1));
        assertEquals("3 key fix 5 [3]", 1, params.getColumnPos(2));
        assertEquals("3 key fix 5 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 5 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3]
    @Test
    public void testLayout3KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_L0);
        assertEquals("3 key fix 5 L0 columns", 3, params.mNumColumns);
        assertEquals("3 key fix 5 L0 rows", 1, params.mNumRows);
        assertEquals("3 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 5 L0 right", 3, params.mRightKeys);
        assertEquals("3 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("3 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3]
    @Test
    public void testLayout3KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_L1);
        assertEquals("3 key fix 5 L1 columns", 3, params.mNumColumns);
        assertEquals("3 key fix 5 L1 rows", 1, params.mNumRows);
        assertEquals("3 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("3 key fix 5 L1 right", 3, params.mRightKeys);
        assertEquals("3 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key fix 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key fix 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("3 key fix 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [1] <2> [3]
    @Test
    public void testLayout3KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_L2);
        assertEquals("3 key fix 5 L2 columns", 3, params.mNumColumns);
        assertEquals("3 key fix 5 L2 rows", 1, params.mNumRows);
        assertEquals("3 key fix 5 L2 left", 1, params.mLeftKeys);
        assertEquals("3 key fix 5 L2 right", 2, params.mRightKeys);
        assertEquals("3 key fix 5 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("3 key fix 5 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("3 key fix 5 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("3 key fix 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [1] [2] <3>|
    @Test
    public void testLayout3KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_R0);
        assertEquals("3 key fix 5 R0 columns", 3, params.mNumColumns);
        assertEquals("3 key fix 5 R0 rows", 1, params.mNumRows);
        assertEquals("3 key fix 5 R0 left", 2, params.mLeftKeys);
        assertEquals("3 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("3 key fix 5 R0 [1]", -2, params.getColumnPos(0));
        assertEquals("3 key fix 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key fix 5 R0 <3>", 0, params.getColumnPos(2));
        assertEquals("3 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 5 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [1] [2] <3> ___|
    @Test
    public void testLayout3KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_R1);
        assertEquals("3 key fix 5 R1 columns", 3, params.mNumColumns);
        assertEquals("3 key fix 5 R1 rows", 1, params.mNumRows);
        assertEquals("3 key fix 5 R1 left", 2, params.mLeftKeys);
        assertEquals("3 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("3 key fix 5 R1 [1]", -2, params.getColumnPos(0));
        assertEquals("3 key fix 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key fix 5 R1 <3>", 0, params.getColumnPos(2));
        assertEquals("3 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 5 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [1] <2> [3] ___|
    @Test
    public void testLayout3KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_R2);
        assertEquals("3 key fix 5 R2 columns", 3, params.mNumColumns);
        assertEquals("3 key fix 5 R2 rows", 1, params.mNumRows);
        assertEquals("3 key fix 5 R2 left", 1, params.mLeftKeys);
        assertEquals("3 key fix 5 R2 right", 2, params.mRightKeys);
        assertEquals("3 key fix 5 R2 [1]", -1, params.getColumnPos(0));
        assertEquals("3 key fix 5 R2 <2>", 0, params.getColumnPos(1));
        assertEquals("3 key fix 5 R2 [3]", 1, params.getColumnPos(2));
        assertEquals("3 key fix 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key fix 5 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [4]
    // [1] <2> [3]
    @Test
    public void testLayout4KeyFix3M0() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_M0);
        assertEquals("4 key fix 3 M0 columns", 3, params.mNumColumns);
        assertEquals("4 key fix 3 M0 rows", 2, params.mNumRows);
        assertEquals("4 key fix 3 M0 left", 1, params.mLeftKeys);
        assertEquals("4 key fix 3 M0 right", 2, params.mRightKeys);
        assertEquals("4 key fix 3 M0 [1]", -1, params.getColumnPos(0));
        assertEquals("4 key fix 3 M0 <2>", 0, params.getColumnPos(1));
        assertEquals("4 key fix 3 M0 [3]", 1, params.getColumnPos(2));
        assertEquals("4 key fix 3 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key fix 3 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 3 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4]
    // |<1> [2] [3]
    @Test
    public void testLayout4KeyFix3L0() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_L0);
        assertEquals("4 key fix 3 L0 columns", 3, params.mNumColumns);
        assertEquals("4 key fix 3 L0 rows", 2, params.mNumRows);
        assertEquals("4 key fix 3 L0 left", 0, params.mLeftKeys);
        assertEquals("4 key fix 3 L0 right", 3, params.mRightKeys);
        assertEquals("4 key fix 3 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key fix 3 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key fix 3 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key fix 3 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key fix 3 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 3 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4]
    // |___ <1> [2] [3]
    @Test
    public void testLayout4KeyFix3L1() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_L1);
        assertEquals("4 key fix 3 L1 columns", 3, params.mNumColumns);
        assertEquals("4 key fix 3 L1 rows", 2, params.mNumRows);
        assertEquals("4 key fix 3 L1 left", 0, params.mLeftKeys);
        assertEquals("4 key fix 3 L1 right", 3, params.mRightKeys);
        assertEquals("4 key fix 3 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key fix 3 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key fix 3 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key fix 3 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key fix 3 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 3 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___     [4]
    // |___ ___ [1] <2> [3]
    @Test
    public void testLayout4KeyFix3L2() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_L2);
        assertEquals("4 key fix 3 L2 columns", 3, params.mNumColumns);
        assertEquals("4 key fix 3 L2 rows", 2, params.mNumRows);
        assertEquals("4 key fix 3 L2 left", 1, params.mLeftKeys);
        assertEquals("4 key fix 3 L2 right", 2, params.mRightKeys);
        assertEquals("4 key fix 3 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("4 key fix 3 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("4 key fix 3 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("4 key fix 3 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key fix 3 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 3 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //         [4]|
    // [1] [2] <3>|
    @Test
    public void testLayout4KeyFix3R0() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_R0);
        assertEquals("4 key fix 3 R0 columns", 3, params.mNumColumns);
        assertEquals("4 key fix 3 R0 rows", 2, params.mNumRows);
        assertEquals("4 key fix 3 R0 left", 2, params.mLeftKeys);
        assertEquals("4 key fix 3 R0 right", 1, params.mRightKeys);
        assertEquals("4 key fix 3 R0 [1]", -2, params.getColumnPos(0));
        assertEquals("4 key fix 3 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key fix 3 R0 <3>", 0, params.getColumnPos(2));
        assertEquals("4 key fix 3 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key fix 3 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 3 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //         [4] ___|
    // [1] [2] <3> ___|
    @Test
    public void testLayout4KeyFix3R1() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_R1);
        assertEquals("4 key fix 3 R1 columns", 3, params.mNumColumns);
        assertEquals("4 key fix 3 R1 rows", 2, params.mNumRows);
        assertEquals("4 key fix 3 R1 left", 2, params.mLeftKeys);
        assertEquals("4 key fix 3 R1 right", 1, params.mRightKeys);
        assertEquals("4 key fix 3 R1 [1]", -2, params.getColumnPos(0));
        assertEquals("4 key fix 3 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key fix 3 R1 <3>", 0, params.getColumnPos(2));
        assertEquals("4 key fix 3 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key fix 3 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 3 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [4]     ___|
    // [1] <2> [3] ___|
    @Test
    public void testLayout4KeyFix3R2() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_R2);
        assertEquals("4 key fix 3 R2 columns", 3, params.mNumColumns);
        assertEquals("4 key fix 3 R2 rows", 2, params.mNumRows);
        assertEquals("4 key fix 3 R2 left", 1, params.mLeftKeys);
        assertEquals("4 key fix 3 R2 right", 2, params.mRightKeys);
        assertEquals("4 key fix 3 R2 [1]", -1, params.getColumnPos(0));
        assertEquals("4 key fix 3 R2 <2>", 0, params.getColumnPos(1));
        assertEquals("4 key fix 3 R2 [3]", 1, params.getColumnPos(2));
        assertEquals("4 key fix 3 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key fix 3 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 3 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //   [4] [5]
    // [1] <2> [3]
    @Test
    public void testLayout5KeyFix3M0() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_M0);
        assertEquals("5 key fix 3 M0 columns", 3, params.mNumColumns);
        assertEquals("5 key fix 3 M0 rows", 2, params.mNumRows);
        assertEquals("5 key fix 3 M0 left", 1, params.mLeftKeys);
        assertEquals("5 key fix 3 M0 right", 2, params.mRightKeys);
        assertEquals("5 key fix 3 M0 [1]", -1, params.getColumnPos(0));
        assertEquals("5 key fix 3 M0 <2>", 0, params.getColumnPos(1));
        assertEquals("5 key fix 3 M0 [3]", 1, params.getColumnPos(2));
        assertEquals("5 key fix 3 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key fix 3 M0 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key fix 3 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key fix 3 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4] [5]
    // |<1> [2] [3]
    @Test
    public void testLayout5KeyFix3L0() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_L0);
        assertEquals("5 key fix 3 L0 columns", 3, params.mNumColumns);
        assertEquals("5 key fix 3 L0 rows", 2, params.mNumRows);
        assertEquals("5 key fix 3 L0 left", 0, params.mLeftKeys);
        assertEquals("5 key fix 3 L0 right", 3, params.mRightKeys);
        assertEquals("5 key fix 3 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key fix 3 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key fix 3 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key fix 3 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key fix 3 L0 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key fix 3 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 3 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4] [5]
    // |___ <1> [2] [3]
    @Test
    public void testLayout5KeyFix3L1() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_L1);
        assertEquals("5 key fix 3 L1 columns", 3, params.mNumColumns);
        assertEquals("5 key fix 3 L1 rows", 2, params.mNumRows);
        assertEquals("5 key fix 3 L1 left", 0, params.mLeftKeys);
        assertEquals("5 key fix 3 L1 right", 3, params.mRightKeys);
        assertEquals("5 key fix 3 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key fix 3 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key fix 3 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key fix 3 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key fix 3 L1 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key fix 3 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 3 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [4] [5]
    // |___ [1] <2> [3]
    @Test
    public void testLayout5KeyFix3L2() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_L2);
        assertEquals("5 key fix 3 L2 columns", 3, params.mNumColumns);
        assertEquals("5 key fix 3 L2 rows", 2, params.mNumRows);
        assertEquals("5 key fix 3 L2 left", 1, params.mLeftKeys);
        assertEquals("5 key fix 3 L2 right", 2, params.mRightKeys);
        assertEquals("5 key fix 3 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("5 key fix 3 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("5 key fix 3 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("5 key fix 3 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key fix 3 L2 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key fix 3 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key fix 3 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [4] [5]|
    // [1] [2] <3>|
    @Test
    public void testLayout5KeyFix3R0() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_R0);
        assertEquals("5 key fix 3 R0 columns", 3, params.mNumColumns);
        assertEquals("5 key fix 3 R0 rows", 2, params.mNumRows);
        assertEquals("5 key fix 3 R0 left", 2, params.mLeftKeys);
        assertEquals("5 key fix 3 R0 right", 1, params.mRightKeys);
        assertEquals("5 key fix 3 R0 [1]", -2, params.getColumnPos(0));
        assertEquals("5 key fix 3 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key fix 3 R0 <3>", 0, params.getColumnPos(2));
        assertEquals("5 key fix 3 R0 [4]", -1, params.getColumnPos(3));
        assertEquals("5 key fix 3 R0 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key fix 3 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 3 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [4] [5] ___|
    // [1] [2] <3> ___|
    @Test
    public void testLayout5KeyFix3R1() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_R1);
        assertEquals("5 key fix 3 R1 columns", 3, params.mNumColumns);
        assertEquals("5 key fix 3 R1 rows", 2, params.mNumRows);
        assertEquals("5 key fix 3 R1 left", 2, params.mLeftKeys);
        assertEquals("5 key fix 3 R1 right", 1, params.mRightKeys);
        assertEquals("5 key fix 3 R1 [1]", -2, params.getColumnPos(0));
        assertEquals("5 key fix 3 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key fix 3 R1 <3>", 0, params.getColumnPos(2));
        assertEquals("5 key fix 3 R1 [4]", -1, params.getColumnPos(3));
        assertEquals("5 key fix 3 R1 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key fix 3 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 3 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [4] [5]   ___|
    // [1] <2> [3] ___|
    @Test
    public void testLayout5KeyFix3R2() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_R2);
        assertEquals("5 key fix 3 R2 columns", 3, params.mNumColumns);
        assertEquals("5 key fix 3 R2 rows", 2, params.mNumRows);
        assertEquals("5 key fix 3 R2 left", 1, params.mLeftKeys);
        assertEquals("5 key fix 3 R2 right", 2, params.mRightKeys);
        assertEquals("5 key fix 3 R2 [1]", -1, params.getColumnPos(0));
        assertEquals("5 key fix 3 R2 <2>", 0, params.getColumnPos(1));
        assertEquals("5 key fix 3 R2 [3]", 1, params.getColumnPos(2));
        assertEquals("5 key fix 3 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key fix 3 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key fix 3 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key fix 3 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [4] [5] [6]
    // [1] <2> [3]
    @Test
    public void testLayout6KeyFix3M0() {
        MoreKeysKeyboardParams params = createParams(6, 3, XPOS_M0);
        assertEquals("6 key fix 3 M0 columns", 3, params.mNumColumns);
        assertEquals("6 key fix 3 M0 rows", 2, params.mNumRows);
        assertEquals("6 key fix 3 M0 left", 1, params.mLeftKeys);
        assertEquals("6 key fix 3 M0 right", 2, params.mRightKeys);
        assertEquals("6 key fix 3 M0 [1]", -1, params.getColumnPos(0));
        assertEquals("6 key fix 3 M0 <2>", 0, params.getColumnPos(1));
        assertEquals("6 key fix 3 M0 [3]", 1, params.getColumnPos(2));
        assertEquals("6 key fix 3 M0 [4]", -1, params.getColumnPos(3));
        assertEquals("6 key fix 3 M0 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key fix 3 M0 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key fix 3 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 3 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4] [5] [6]
    // |<1> [2] [3]
    @Test
    public void testLayout6KeyFix3L0() {
        MoreKeysKeyboardParams params = createParams(6, 3, XPOS_L0);
        assertEquals("6 key fix 3 L0 columns", 3, params.mNumColumns);
        assertEquals("6 key fix 3 L0 rows", 2, params.mNumRows);
        assertEquals("6 key fix 3 L0 left", 0, params.mLeftKeys);
        assertEquals("6 key fix 3 L0 right", 3, params.mRightKeys);
        assertEquals("6 key fix 3 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key fix 3 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key fix 3 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key fix 3 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key fix 3 L0 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key fix 3 L0 [6]", 2, params.getColumnPos(5));
        assertEquals("6 key fix 3 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 3 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4] [5] [6]
    // |___ <1> [2] [3]
    @Test
    public void testLayout6KeyFix3L1() {
        MoreKeysKeyboardParams params = createParams(6, 3, XPOS_L1);
        assertEquals("6 key fix 3 L1 columns", 3, params.mNumColumns);
        assertEquals("6 key fix 3 L1 rows", 2, params.mNumRows);
        assertEquals("6 key fix 3 L1 left", 0, params.mLeftKeys);
        assertEquals("6 key fix 3 L1 right", 3, params.mRightKeys);
        assertEquals("6 key fix 3 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key fix 3 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key fix 3 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key fix 3 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key fix 3 L1 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key fix 3 L1 [6]", 2, params.getColumnPos(5));
        assertEquals("6 key fix 3 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 3 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4] [5] [6]
    // |___ [1] <2> [3]
    @Test
    public void testLayout6KeyFix3L2() {
        MoreKeysKeyboardParams params = createParams(6, 3, XPOS_L2);
        assertEquals("6 key fix 3 L2 columns", 3, params.mNumColumns);
        assertEquals("6 key fix 3 L2 rows", 2, params.mNumRows);
        assertEquals("6 key fix 3 L2 left", 1, params.mLeftKeys);
        assertEquals("6 key fix 3 L2 right", 2, params.mRightKeys);
        assertEquals("6 key fix 3 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("6 key fix 3 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("6 key fix 3 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("6 key fix 3 L2 [4]", -1, params.getColumnPos(3));
        assertEquals("6 key fix 3 L2 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key fix 3 L2 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key fix 3 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 3 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [4] [5] [6]|
    // [1] [2] <3>|
    @Test
    public void testLayout6KeyFix3R0() {
        MoreKeysKeyboardParams params = createParams(6, 3, XPOS_R0);
        assertEquals("6 key fix 3 R0 columns", 3, params.mNumColumns);
        assertEquals("6 key fix 3 R0 rows", 2, params.mNumRows);
        assertEquals("6 key fix 3 R0 left", 2, params.mLeftKeys);
        assertEquals("6 key fix 3 R0 right", 1, params.mRightKeys);
        assertEquals("6 key fix 3 R0 [1]", -2, params.getColumnPos(0));
        assertEquals("6 key fix 3 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key fix 3 R0 <3>", 0, params.getColumnPos(2));
        assertEquals("6 key fix 3 R0 [4]", -2, params.getColumnPos(3));
        assertEquals("6 key fix 3 R0 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key fix 3 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 3 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 3 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [4] [5] [6] ___|
    // [1] [2] <3> ___|
    @Test
    public void testLayout6KeyFix3R1() {
        MoreKeysKeyboardParams params = createParams(6, 3, XPOS_R1);
        assertEquals("6 key fix 3 R1 columns", 3, params.mNumColumns);
        assertEquals("6 key fix 3 R1 rows", 2, params.mNumRows);
        assertEquals("6 key fix 3 R1 left", 2, params.mLeftKeys);
        assertEquals("6 key fix 3 R1 right", 1, params.mRightKeys);
        assertEquals("6 key fix 3 R1 [1]", -2, params.getColumnPos(0));
        assertEquals("6 key fix 3 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key fix 3 R1 <3>", 0, params.getColumnPos(2));
        assertEquals("6 key fix 3 R1 [4]", -2, params.getColumnPos(3));
        assertEquals("6 key fix 3 R1 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key fix 3 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 3 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 3 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [4] [5] [6] ___|
    // [1] <2> [3] ___|
    @Test
    public void testLayout6KeyFix3R2() {
        MoreKeysKeyboardParams params = createParams(6, 3, XPOS_R2);
        assertEquals("6 key fix 3 R2 columns", 3, params.mNumColumns);
        assertEquals("6 key fix 3 R2 rows", 2, params.mNumRows);
        assertEquals("6 key fix 3 R2 left", 1, params.mLeftKeys);
        assertEquals("6 key fix 3 R2 right", 2, params.mRightKeys);
        assertEquals("6 key fix 3 R2 [1]", -1, params.getColumnPos(0));
        assertEquals("6 key fix 3 R2 <2>", 0, params.getColumnPos(1));
        assertEquals("6 key fix 3 R2 [1]", 1, params.getColumnPos(2));
        assertEquals("6 key fix 3 R2 [4]", -1, params.getColumnPos(3));
        assertEquals("6 key fix 3 R2 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key fix 3 R2 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key fix 3 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 3 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // <1> [2] [3] [4]
    @Test
    public void testLayout4KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_M0);
        assertEquals("4 key fix 5 columns", 4, params.mNumColumns);
        assertEquals("4 key fix 5 rows", 1, params.mNumRows);
        assertEquals("4 key fix 5 left", 1, params.mLeftKeys);
        assertEquals("4 key fix 5 right", 3, params.mRightKeys);
        assertEquals("4 key fix 5 <1>", -1, params.getColumnPos(0));
        assertEquals("4 key fix 5 [2]", 0, params.getColumnPos(1));
        assertEquals("4 key fix 5 [3]", 1, params.getColumnPos(2));
        assertEquals("4 key fix 5 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key fix 5 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 5 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4]
    @Test
    public void testLayout4KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_L0);
        assertEquals("4 key fix 5 L0 columns", 4, params.mNumColumns);
        assertEquals("4 key fix 5 L0 rows", 1, params.mNumRows);
        assertEquals("4 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("4 key fix 5 L0 right", 4, params.mRightKeys);
        assertEquals("4 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key fix 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key fix 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key fix 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout4KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_L1);
        assertEquals("4 key fix 5 L1 columns", 4, params.mNumColumns);
        assertEquals("4 key fix 5 L1 rows", 1, params.mNumRows);
        assertEquals("4 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("4 key fix 5 L1 right", 4, params.mRightKeys);
        assertEquals("4 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key fix 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key fix 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key fix 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key fix 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [1] <2> [3] [4]
    @Test
    public void testLayout4KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_L2);
        assertEquals("4 key fix 5 L2 columns", 4, params.mNumColumns);
        assertEquals("4 key fix 5 L2 rows", 1, params.mNumRows);
        assertEquals("4 key fix 5 L2 left", 1, params.mLeftKeys);
        assertEquals("4 key fix 5 L2 right", 3, params.mRightKeys);
        assertEquals("4 key fix 5 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("4 key fix 5 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("4 key fix 5 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("4 key fix 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key fix 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [1] [2] [3] <4>|
    @Test
    public void testLayout4KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_R0);
        assertEquals("4 key fix 5 R0 columns", 4, params.mNumColumns);
        assertEquals("4 key fix 5 R0 rows", 1, params.mNumRows);
        assertEquals("4 key fix 5 R0 left", 3, params.mLeftKeys);
        assertEquals("4 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("4 key fix 5 R0 [1]", -3, params.getColumnPos(0));
        assertEquals("4 key fix 5 R0 [2]", -2, params.getColumnPos(1));
        assertEquals("4 key fix 5 R0 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key fix 5 R0 <4>", 0, params.getColumnPos(3));
        assertEquals("4 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 5 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [1] [2] [3] <4> ___|
    @Test
    public void testLayout4KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_R1);
        assertEquals("4 key fix 5 R1 columns", 4, params.mNumColumns);
        assertEquals("4 key fix 5 R1 rows", 1, params.mNumRows);
        assertEquals("4 key fix 5 R1 left", 3, params.mLeftKeys);
        assertEquals("4 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("4 key fix 5 R1 [1]", -3, params.getColumnPos(0));
        assertEquals("4 key fix 5 R1 [2]", -2, params.getColumnPos(1));
        assertEquals("4 key fix 5 R1 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key fix 5 R1 <4>", 0, params.getColumnPos(3));
        assertEquals("4 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 5 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [1] [2] <3> [4] ___|
    @Test
    public void testLayout4KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_R2);
        assertEquals("4 key fix 5 R2 columns", 4, params.mNumColumns);
        assertEquals("4 key fix 5 R2 rows", 1, params.mNumRows);
        assertEquals("4 key fix 5 R2 left", 2, params.mLeftKeys);
        assertEquals("4 key fix 5 R2 right", 2, params.mRightKeys);
        assertEquals("4 key fix 5 R2 [1]", -2, params.getColumnPos(0));
        assertEquals("4 key fix 5 R2 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key fix 5 R2 <3>", 0, params.getColumnPos(2));
        assertEquals("4 key fix 5 R2 [4]", 1, params.getColumnPos(3));
        assertEquals("4 key fix 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key fix 5 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [5]
    // [1] <2> [3] [4]
    @Test
    public void testLayout5KeyFix4M0() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_M0);
        assertEquals("5 key fix 4 M0 columns", 4, params.mNumColumns);
        assertEquals("5 key fix 4 M0 rows", 2, params.mNumRows);
        assertEquals("5 key fix 4 M0 left", 1, params.mLeftKeys);
        assertEquals("5 key fix 4 M0 right", 3, params.mRightKeys);
        assertEquals("5 key fix 4 M0 [1]", -1, params.getColumnPos(0));
        assertEquals("5 key fix 4 M0 <2>", 0, params.getColumnPos(1));
        assertEquals("5 key fix 4 M0 [3]", 1, params.getColumnPos(2));
        assertEquals("5 key fix 4 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key fix 4 M0 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key fix 4 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5]
    // |<1> [2] [3] [4]
    @Test
    public void testLayout5KeyFix4L0() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_L0);
        assertEquals("5 key fix 4 L0 columns", 4, params.mNumColumns);
        assertEquals("5 key fix 4 L0 rows", 2, params.mNumRows);
        assertEquals("5 key fix 4 L0 left", 0, params.mLeftKeys);
        assertEquals("5 key fix 4 L0 right", 4, params.mRightKeys);
        assertEquals("5 key fix 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key fix 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key fix 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key fix 4 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key fix 4 L0 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key fix 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5]
    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout5KeyFix4L1() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_L1);
        assertEquals("5 key fix 4 L1 columns", 4, params.mNumColumns);
        assertEquals("5 key fix 4 L1 rows", 2, params.mNumRows);
        assertEquals("5 key fix 4 L1 left", 0, params.mLeftKeys);
        assertEquals("5 key fix 4 L1 right", 4, params.mRightKeys);
        assertEquals("5 key fix 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key fix 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key fix 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key fix 4 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key fix 4 L1 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key fix 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___     [5]
    // |___ [1] <2> [3] [4]
    @Test
    public void testLayout5KeyFix4L2() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_L2);
        assertEquals("5 key fix 4 L2 columns", 4, params.mNumColumns);
        assertEquals("5 key fix 4 L2 rows", 2, params.mNumRows);
        assertEquals("5 key fix 4 L2 left", 1, params.mLeftKeys);
        assertEquals("5 key fix 4 L2 right", 3, params.mRightKeys);
        assertEquals("5 key fix 4 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("5 key fix 4 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("5 key fix 4 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("5 key fix 4 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key fix 4 L2 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key fix 4 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //             [5]|
    // [1] [2] [3] <4>|
    @Test
    public void testLayout5KeyFix4R0() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_R0);
        assertEquals("5 key fix 4 R0 columns", 4, params.mNumColumns);
        assertEquals("5 key fix 4 R0 rows", 2, params.mNumRows);
        assertEquals("5 key fix 4 R0 left", 3, params.mLeftKeys);
        assertEquals("5 key fix 4 R0 right", 1, params.mRightKeys);
        assertEquals("5 key fix 4 R0 [1]", -3, params.getColumnPos(0));
        assertEquals("5 key fix 4 R0 [2]", -2, params.getColumnPos(1));
        assertEquals("5 key fix 4 R0 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key fix 4 R0 <4>", 0, params.getColumnPos(3));
        assertEquals("5 key fix 4 R0 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key fix 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 4 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //             [5] ___|
    // [1] [2] [3] <4> ___|
    @Test
    public void testLayout5KeyFix4R1() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_R1);
        assertEquals("5 key fix 4 R1 columns", 4, params.mNumColumns);
        assertEquals("5 key fix 4 R1 rows", 2, params.mNumRows);
        assertEquals("5 key fix 4 R1 left", 3, params.mLeftKeys);
        assertEquals("5 key fix 4 R1 right", 1, params.mRightKeys);
        assertEquals("5 key fix 4 R1 [1]", -3, params.getColumnPos(0));
        assertEquals("5 key fix 4 R1 [2]", -2, params.getColumnPos(1));
        assertEquals("5 key fix 4 R1 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key fix 4 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key fix 4 R1 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key fix 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 4 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //         [5]     ___|
    // [1] [2] <3> [4] ___|
    @Test
    public void testLayout5KeyFix4R2() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_R2);
        assertEquals("5 key fix 4 R2 columns", 4, params.mNumColumns);
        assertEquals("5 key fix 4 R2 rows", 2, params.mNumRows);
        assertEquals("5 key fix 4 R2 left", 2, params.mLeftKeys);
        assertEquals("5 key fix 4 R2 right", 2, params.mRightKeys);
        assertEquals("5 key fix 4 R2 [1]", -2, params.getColumnPos(0));
        assertEquals("5 key fix 4 R2 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key fix 4 R2 <3>", 0, params.getColumnPos(2));
        assertEquals("5 key fix 4 R2 [4]", 1, params.getColumnPos(3));
        assertEquals("5 key fix 4 R2 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key fix 4 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 4 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [5] [6]
    // [1] <2> [3] [4]
    @Test
    public void testLayout6KeyFix4M0() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_M0);
        assertEquals("6 key fix 4 M0 columns", 4, params.mNumColumns);
        assertEquals("6 key fix 4 M0 rows", 2, params.mNumRows);
        assertEquals("6 key fix 4 M0 left", 1, params.mLeftKeys);
        assertEquals("6 key fix 4 M0 right", 3, params.mRightKeys);
        assertEquals("6 key fix 4 M0 [1]", -1, params.getColumnPos(0));
        assertEquals("6 key fix 4 M0 <2>", 0, params.getColumnPos(1));
        assertEquals("6 key fix 4 M0 [3]", 1, params.getColumnPos(2));
        assertEquals("6 key fix 4 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("6 key fix 4 M0 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key fix 4 M0 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key fix 4 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("6 key fix 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5] [6]
    // |<1> [2] [3] [4]
    @Test
    public void testLayout6KeyFix4L0() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_L0);
        assertEquals("6 key fix 4 L0 columns", 4, params.mNumColumns);
        assertEquals("6 key fix 4 L0 rows", 2, params.mNumRows);
        assertEquals("6 key fix 4 L0 left", 0, params.mLeftKeys);
        assertEquals("6 key fix 4 L0 right", 4, params.mRightKeys);
        assertEquals("6 key fix 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key fix 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key fix 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key fix 4 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("6 key fix 4 L0 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key fix 4 L0 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key fix 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6]
    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout6KeyFix4L1() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_L1);
        assertEquals("6 key fix 4 L1 columns", 4, params.mNumColumns);
        assertEquals("6 key fix 4 L1 rows", 2, params.mNumRows);
        assertEquals("6 key fix 4 L1 left", 0, params.mLeftKeys);
        assertEquals("6 key fix 4 L1 right", 4, params.mRightKeys);
        assertEquals("6 key fix 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key fix 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key fix 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key fix 4 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("6 key fix 4 L1 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key fix 4 L1 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key fix 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [5] [6]
    // |___ [1] <2> [3] [4]
    @Test
    public void testLayout6KeyFix4L2() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_L2);
        assertEquals("6 key fix 4 L2 columns", 4, params.mNumColumns);
        assertEquals("6 key fix 4 L2 rows", 2, params.mNumRows);
        assertEquals("6 key fix 4 L2 left", 1, params.mLeftKeys);
        assertEquals("6 key fix 4 L2 right", 3, params.mRightKeys);
        assertEquals("6 key fix 4 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("6 key fix 4 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("6 key fix 4 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("6 key fix 4 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("6 key fix 4 L2 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key fix 4 L2 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key fix 4 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("6 key fix 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //         [5] [6]|
    // [1] [2] [3] <4>|
    @Test
    public void testLayout6KeyFix4R0() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_R0);
        assertEquals("6 key fix 4 R0 columns", 4, params.mNumColumns);
        assertEquals("6 key fix 4 R0 rows", 2, params.mNumRows);
        assertEquals("6 key fix 4 R0 left", 3, params.mLeftKeys);
        assertEquals("6 key fix 4 R0 right", 1, params.mRightKeys);
        assertEquals("6 key fix 4 R0 [1]", -3, params.getColumnPos(0));
        assertEquals("6 key fix 4 R0 [2]", -2, params.getColumnPos(1));
        assertEquals("6 key fix 4 R0 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key fix 4 R0 <4>", 0, params.getColumnPos(3));
        assertEquals("6 key fix 4 R0 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key fix 4 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 4 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //         [5] [6] ___|
    // [1] [2] [3] <4> ___|
    @Test
    public void testLayout6KeyFix4R1() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_R1);
        assertEquals("6 key fix 4 R1 columns", 4, params.mNumColumns);
        assertEquals("6 key fix 4 R1 rows", 2, params.mNumRows);
        assertEquals("6 key fix 4 R1 left", 3, params.mLeftKeys);
        assertEquals("6 key fix 4 R1 right", 1, params.mRightKeys);
        assertEquals("6 key fix 4 R1 [1]", -3, params.getColumnPos(0));
        assertEquals("6 key fix 4 R1 [2]", -2, params.getColumnPos(1));
        assertEquals("6 key fix 4 R1 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key fix 4 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key fix 4 R1 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key fix 4 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 4 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //       [5] [6]   ___|
    // [1] [2] <3> [4] ___|
    @Test
    public void testLayout6KeyFix4R2() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_R2);
        assertEquals("6 key fix 4 R2 columns", 4, params.mNumColumns);
        assertEquals("6 key fix 4 R2 rows", 2, params.mNumRows);
        assertEquals("6 key fix 4 R2 left", 2, params.mLeftKeys);
        assertEquals("6 key fix 4 R2 right", 2, params.mRightKeys);
        assertEquals("6 key fix 4 R2 [1]", -2, params.getColumnPos(0));
        assertEquals("6 key fix 4 R2 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key fix 4 R2 <3>", 0, params.getColumnPos(2));
        assertEquals("6 key fix 4 R2 [4]", 1, params.getColumnPos(3));
        assertEquals("6 key fix 4 R2 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key fix 4 R2 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key fix 4 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("6 key fix 4 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [5] [6] [7]
    // [1] <2> [3] [4]
    @Test
    public void testLayout7KeyFix4M0() {
        MoreKeysKeyboardParams params = createParams(7, 4, XPOS_M0);
        assertEquals("7 key fix 4 M0 columns", 4, params.mNumColumns);
        assertEquals("7 key fix 4 M0 rows", 2, params.mNumRows);
        assertEquals("7 key fix 4 M0 left", 1, params.mLeftKeys);
        assertEquals("7 key fix 4 M0 right", 3, params.mRightKeys);
        assertEquals("7 key fix 4 M0 [1]", -1, params.getColumnPos(0));
        assertEquals("7 key fix 4 M0 <2>", 0, params.getColumnPos(1));
        assertEquals("7 key fix 4 M0 [3]", 1, params.getColumnPos(2));
        assertEquals("7 key fix 4 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key fix 4 M0 [5]", -1, params.getColumnPos(4));
        assertEquals("7 key fix 4 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key fix 4 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key fix 4 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5] [6] [7]
    // |<1> [2] [3] [4]
    @Test
    public void testLayout7KeyFix4L0() {
        MoreKeysKeyboardParams params = createParams(7, 4, XPOS_L0);
        assertEquals("7 key fix 4 L0 columns", 4, params.mNumColumns);
        assertEquals("7 key fix 4 L0 rows", 2, params.mNumRows);
        assertEquals("7 key fix 4 L0 left", 0, params.mLeftKeys);
        assertEquals("7 key fix 4 L0 right", 4, params.mRightKeys);
        assertEquals("7 key fix 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key fix 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key fix 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key fix 4 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key fix 4 L0 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key fix 4 L0 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key fix 4 L0 [7]", 2, params.getColumnPos(6));
        assertEquals("7 key fix 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6] [7]
    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout7KeyFix4L1() {
        MoreKeysKeyboardParams params = createParams(7, 4, XPOS_L1);
        assertEquals("7 key fix 4 L1 columns", 4, params.mNumColumns);
        assertEquals("7 key fix 4 L1 rows", 2, params.mNumRows);
        assertEquals("7 key fix 4 L1 left", 0, params.mLeftKeys);
        assertEquals("7 key fix 4 L1 right", 4, params.mRightKeys);
        assertEquals("7 key fix 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key fix 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key fix 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key fix 4 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key fix 4 L1 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key fix 4 L1 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key fix 4 l1 [7]", 2, params.getColumnPos(6));
        assertEquals("7 key fix 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6] [7]
    // |___ [1] <2> [3] [4]
    @Test
    public void testLayout7KeyFix4L2() {
        MoreKeysKeyboardParams params = createParams(7, 4, XPOS_L2);
        assertEquals("7 key fix 4 L2 columns", 4, params.mNumColumns);
        assertEquals("7 key fix 4 L2 rows", 2, params.mNumRows);
        assertEquals("7 key fix 4 L2 left", 1, params.mLeftKeys);
        assertEquals("7 key fix 4 L2 right", 3, params.mRightKeys);
        assertEquals("7 key fix 4 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("7 key fix 4 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("7 key fix 4 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("7 key fix 4 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key fix 4 L2 [5]", -1, params.getColumnPos(4));
        assertEquals("7 key fix 4 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key fix 4 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key fix 4 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [5] [6] [7]|
    // [1] [2] [3] <4>|
    @Test
    public void testLayout7KeyFix4R0() {
        MoreKeysKeyboardParams params = createParams(7, 4, XPOS_R0);
        assertEquals("7 key fix 4 R0 columns", 4, params.mNumColumns);
        assertEquals("7 key fix 4 R0 rows", 2, params.mNumRows);
        assertEquals("7 key fix 4 R0 left", 3, params.mLeftKeys);
        assertEquals("7 key fix 4 R0 right", 1, params.mRightKeys);
        assertEquals("7 key fix 4 R0 [1]", -3, params.getColumnPos(0));
        assertEquals("7 key fix 4 R0 [2]", -2, params.getColumnPos(1));
        assertEquals("7 key fix 4 R0 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key fix 4 R0 <4>", 0, params.getColumnPos(3));
        assertEquals("7 key fix 4 R0 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key fix 4 R0 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key fix 4 R0 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key fix 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 4 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //     [5] [6] [7] ___|
    // [1] [2] [3] <4> ___|
    @Test
    public void testLayout7KeyFix4R1() {
        MoreKeysKeyboardParams params = createParams(7, 4, XPOS_R1);
        assertEquals("7 key fix 4 R1 columns", 4, params.mNumColumns);
        assertEquals("7 key fix 4 R1 rows", 2, params.mNumRows);
        assertEquals("7 key fix 4 R1 left", 3, params.mLeftKeys);
        assertEquals("7 key fix 4 R1 right", 1, params.mRightKeys);
        assertEquals("7 key fix 4 R1 [1]", -3, params.getColumnPos(0));
        assertEquals("7 key fix 4 R1 [2]", -2, params.getColumnPos(1));
        assertEquals("7 key fix 4 R1 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key fix 4 R1 <4>", 0, params.getColumnPos(3));
        assertEquals("7 key fix 4 R1 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key fix 4 R1 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key fix 4 R1 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key fix 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 4 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //     [5] [6] [7] ___|
    // [1] [2] <3> [4] ___|
    @Test
    public void testLayout7KeyFix4R2() {
        MoreKeysKeyboardParams params = createParams(7, 4, XPOS_R2);
        assertEquals("7 key fix 4 R2 columns", 4, params.mNumColumns);
        assertEquals("7 key fix 4 R2 rows", 2, params.mNumRows);
        assertEquals("7 key fix 4 R2 left", 2, params.mLeftKeys);
        assertEquals("7 key fix 4 R2 right", 2, params.mRightKeys);
        assertEquals("7 key fix 4 R2 [1]", -2, params.getColumnPos(0));
        assertEquals("7 key fix 4 R2 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key fix 4 R2 <3>", 0, params.getColumnPos(2));
        assertEquals("7 key fix 4 R2 [4]", 1, params.getColumnPos(3));
        assertEquals("7 key fix 4 R2 [5]", -1, params.getColumnPos(4));
        assertEquals("7 key fix 4 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key fix 4 R2 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key fix 4 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 4 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [5] [6] [7] [8]
    // [1] <2> [3] [4]
    @Test
    public void testLayout8KeyFix4M0() {
        MoreKeysKeyboardParams params = createParams(8, 4, XPOS_M0);
        assertEquals("8 key fix 4 M0 columns", 4, params.mNumColumns);
        assertEquals("8 key fix 4 M0 rows", 2, params.mNumRows);
        assertEquals("8 key fix 4 M0 left", 1, params.mLeftKeys);
        assertEquals("8 key fix 4 M0 right", 3, params.mRightKeys);
        assertEquals("8 key fix 4 M0 [1]", -1, params.getColumnPos(0));
        assertEquals("8 key fix 4 M0 <2>", 0, params.getColumnPos(1));
        assertEquals("8 key fix 4 M0 [3]", 1, params.getColumnPos(2));
        assertEquals("8 key fix 4 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("8 key fix 4 M0 [5]", -1, params.getColumnPos(4));
        assertEquals("8 key fix 4 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key fix 4 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("8 key fix 4 M0 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key fix 4 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5] [6] [7] [8]
    // |<1> [2] [3] [4]
    @Test
    public void testLayout8KeyFix4L0() {
        MoreKeysKeyboardParams params = createParams(8, 4, XPOS_L0);
        assertEquals("8 key fix 4 L0 columns", 4, params.mNumColumns);
        assertEquals("8 key fix 4 L0 rows", 2, params.mNumRows);
        assertEquals("8 key fix 4 L0 left", 0, params.mLeftKeys);
        assertEquals("8 key fix 4 L0 right", 4, params.mRightKeys);
        assertEquals("8 key fix 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key fix 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key fix 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key fix 4 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key fix 4 L0 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key fix 4 L0 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key fix 4 L0 [7]", 2, params.getColumnPos(6));
        assertEquals("8 key fix 4 L0 [8]", 3, params.getColumnPos(7));
        assertEquals("8 key fix 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6] [7] [8]
    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout8KeyFix4L1() {
        MoreKeysKeyboardParams params = createParams(8, 4, XPOS_L1);
        assertEquals("8 key fix 4 L1 columns", 4, params.mNumColumns);
        assertEquals("8 key fix 4 L1 rows", 2, params.mNumRows);
        assertEquals("8 key fix 4 L1 left", 0, params.mLeftKeys);
        assertEquals("8 key fix 4 L1 right", 4, params.mRightKeys);
        assertEquals("8 key fix 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key fix 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key fix 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key fix 4 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key fix 4 L1 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key fix 4 L1 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key fix 4 L1 [7]", 2, params.getColumnPos(6));
        assertEquals("8 key fix 4 L1 [8]", 3, params.getColumnPos(7));
        assertEquals("8 key fix 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6] [7] [8]
    // |___ [1] <2> [3] [4]
    @Test
    public void testLayout8KeyFix4L2() {
        MoreKeysKeyboardParams params = createParams(8, 4, XPOS_L2);
        assertEquals("8 key fix 4 L2 columns", 4, params.mNumColumns);
        assertEquals("8 key fix 4 L2 rows", 2, params.mNumRows);
        assertEquals("8 key fix 4 L2 left", 1, params.mLeftKeys);
        assertEquals("8 key fix 4 L2 right", 3, params.mRightKeys);
        assertEquals("8 key fix 4 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("8 key fix 4 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("8 key fix 4 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("8 key fix 4 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("8 key fix 4 L2 [5]", -1, params.getColumnPos(4));
        assertEquals("8 key fix 4 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key fix 4 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("8 key fix 4 L2 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key fix 4 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [5] [6] [7] [8]|
    // [1] [2] [3] <4>|
    @Test
    public void testLayout8KeyFix4R0() {
        MoreKeysKeyboardParams params = createParams(8, 4, XPOS_R0);
        assertEquals("8 key fix 4 R0 columns", 4, params.mNumColumns);
        assertEquals("8 key fix 4 R0 rows", 2, params.mNumRows);
        assertEquals("8 key fix 4 R0 left", 3, params.mLeftKeys);
        assertEquals("8 key fix 4 R0 right", 1, params.mRightKeys);
        assertEquals("8 key fix 4 R0 [1]", -3, params.getColumnPos(0));
        assertEquals("8 key fix 4 R0 [2]", -2, params.getColumnPos(1));
        assertEquals("8 key fix 4 R0 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key fix 4 R0 <4>", 0, params.getColumnPos(3));
        assertEquals("8 key fix 4 R0 [5]", -3, params.getColumnPos(4));
        assertEquals("8 key fix 4 R0 [6]", -2, params.getColumnPos(5));
        assertEquals("8 key fix 4 R0 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key fix 4 R0 [8]", 0, params.getColumnPos(7));
        assertEquals("8 key fix 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 4 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [5] [6] [7] [8] ___|
    // [1] [2] [3] <4> ___|
    @Test
    public void testLayout8KeyFix4R1() {
        MoreKeysKeyboardParams params = createParams(8, 4, XPOS_R1);
        assertEquals("8 key fix 4 R1 columns", 4, params.mNumColumns);
        assertEquals("8 key fix 4 R1 rows", 2, params.mNumRows);
        assertEquals("8 key fix 4 R1 left", 3, params.mLeftKeys);
        assertEquals("8 key fix 4 R1 right", 1, params.mRightKeys);
        assertEquals("8 key fix 4 R1 [1]", -3, params.getColumnPos(0));
        assertEquals("8 key fix 4 R1 [2]", -2, params.getColumnPos(1));
        assertEquals("8 key fix 4 R1 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key fix 4 R1 <4>", 0, params.getColumnPos(3));
        assertEquals("8 key fix 4 R1 [5]", -3, params.getColumnPos(4));
        assertEquals("8 key fix 4 R1 [6]", -2, params.getColumnPos(5));
        assertEquals("8 key fix 4 R1 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key fix 4 R1 [8]", 0, params.getColumnPos(7));
        assertEquals("8 key fix 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 4 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [5] [6] [7] [8] ___|
    // [1] [2] <3> [4] ___|
    @Test
    public void testLayout8KeyFix4R2() {
        MoreKeysKeyboardParams params = createParams(8, 4, XPOS_R2);
        assertEquals("8 key fix 4 R2 columns", 4, params.mNumColumns);
        assertEquals("8 key fix 4 R2 rows", 2, params.mNumRows);
        assertEquals("8 key fix 4 R2 left", 2, params.mLeftKeys);
        assertEquals("8 key fix 4 R2 right", 2, params.mRightKeys);
        assertEquals("8 key fix 4 R2 [1]", -2, params.getColumnPos(0));
        assertEquals("8 key fix 4 R2 [2]", -1, params.getColumnPos(1));
        assertEquals("8 key fix 4 R2 <3>", 0, params.getColumnPos(2));
        assertEquals("8 key fix 4 R2 [4]", 1, params.getColumnPos(3));
        assertEquals("8 key fix 4 R2 [5]", -2, params.getColumnPos(4));
        assertEquals("8 key fix 4 R2 [6]", -1, params.getColumnPos(5));
        assertEquals("8 key fix 4 R2 [7]", 0, params.getColumnPos(6));
        assertEquals("8 key fix 4 R2 [8]", 1, params.getColumnPos(7));
        assertEquals("8 key fix 4 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 4 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

     // [1] [2] <3> [4] [5]
    @Test
    public void testLayout5KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_M0);
        assertEquals("5 key fix 5 columns", 5, params.mNumColumns);
        assertEquals("5 key fix 5 rows", 1, params.mNumRows);
        assertEquals("5 key fix 5 left", 2, params.mLeftKeys);
        assertEquals("5 key fix 5 right", 3, params.mRightKeys);
        assertEquals("5 key fix 5 [1]", -2, params.getColumnPos(0));
        assertEquals("5 key fix 5 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key fix 5 <3>", 0, params.getColumnPos(2));
        assertEquals("5 key fix 5 [4]", 1, params.getColumnPos(3));
        assertEquals("5 key fix 5 [5]", 2, params.getColumnPos(4));
        assertEquals("5 key fix 5 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 5 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout5KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_L0);
        assertEquals("5 key fix 5 L0 columns", 5, params.mNumColumns);
        assertEquals("5 key fix 5 L0 rows", 1, params.mNumRows);
        assertEquals("5 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("5 key fix 5 L0 right", 5, params.mRightKeys);
        assertEquals("5 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key fix 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key fix 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key fix 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key fix 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("5 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout5KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_L1);
        assertEquals("5 key fix 5 L1 columns", 5, params.mNumColumns);
        assertEquals("5 key fix 5 L1 rows", 1, params.mNumRows);
        assertEquals("5 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("5 key fix 5 L1 right", 5, params.mRightKeys);
        assertEquals("5 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key fix 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key fix 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key fix 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key fix 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("5 key fix 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [1] <2> [3] [4] [5]
    @Test
    public void testLayout5KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_L2);
        assertEquals("5 key fix 5 L2 columns", 5, params.mNumColumns);
        assertEquals("5 key fix 5 L2 rows", 1, params.mNumRows);
        assertEquals("5 key fix 5 L2 left", 1, params.mLeftKeys);
        assertEquals("5 key fix 5 L2 right", 4, params.mRightKeys);
        assertEquals("5 key fix 5 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("5 key fix 5 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("5 key fix 5 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("5 key fix 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key fix 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("5 key fix 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [1] [2] [3] [4] <5>|
    @Test
    public void testLayout5KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_R0);
        assertEquals("5 key fix 5 R0 columns", 5, params.mNumColumns);
        assertEquals("5 key fix 5 R0 rows", 1, params.mNumRows);
        assertEquals("5 key fix 5 R0 left", 4, params.mLeftKeys);
        assertEquals("5 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("5 key fix 5 R0 [1]", -4, params.getColumnPos(0));
        assertEquals("5 key fix 5 R0 [2]", -3, params.getColumnPos(1));
        assertEquals("5 key fix 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key fix 5 R0 [4]", -1, params.getColumnPos(3));
        assertEquals("5 key fix 5 R0 <5>", 0, params.getColumnPos(4));
        assertEquals("5 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [1] [2] [3] [4] <5> ___|
    @Test
    public void testLayout5KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_R1);
        assertEquals("5 key fix 5 R1 columns", 5, params.mNumColumns);
        assertEquals("5 key fix 5 R1 rows", 1, params.mNumRows);
        assertEquals("5 key fix 5 R1 left", 4, params.mLeftKeys);
        assertEquals("5 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("5 key fix 5 R1 [1]", -4, params.getColumnPos(0));
        assertEquals("5 key fix 5 R1 [2]", -3, params.getColumnPos(1));
        assertEquals("5 key fix 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key fix 5 R1 [4]", -1, params.getColumnPos(3));
        assertEquals("5 key fix 5 R1 <5>", 0, params.getColumnPos(4));
        assertEquals("5 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [1] [2] [3] <4> [5] ___|
    @Test
    public void testLayout5KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_R2);
        assertEquals("5 key fix 5 R2 columns", 5, params.mNumColumns);
        assertEquals("5 key fix 5 R2 rows", 1, params.mNumRows);
        assertEquals("5 key fix 5 R2 left", 3, params.mLeftKeys);
        assertEquals("5 key fix 5 R2 right", 2, params.mRightKeys);
        assertEquals("5 key fix 5 R2 [1]", -3, params.getColumnPos(0));
        assertEquals("5 key fix 5 R2 [2]", -2, params.getColumnPos(1));
        assertEquals("5 key fix 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key fix 5 R2 <4>", 0, params.getColumnPos(3));
        assertEquals("5 key fix 5 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key fix 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key fix 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //         [6]
    // [1] [2] <3> [4] [5]
    @Test
    public void testLayout6KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_M0);
        assertEquals("6 key fix 5 columns", 5, params.mNumColumns);
        assertEquals("6 key fix 5 rows", 2, params.mNumRows);
        assertEquals("6 key fix 5 left", 2, params.mLeftKeys);
        assertEquals("6 key fix 5 right", 3, params.mRightKeys);
        assertEquals("6 key fix 5 [1]", -2, params.getColumnPos(0));
        assertEquals("6 key fix 5 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key fix 5 <3>", 0, params.getColumnPos(2));
        assertEquals("6 key fix 5 [4]", 1, params.getColumnPos(3));
        assertEquals("6 key fix 5 [5]", 2, params.getColumnPos(4));
        assertEquals("6 key fix 5 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 5 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 5 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout6KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_L0);
        assertEquals("6 key fix 5 L0 columns", 5, params.mNumColumns);
        assertEquals("6 key fix 5 L0 rows", 2, params.mNumRows);
        assertEquals("6 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("6 key fix 5 L0 right", 5, params.mRightKeys);
        assertEquals("6 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key fix 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key fix 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key fix 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("6 key fix 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("6 key fix 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout6KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_L1);
        assertEquals("6 key fix 5 L1 columns", 5, params.mNumColumns);
        assertEquals("6 key fix 5 L1 rows", 2, params.mNumRows);
        assertEquals("6 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("6 key fix 5 L1 right", 5, params.mRightKeys);
        assertEquals("6 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key fix 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key fix 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key fix 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("6 key fix 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("6 key fix 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___     [6]
    // |___ [1] <2> [3] [4] [5]
    @Test
    public void testLayout6KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_L2);
        assertEquals("6 key fix 5 L2 columns", 5, params.mNumColumns);
        assertEquals("6 key fix 5 L2 rows", 2, params.mNumRows);
        assertEquals("6 key fix 5 L2 left", 1, params.mLeftKeys);
        assertEquals("6 key fix 5 L2 right", 4, params.mRightKeys);
        assertEquals("6 key fix 5 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("6 key fix 5 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("6 key fix 5 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("6 key fix 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("6 key fix 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("6 key fix 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //                 [6]|
    // [1] [2] [3] [4] <5>|
    @Test
    public void testLayout6KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_R0);
        assertEquals("6 key fix 5 R0 columns", 5, params.mNumColumns);
        assertEquals("6 key fix 5 R0 rows", 2, params.mNumRows);
        assertEquals("6 key fix 5 R0 left", 4, params.mLeftKeys);
        assertEquals("6 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("6 key fix 5 R0 [1]", -4, params.getColumnPos(0));
        assertEquals("6 key fix 5 R0 [2]", -3, params.getColumnPos(1));
        assertEquals("6 key fix 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key fix 5 R0 [4]", -1, params.getColumnPos(3));
        assertEquals("6 key fix 5 R0 <5>", 0, params.getColumnPos(4));
        assertEquals("6 key fix 5 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //                 [6] ___|
    // [1] [2] [3] [4] <5> ___|
    @Test
    public void testLayout6KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_R1);
        assertEquals("6 key fix 5 R1 columns", 5, params.mNumColumns);
        assertEquals("6 key fix 5 R1 rows", 2, params.mNumRows);
        assertEquals("6 key fix 5 R1 left", 4, params.mLeftKeys);
        assertEquals("6 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("6 key fix 5 R1 [1]", -4, params.getColumnPos(0));
        assertEquals("6 key fix 5 R1 [2]", -3, params.getColumnPos(1));
        assertEquals("6 key fix 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key fix 5 R1 [4]", -1, params.getColumnPos(3));
        assertEquals("6 key fix 5 R1 <5>", 0, params.getColumnPos(4));
        assertEquals("6 key fix 5 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //             [6]     ___|
    // [1] [2] [3] <4> [5] ___|
    @Test
    public void testLayout6KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_R2);
        assertEquals("6 key fix 5 R2 columns", 5, params.mNumColumns);
        assertEquals("6 key fix 5 R2 rows", 2, params.mNumRows);
        assertEquals("6 key fix 5 R2 left", 3, params.mLeftKeys);
        assertEquals("6 key fix 5 R2 right", 2, params.mRightKeys);
        assertEquals("6 key fix 5 R2 [1]", -3, params.getColumnPos(0));
        assertEquals("6 key fix 5 R2 [2]", -2, params.getColumnPos(1));
        assertEquals("6 key fix 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key fix 5 R2 <4>", 0, params.getColumnPos(3));
        assertEquals("6 key fix 5 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key fix 5 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key fix 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key fix 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //       [6] [7]
    // [1] [2] <3> [4] [5]
    @Test
    public void testLayout7KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_M0);
        assertEquals("7 key fix 5 columns", 5, params.mNumColumns);
        assertEquals("7 key fix 5 rows", 2, params.mNumRows);
        assertEquals("7 key fix 5 left", 2, params.mLeftKeys);
        assertEquals("7 key fix 5 right", 3, params.mRightKeys);
        assertEquals("7 key fix 5 [1]", -2, params.getColumnPos(0));
        assertEquals("7 key fix 5 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key fix 5 <3>", 0, params.getColumnPos(2));
        assertEquals("7 key fix 5 [4]", 1, params.getColumnPos(3));
        assertEquals("7 key fix 5 [5]", 2, params.getColumnPos(4));
        assertEquals("7 key fix 5 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key fix 5 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key fix 5 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key fix 5 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout7KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_L0);
        assertEquals("7 key fix 5 L0 columns", 5, params.mNumColumns);
        assertEquals("7 key fix 5 L0 rows", 2, params.mNumRows);
        assertEquals("7 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("7 key fix 5 L0 right", 5, params.mRightKeys);
        assertEquals("7 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key fix 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key fix 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key fix 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key fix 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key fix 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key fix 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout7KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_L1);
        assertEquals("7 key fix 5 L1 columns", 5, params.mNumColumns);
        assertEquals("7 key fix 5 L1 rows", 2, params.mNumRows);
        assertEquals("7 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("7 key fix 5 L1 right", 5, params.mRightKeys);
        assertEquals("7 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key fix 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key fix 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key fix 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key fix 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key fix 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key fix 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key fix 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [6] [7]
    // |___ [1] <2> [3] [4] [5]
    @Test
    public void testLayout7KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_L2);
        assertEquals("7 key fix 5 L2 columns", 5, params.mNumColumns);
        assertEquals("7 key fix 5 L2 rows", 2, params.mNumRows);
        assertEquals("7 key fix 5 L2 left", 1, params.mLeftKeys);
        assertEquals("7 key fix 5 L2 right", 4, params.mRightKeys);
        assertEquals("7 key fix 5 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("7 key fix 5 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("7 key fix 5 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("7 key fix 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key fix 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("7 key fix 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key fix 5 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key fix 5 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key fix 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //             [6] [7]|
    // [1] [2] [3] [4] <5>|
    @Test
    public void testLayout7KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_R0);
        assertEquals("7 key fix 5 R0 columns", 5, params.mNumColumns);
        assertEquals("7 key fix 5 R0 rows", 2, params.mNumRows);
        assertEquals("7 key fix 5 R0 left", 4, params.mLeftKeys);
        assertEquals("7 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("7 key fix 5 R0 [1]", -4, params.getColumnPos(0));
        assertEquals("7 key fix 5 R0 [2]", -3, params.getColumnPos(1));
        assertEquals("7 key fix 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key fix 5 R0 [4]", -1, params.getColumnPos(3));
        assertEquals("7 key fix 5 R0 <5>", 0, params.getColumnPos(4));
        assertEquals("7 key fix 5 R0 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key fix 5 R0 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //             [6] [7] ___|
    // [1] [2] [3] [4] <5> ___|
    @Test
    public void testLayout7KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_R1);
        assertEquals("7 key fix 5 R1 columns", 5, params.mNumColumns);
        assertEquals("7 key fix 5 R1 rows", 2, params.mNumRows);
        assertEquals("7 key fix 5 R1 left", 4, params.mLeftKeys);
        assertEquals("7 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("7 key fix 5 R1 [1]", -4, params.getColumnPos(0));
        assertEquals("7 key fix 5 R1 [2]", -3, params.getColumnPos(1));
        assertEquals("7 key fix 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key fix 5 R1 [4]", -1, params.getColumnPos(3));
        assertEquals("7 key fix 5 R1 <5>", 0, params.getColumnPos(4));
        assertEquals("7 key fix 5 R1 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key fix 5 R1 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //           [6] [7]   ___|
    // [1] [2] [3] <4> [5] ___|
    @Test
    public void testLayout7KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_R2);
        assertEquals("7 key fix 5 R2 columns",5, params.mNumColumns);
        assertEquals("7 key fix 5 R2 rows", 2, params.mNumRows);
        assertEquals("7 key fix 5 R2 left", 3, params.mLeftKeys);
        assertEquals("7 key fix 5 R2 right", 2, params.mRightKeys);
        assertEquals("7 key fix 5 R2 [1]", -3, params.getColumnPos(0));
        assertEquals("7 key fix 5 R2 [2]", -2, params.getColumnPos(1));
        assertEquals("7 key fix 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key fix 5 R2 <4>", 0, params.getColumnPos(3));
        assertEquals("7 key fix 5 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key fix 5 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key fix 5 R2 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key fix 5 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key fix 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //     [6] [7] [8]
    // [1] [2] <3> [4] [5]
    @Test
    public void testLayout8KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_M0);
        assertEquals("8 key fix 5 M0 columns", 5, params.mNumColumns);
        assertEquals("8 key fix 5 M0 rows", 2, params.mNumRows);
        assertEquals("8 key fix 5 M0 left", 2, params.mLeftKeys);
        assertEquals("8 key fix 5 M0 right", 3, params.mRightKeys);
        assertEquals("8 key fix 5 M0 [1]", -2, params.getColumnPos(0));
        assertEquals("8 key fix 5 M0 [2]", -1, params.getColumnPos(1));
        assertEquals("8 key fix 5 M0 <3>", 0, params.getColumnPos(2));
        assertEquals("8 key fix 5 M0 [4]", 1, params.getColumnPos(3));
        assertEquals("8 key fix 5 M0 [5]", 2, params.getColumnPos(4));
        assertEquals("8 key fix 5 M0 [6]", -1, params.getColumnPos(5));
        assertEquals("8 key fix 5 M0 [7]", 0, params.getColumnPos(6));
        assertEquals("8 key fix 5 M0 [8]", 1, params.getColumnPos(7));
        assertEquals("8 key fix 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout8KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_L0);
        assertEquals("8 key fix 5 L0 columns", 5, params.mNumColumns);
        assertEquals("8 key fix 5 L0 rows", 2, params.mNumRows);
        assertEquals("8 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("8 key fix 5 L0 right", 5, params.mRightKeys);
        assertEquals("8 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key fix 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key fix 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key fix 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key fix 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("8 key fix 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key fix 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("8 key fix 5 L0 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout8KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_L1);
        assertEquals("8 key fix 5 L1 columns", 5, params.mNumColumns);
        assertEquals("8 key fix 5 L1 rows", 2, params.mNumRows);
        assertEquals("8 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("8 key fix 5 L1 right", 5, params.mRightKeys);
        assertEquals("8 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key fix 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key fix 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key fix 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key fix 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("8 key fix 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key fix 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("8 key fix 5 L1 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key fix 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8]
    // |___ [1] <2> [3] [4] [5]
    @Test
    public void testLayout8KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_L2);
        assertEquals("8 key fix 5 L2 columns", 5, params.mNumColumns);
        assertEquals("8 key fix 5 L2 rows", 2, params.mNumRows);
        assertEquals("8 key fix 5 L2 left", 1, params.mLeftKeys);
        assertEquals("8 key fix 5 L2 right", 4, params.mRightKeys);
        assertEquals("8 key fix 5 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("8 key fix 5 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("8 key fix 5 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("8 key fix 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("8 key fix 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("8 key fix 5 L2 [6]", -1, params.getColumnPos(5));
        assertEquals("8 key fix 5 L2 [7]", 0, params.getColumnPos(6));
        assertEquals("8 key fix 5 L2 [8]", 1, params.getColumnPos(7));
        assertEquals("8 key fix 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //         [6] [7] [8]|
    // [1] [2] [3] [4] <5>|
    @Test
    public void testLayout8KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_R0);
        assertEquals("8 key fix 5 R0 columns", 5, params.mNumColumns);
        assertEquals("8 key fix 5 R0 rows", 2, params.mNumRows);
        assertEquals("8 key fix 5 R0 left", 4, params.mLeftKeys);
        assertEquals("8 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("8 key fix 5 R0 [1]", -4, params.getColumnPos(0));
        assertEquals("8 key fix 5 R0 [2]", -3, params.getColumnPos(1));
        assertEquals("8 key fix 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("8 key fix 5 R0 [4]", -1, params.getColumnPos(3));
        assertEquals("8 key fix 5 R0 <5>", 0, params.getColumnPos(4));
        assertEquals("8 key fix 5 R0 [6]", -2, params.getColumnPos(5));
        assertEquals("8 key fix 5 R0 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key fix 5 R0 [8]", 0, params.getColumnPos(7));
        assertEquals("8 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //         [6] [7] [8] ___|
    // [1] [2] [3] [4] <5> ___|
    @Test
    public void testLayout8KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_R1);
        assertEquals("8 key fix 5 R1 columns", 5, params.mNumColumns);
        assertEquals("8 key fix 5 R1 rows", 2, params.mNumRows);
        assertEquals("8 key fix 5 R1 left", 4, params.mLeftKeys);
        assertEquals("8 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("8 key fix 5 R1 [1]", -4, params.getColumnPos(0));
        assertEquals("8 key fix 5 R1 [2]", -3, params.getColumnPos(1));
        assertEquals("8 key fix 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("8 key fix 5 R1 [4]", -1, params.getColumnPos(3));
        assertEquals("8 key fix 5 R1 <5>", 0, params.getColumnPos(4));
        assertEquals("8 key fix 5 R1 [6]", -2, params.getColumnPos(5));
        assertEquals("8 key fix 5 R1 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key fix 5 R1 [8]", 0, params.getColumnPos(7));
        assertEquals("8 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //         [6] [7] [8] ___|
    // [1] [2] [3] <4> [5] ___|
    @Test
    public void testLayout8KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_R2);
        assertEquals("8 key fix 5 R2 columns", 5, params.mNumColumns);
        assertEquals("8 key fix 5 R2 rows", 2, params.mNumRows);
        assertEquals("8 key fix 5 R2 left", 3, params.mLeftKeys);
        assertEquals("8 key fix 5 R2 right", 2, params.mRightKeys);
        assertEquals("8 key fix 5 R2 [1]", -3, params.getColumnPos(0));
        assertEquals("8 key fix 5 R2 [2]", -2, params.getColumnPos(1));
        assertEquals("8 key fix 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key fix 5 R2 <4>", 0, params.getColumnPos(3));
        assertEquals("8 key fix 5 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("8 key fix 5 R2 [6]", -1, params.getColumnPos(5));
        assertEquals("8 key fix 5 R2 [7]", 0, params.getColumnPos(6));
        assertEquals("8 key fix 5 R2 [8]", 1, params.getColumnPos(7));
        assertEquals("8 key fix 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key fix 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //   [6] [7] [8] [9]
    // [1] [2] <3> [4] [5]
    @Test
    public void testLayout9KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_M0);
        assertEquals("9 key fix 5 M0 columns", 5, params.mNumColumns);
        assertEquals("9 key fix 5 M0 rows", 2, params.mNumRows);
        assertEquals("9 key fix 5 M0 left", 2, params.mLeftKeys);
        assertEquals("9 key fix 5 M0 right", 3, params.mRightKeys);
        assertEquals("9 key fix 5 M0 [1]", -2, params.getColumnPos(0));
        assertEquals("9 key fix 5 M0 [2]", -1, params.getColumnPos(1));
        assertEquals("9 key fix 5 M0 <3>", 0, params.getColumnPos(2));
        assertEquals("9 key fix 5 M0 [4]", 1, params.getColumnPos(3));
        assertEquals("9 key fix 5 M0 [5]", 2, params.getColumnPos(4));
        assertEquals("9 key fix 5 M0 [6]", -1, params.getColumnPos(5));
        assertEquals("9 key fix 5 M0 [7]", 0, params.getColumnPos(6));
        assertEquals("9 key fix 5 M0 [8]", 1, params.getColumnPos(7));
        assertEquals("9 key fix 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("9 key fix 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key fix 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8] [9]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout9KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_L0);
        assertEquals("9 key fix 5 L0 columns", 5, params.mNumColumns);
        assertEquals("9 key fix 5 L0 rows", 2, params.mNumRows);
        assertEquals("9 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("9 key fix 5 L0 right", 5, params.mRightKeys);
        assertEquals("9 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key fix 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key fix 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("9 key fix 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("9 key fix 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("9 key fix 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key fix 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key fix 5 L0 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key fix 5 L0 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8] [9]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout9KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_L1);
        assertEquals("9 key fix 5 L1 columns", 5, params.mNumColumns);
        assertEquals("9 key fix 5 L1 rows", 2, params.mNumRows);
        assertEquals("9 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("9 key fix 5 L1 right", 5, params.mRightKeys);
        assertEquals("9 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key fix 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key fix 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("9 key fix 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("9 key fix 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("9 key fix 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key fix 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key fix 5 L1 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key fix 5 L1 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key fix 5 L1 adjust",0, params.mTopRowAdjustment);
        assertEquals("9 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [6] [7] [8] [9]
    // |___ [1] <2> [3] [4] [5]
    @Test
    public void testLayout9KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_L2);
        assertEquals("9 key fix 5 L2 columns", 5, params.mNumColumns);
        assertEquals("9 key fix 5 L2 rows", 2, params.mNumRows);
        assertEquals("9 key fix 5 L2 left", 1, params.mLeftKeys);
        assertEquals("9 key fix 5 L2 right", 4, params.mRightKeys);
        assertEquals("9 key fix 5 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("9 key fix 5 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("9 key fix 5 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("9 key fix 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("9 key fix 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("9 key fix 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key fix 5 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key fix 5 L2 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key fix 5 L2 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key fix 5 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key fix 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [6] [7] [8] [9]|
    // [1] [2] [3] [4] <5>|
    @Test
    public void testLayout9KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_R0);
        assertEquals("9 key fix 5 R0 columns", 5, params.mNumColumns);
        assertEquals("9 key fix 5 R0 rows", 2, params.mNumRows);
        assertEquals("9 key fix 5 R0 left", 4, params.mLeftKeys);
        assertEquals("9 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("9 key fix 5 R0 [1]", -4, params.getColumnPos(0));
        assertEquals("9 key fix 5 R0 [2]", -3, params.getColumnPos(1));
        assertEquals("9 key fix 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("9 key fix 5 R0 [4]", -1, params.getColumnPos(3));
        assertEquals("9 key fix 5 R0 <5>", 0, params.getColumnPos(4));
        assertEquals("9 key fix 5 R0 [6]", -3, params.getColumnPos(5));
        assertEquals("9 key fix 5 R0 [7]", -2, params.getColumnPos(6));
        assertEquals("9 key fix 5 R0 [8]", -1, params.getColumnPos(7));
        assertEquals("9 key fix 5 R0 [9]", 0, params.getColumnPos(8));
        assertEquals("9 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key fix 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //     [6] [7] [8] [9] ___|
    // [1] [2] [3] [4] <5> ___|
    @Test
    public void testLayout9KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_R1);
        assertEquals("9 key fix 5 R1 columns", 5, params.mNumColumns);
        assertEquals("9 key fix 5 R1 rows", 2, params.mNumRows);
        assertEquals("9 key fix 5 R1 left", 4, params.mLeftKeys);
        assertEquals("9 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("9 key fix 5 R1 [1]", -4, params.getColumnPos(0));
        assertEquals("9 key fix 5 R1 [2]", -3, params.getColumnPos(1));
        assertEquals("9 key fix 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("9 key fix 5 R1 [4]", -1, params.getColumnPos(3));
        assertEquals("9 key fix 5 R1 <5>", 0, params.getColumnPos(4));
        assertEquals("9 key fix 5 R1 [6]", -3, params.getColumnPos(5));
        assertEquals("9 key fix 5 R1 [7]", -2, params.getColumnPos(6));
        assertEquals("9 key fix 5 R1 [8]", -1, params.getColumnPos(7));
        assertEquals("9 key fix 5 R1 [9]", 0, params.getColumnPos(8));
        assertEquals("9 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key fix 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //   [6] [7] [8] [9]  ___|
    // [1] [2] [3] <4> [5] ___|
    @Test
    public void testLayout9KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_R2);
        assertEquals("9 key fix 5 R2 columns", 5, params.mNumColumns);
        assertEquals("9 key fix 5 R2 rows", 2, params.mNumRows);
        assertEquals("9 key fix 5 R2 left", 3, params.mLeftKeys);
        assertEquals("9 key fix 5 R2 right", 2, params.mRightKeys);
        assertEquals("9 key fix 5 R2 [1]", -3, params.getColumnPos(0));
        assertEquals("9 key fix 5 R2 [2]", -2, params.getColumnPos(1));
        assertEquals("9 key fix 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key fix 5 R2 <4>", 0, params.getColumnPos(3));
        assertEquals("9 key fix 5 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("9 key fix 5 R2 [6]", -2, params.getColumnPos(5));
        assertEquals("9 key fix 5 R2 [7]", -1, params.getColumnPos(6));
        assertEquals("9 key fix 5 R2 [8]", 0, params.getColumnPos(7));
        assertEquals("9 key fix 5 R2 [9]", 1, params.getColumnPos(8));
        assertEquals("9 key fix 5 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key fix 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [6] [7] [8] [9] [A]
    // [1] [2] <3> [4] [5]
    @Test
    public void testLayout10KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_M0);
        assertEquals("10 key fix 5 M0 columns", 5, params.mNumColumns);
        assertEquals("10 key fix 5 M0 rows", 2, params.mNumRows);
        assertEquals("10 key fix 5 M0 left", 2, params.mLeftKeys);
        assertEquals("10 key fix 5 M0 right", 3, params.mRightKeys);
        assertEquals("10 key fix 5 M0 [1]", -2, params.getColumnPos(0));
        assertEquals("10 key fix 5 M0 [2]", -1, params.getColumnPos(1));
        assertEquals("10 key fix 5 M0 <3>", 0, params.getColumnPos(2));
        assertEquals("10 key fix 5 M0 [4]", 1, params.getColumnPos(3));
        assertEquals("10 key fix 5 M0 [5]", 2, params.getColumnPos(4));
        assertEquals("10 key fix 5 M0 [6]", -2, params.getColumnPos(5));
        assertEquals("10 key fix 5 M0 [7]", -1, params.getColumnPos(6));
        assertEquals("10 key fix 5 M0 [8]", 0, params.getColumnPos(7));
        assertEquals("10 key fix 5 M0 [9]", 1, params.getColumnPos(8));
        assertEquals("10 key fix 5 M0 [A]", 2, params.getColumnPos(9));
        assertEquals("10 key fix 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key fix 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8] [9] [A]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout10KeyFix5L0() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_L0);
        assertEquals("10 key fix 5 L0 columns", 5, params.mNumColumns);
        assertEquals("10 key fix 5 L0 rows", 2, params.mNumRows);
        assertEquals("10 key fix 5 L0 left", 0, params.mLeftKeys);
        assertEquals("10 key fix 5 L0 right", 5, params.mRightKeys);
        assertEquals("10 key fix 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key fix 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key fix 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("10 key fix 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("10 key fix 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("10 key fix 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key fix 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key fix 5 L0 [8]", 2, params.getColumnPos(7));
        assertEquals("10 key fix 5 L0 [9]", 3, params.getColumnPos(8));
        assertEquals("10 key fix 5 L0 [A]", 4, params.getColumnPos(9));
        assertEquals("10 key fix 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key fix 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8] [9] [A]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout10KeyFix5L1() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_L1);
        assertEquals("10 key fix 5 L1 columns", 5, params.mNumColumns);
        assertEquals("10 key fix 5 L1 rows", 2, params.mNumRows);
        assertEquals("10 key fix 5 L1 left", 0, params.mLeftKeys);
        assertEquals("10 key fix 5 L1 right", 5, params.mRightKeys);
        assertEquals("10 key fix 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key fix 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key fix 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("10 key fix 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("10 key fix 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("10 key fix 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key fix 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key fix 5 L1 [8]", 2, params.getColumnPos(7));
        assertEquals("10 key fix 5 L1 [9]", 3, params.getColumnPos(8));
        assertEquals("10 key fix 5 L1 [A]", 4, params.getColumnPos(9));
        assertEquals("10 key fix 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key fix 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8] [9] [A]
    // |___ [1] <2> [3] [4] [5]
    @Test
    public void testLayout10KeyFix5L2() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_L2);
        assertEquals("10 key fix 5 L2 columns", 5, params.mNumColumns);
        assertEquals("10 key fix 5 L2 rows", 2, params.mNumRows);
        assertEquals("10 key fix 5 L2 left", 1, params.mLeftKeys);
        assertEquals("10 key fix 5 L2 right", 4, params.mRightKeys);
        assertEquals("10 key fix 5 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("10 key fix 5 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("10 key fix 5 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("10 key fix 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("10 key fix 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("10 key fix 5 L2 [6]", -1, params.getColumnPos(5));
        assertEquals("10 key fix 5 L2 [7]", 0, params.getColumnPos(6));
        assertEquals("10 key fix 5 L2 [8]", 1, params.getColumnPos(7));
        assertEquals("10 key fix 5 L2 [9]", 2, params.getColumnPos(8));
        assertEquals("10 key fix 5 L2 [A]", 3, params.getColumnPos(9));
        assertEquals("10 key fix 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key fix 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [6] [7] [8] [9] [A]|
    // [1] [2] [3] [4] <5>|
    @Test
    public void testLayout10KeyFix5R0() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_R0);
        assertEquals("10 key fix 5 R0 columns", 5, params.mNumColumns);
        assertEquals("10 key fix 5 R0 rows", 2, params.mNumRows);
        assertEquals("10 key fix 5 R0 left", 4, params.mLeftKeys);
        assertEquals("10 key fix 5 R0 right", 1, params.mRightKeys);
        assertEquals("10 key fix 5 R0 [1]", -4, params.getColumnPos(0));
        assertEquals("10 key fix 5 R0 [2]", -3, params.getColumnPos(1));
        assertEquals("10 key fix 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("10 key fix 5 R0 [4]", -1, params.getColumnPos(3));
        assertEquals("10 key fix 5 R0 <5>", 0, params.getColumnPos(4));
        assertEquals("10 key fix 5 R0 [6]", -4, params.getColumnPos(5));
        assertEquals("10 key fix 5 R0 [7]", -3, params.getColumnPos(6));
        assertEquals("10 key fix 5 R0 [8]", -2, params.getColumnPos(7));
        assertEquals("10 key fix 5 R0 [9]", -1, params.getColumnPos(8));
        assertEquals("10 key fix 5 R0 [A]", 0, params.getColumnPos(9));
        assertEquals("10 key fix 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key fix 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [6] [7] [8] [9] [A] ___|
    // [1] [2] [3] [4] <5> ___|
    @Test
    public void testLayout10KeyFix5R1() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_R1);
        assertEquals("10 key fix 5 R1 columns", 5, params.mNumColumns);
        assertEquals("10 key fix 5 R1 rows", 2, params.mNumRows);
        assertEquals("10 key fix 5 R1 left", 4, params.mLeftKeys);
        assertEquals("10 key fix 5 R1 right", 1, params.mRightKeys);
        assertEquals("10 key fix 5 R1 [1]", -4, params.getColumnPos(0));
        assertEquals("10 key fix 5 R1 [2]", -3, params.getColumnPos(1));
        assertEquals("10 key fix 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("10 key fix 5 R1 [4]", -1, params.getColumnPos(3));
        assertEquals("10 key fix 5 R1 <5>", 0, params.getColumnPos(4));
        assertEquals("10 key fix 5 R1 [6]", -4, params.getColumnPos(5));
        assertEquals("10 key fix 5 R1 [7]", -3, params.getColumnPos(6));
        assertEquals("10 key fix 5 R1 [8]", -2, params.getColumnPos(7));
        assertEquals("10 key fix 5 R1 [9]", -1, params.getColumnPos(8));
        assertEquals("10 key fix 5 R1 [A]", 0, params.getColumnPos(9));
        assertEquals("10 key fix 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key fix 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [6] [7] [8] [9] [A] ___|
    // [1] [2] [3] <4> [5] ___|
    @Test
    public void testLayout10KeyFix5R2() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_R2);
        assertEquals("10 key fix 5 R2 columns", 5, params.mNumColumns);
        assertEquals("10 key fix 5 R2 rows", 2, params.mNumRows);
        assertEquals("10 key fix 5 R2 left", 3, params.mLeftKeys);
        assertEquals("10 key fix 5 R2 right", 2, params.mRightKeys);
        assertEquals("10 key fix 5 R2 [1]", -3, params.getColumnPos(0));
        assertEquals("10 key fix 5 R2 [2]", -2, params.getColumnPos(1));
        assertEquals("10 key fix 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key fix 5 R2 <4>", 0, params.getColumnPos(3));
        assertEquals("10 key fix 5 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("10 key fix 5 R2 [6]", -3, params.getColumnPos(5));
        assertEquals("10 key fix 5 R2 [7]", -2, params.getColumnPos(6));
        assertEquals("10 key fix 5 R2 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key fix 5 R2 [9]", 0, params.getColumnPos(8));
        assertEquals("10 key fix 5 R2 [A]", 1, params.getColumnPos(9));
        assertEquals("10 key fix 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key fix 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //         [B]
    // [6] [7] [8] [9] [A]
    // [1] [2] <3> [4] [5]
    @Test
    public void testLayout11KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(11, 5, XPOS_M0);
        assertEquals("11 key fix 5 M0 columns", 5, params.mNumColumns);
        assertEquals("11 key fix 5 M0 rows", 3, params.mNumRows);
        assertEquals("11 key fix 5 M0 left", 2, params.mLeftKeys);
        assertEquals("11 key fix 5 M0 right", 3, params.mRightKeys);
        assertEquals("11 key fix 5 M0 [1]", -2, params.getColumnPos(0));
        assertEquals("11 key fix 5 M0 [2]", -1, params.getColumnPos(1));
        assertEquals("11 key fix 5 M0 <3>", 0, params.getColumnPos(2));
        assertEquals("11 key fix 5 M0 [4]", 1, params.getColumnPos(3));
        assertEquals("11 key fix 5 M0 [5]", 2, params.getColumnPos(4));
        assertEquals("11 key fix 5 M0 [6]", -2, params.getColumnPos(5));
        assertEquals("11 key fix 5 M0 [7]", -1, params.getColumnPos(6));
        assertEquals("11 key fix 5 M0 [8]", 0, params.getColumnPos(7));
        assertEquals("11 key fix 5 M0 [9]", 1, params.getColumnPos(8));
        assertEquals("11 key fix 5 M0 [A]", 2, params.getColumnPos(9));
        assertEquals("11 key fix 5 M0 [B]", 0, params.getColumnPos(10));
        assertEquals("11 key fix 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("11 key fix 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //       [B] [C]
    // [6] [7] [8] [9] [A]
    // [1] [2] <3> [4] [5]
    @Test
    public void testLayout12KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(12, 5, XPOS_M0);
        assertEquals("12 key fix 5 M0 columns", 5, params.mNumColumns);
        assertEquals("12 key fix 5 M0 rows", 3, params.mNumRows);
        assertEquals("12 key fix 5 M0 left", 2, params.mLeftKeys);
        assertEquals("12 key fix 5 M0 right", 3, params.mRightKeys);
        assertEquals("12 key fix 5 M0 [1]", -2, params.getColumnPos(0));
        assertEquals("12 key fix 5 M0 [2]", -1, params.getColumnPos(1));
        assertEquals("12 key fix 5 M0 <3>", 0, params.getColumnPos(2));
        assertEquals("12 key fix 5 M0 [4]", 1, params.getColumnPos(3));
        assertEquals("12 key fix 5 M0 [5]", 2, params.getColumnPos(4));
        assertEquals("12 key fix 5 M0 [6]", -2, params.getColumnPos(5));
        assertEquals("12 key fix 5 M0 [7]", -1, params.getColumnPos(6));
        assertEquals("12 key fix 5 M0 [8]", 0, params.getColumnPos(7));
        assertEquals("12 key fix 5 M0 [9]", 1, params.getColumnPos(8));
        assertEquals("12 key fix 5 M0 [A]", 2, params.getColumnPos(9));
        assertEquals("12 key fix 5 M0 [B]", 0, params.getColumnPos(10));
        assertEquals("12 key fix 5 M0 [C]", 1, params.getColumnPos(11));
        assertEquals("12 key fix 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("12 key fix 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [B] [C] [D]
    // [6] [7] [8] [9] [A]
    // [1] [2] <3> [4] [5]
    @Test
    public void testLayout13KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(13, 5, XPOS_M0);
        assertEquals("13 key fix 5 M0 columns", 5, params.mNumColumns);
        assertEquals("13 key fix 5 M0 rows", 3, params.mNumRows);
        assertEquals("13 key fix 5 M0 left", 2, params.mLeftKeys);
        assertEquals("13 key fix 5 M0 right", 3, params.mRightKeys);
        assertEquals("13 key fix 5 M0 [1]", -2, params.getColumnPos(0));
        assertEquals("13 key fix 5 M0 [2]", -1, params.getColumnPos(1));
        assertEquals("13 key fix 5 M0 <3>", 0, params.getColumnPos(2));
        assertEquals("13 key fix 5 M0 [4]", 1, params.getColumnPos(3));
        assertEquals("13 key fix 5 M0 [5]", 2, params.getColumnPos(4));
        assertEquals("13 key fix 5 M0 [6]", -2, params.getColumnPos(5));
        assertEquals("13 key fix 5 M0 [7]", -1, params.getColumnPos(6));
        assertEquals("13 key fix 5 M0 [8]", 0, params.getColumnPos(7));
        assertEquals("13 key fix 5 M0 [9]", 1, params.getColumnPos(8));
        assertEquals("13 key fix 5 M0 [A]", 2, params.getColumnPos(9));
        assertEquals("13 key fix 5 M0 [B]", -1, params.getColumnPos(10));
        assertEquals("13 key fix 5 M0 [C]", 0, params.getColumnPos(11));
        assertEquals("13 key fix 5 M0 [D]", 1, params.getColumnPos(12));
        assertEquals("13 key fix 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("13 key fix 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [B] [C] [D] [E]
    // [6] [7] [8] [9] [A]
    // [1] [2] <3> [4] [5]
    @Test
    public void testLayout14KeyFix5M0() {
        MoreKeysKeyboardParams params = createParams(14, 5, XPOS_M0);
        assertEquals("14 key fix 5 M0 columns", 5, params.mNumColumns);
        assertEquals("14 key fix 5 M0 rows", 3, params.mNumRows);
        assertEquals("14 key fix 5 M0 left", 2, params.mLeftKeys);
        assertEquals("14 key fix 5 M0 right", 3, params.mRightKeys);
        assertEquals("14 key fix 5 M0 [1]", -2, params.getColumnPos(0));
        assertEquals("14 key fix 5 M0 [2]", -1, params.getColumnPos(1));
        assertEquals("14 key fix 5 M0 <3>", 0, params.getColumnPos(2));
        assertEquals("14 key fix 5 M0 [4]", 1, params.getColumnPos(3));
        assertEquals("14 key fix 5 M0 [5]", 2, params.getColumnPos(4));
        assertEquals("14 key fix 5 M0 [6]", -2, params.getColumnPos(5));
        assertEquals("14 key fix 5 M0 [7]", -1, params.getColumnPos(6));
        assertEquals("14 key fix 5 M0 [8]", 0, params.getColumnPos(7));
        assertEquals("14 key fix 5 M0 [9]", 1, params.getColumnPos(8));
        assertEquals("14 key fix 5 M0 [A]", 2, params.getColumnPos(9));
        assertEquals("14 key fix 5 M0 [B]", -1, params.getColumnPos(10));
        assertEquals("14 key fix 5 M0 [C]", 0, params.getColumnPos(11));
        assertEquals("14 key fix 5 M0 [D]", 1, params.getColumnPos(12));
        assertEquals("14 key fix 5 M0 [E]", 2, params.getColumnPos(13));
        assertEquals("14 key fix 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("14 key fix 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4] [5] [6] [7]
    @Test
    public void testLayout7KeyFix7L0() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L0);
        assertEquals("7 key fix 7 L0 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 L0 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 L0 left", 0, params.mLeftKeys);
        assertEquals("7 key fix 7 L0 right", 7, params.mRightKeys);
        assertEquals("7 key fix 7 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key fix 7 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key fix 7 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key fix 7 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key fix 7 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key fix 7 L0 [6]", 5, params.getColumnPos(5));
        assertEquals("7 key fix 7 L0 [7]", 6, params.getColumnPos(6));
        assertEquals("7 key fix 7 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4] [5] [6] [7]
    @Test
    public void testLayout7KeyFix7L1() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L1);
        assertEquals("7 key fix 7 L1 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 L1 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 L1 left", 0, params.mLeftKeys);
        assertEquals("7 key fix 7 L1 right", 7, params.mRightKeys);
        assertEquals("7 key fix 7 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key fix 7 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key fix 7 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key fix 7 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key fix 7 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key fix 7 L1 [6]", 5, params.getColumnPos(5));
        assertEquals("7 key fix 7 L1 [7]", 6, params.getColumnPos(6));
        assertEquals("7 key fix 7 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [1] <2> [3] [4] [5] [6] [7]
    @Test
    public void testLayout7KeyFix7L2() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L2);
        assertEquals("7 key fix 7 L2 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 L2 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 L2 left", 1, params.mLeftKeys);
        assertEquals("7 key fix 7 L2 right", 6, params.mRightKeys);
        assertEquals("7 key fix 7 L2 [1]", -1, params.getColumnPos(0));
        assertEquals("7 key fix 7 L2 <2>", 0, params.getColumnPos(1));
        assertEquals("7 key fix 7 L2 [3]", 1, params.getColumnPos(2));
        assertEquals("7 key fix 7 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key fix 7 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("7 key fix 7 L2 [6]", 4, params.getColumnPos(5));
        assertEquals("7 key fix 7 L2 [7]", 5, params.getColumnPos(6));
        assertEquals("7 key fix 7 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |___ [1] [2] <3> [4] [5] [6] [7]
    @Test
    public void testLayout7KeyFix7L3() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L3);
        assertEquals("7 key fix 7 L3 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 L3 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 L3 left", 2, params.mLeftKeys);
        assertEquals("7 key fix 7 L3 right", 5, params.mRightKeys);
        assertEquals("7 key fix 7 L3 [1]", -2, params.getColumnPos(0));
        assertEquals("7 key fix 7 L3 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key fix 7 L3 <3>", 0, params.getColumnPos(2));
        assertEquals("7 key fix 7 L3 [4]", 1, params.getColumnPos(3));
        assertEquals("7 key fix 7 L3 [5]", 2, params.getColumnPos(4));
        assertEquals("7 key fix 7 L3 [6]", 3, params.getColumnPos(5));
        assertEquals("7 key fix 7 L3 [7]", 4, params.getColumnPos(6));
        assertEquals("7 key fix 7 L3 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 L3 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |___ [1] [2] [3] <4> [5] [6] [7] ___ ___|
    @Test
    public void testLayout7KeyFix7M0() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_M0);
        assertEquals("7 key fix 7 M0 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 M0 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 M0 left", 3, params.mLeftKeys);
        assertEquals("7 key fix 7 M0 right", 4, params.mRightKeys);
        assertEquals("7 key fix 7 M0 [1]", -3, params.getColumnPos(0));
        assertEquals("7 key fix 7 M0 [2]", -2, params.getColumnPos(1));
        assertEquals("7 key fix 7 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key fix 7 M0 <4>", 0, params.getColumnPos(3));
        assertEquals("7 key fix 7 M0 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key fix 7 M0 [6]", 2, params.getColumnPos(5));
        assertEquals("7 key fix 7 M0 [7]", 3, params.getColumnPos(6));
        assertEquals("7 key fix 7 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 M0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // |___ ___ [1] [2] [3] <4> [5] [6] [7] ___|
    @Test
    public void testLayout7KeyFix7M1() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_M1);
        assertEquals("7 key fix 7 M1 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 M1 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 M1 left", 3, params.mLeftKeys);
        assertEquals("7 key fix 7 M1 right", 4, params.mRightKeys);
        assertEquals("7 key fix 7 M1 [1]", -3, params.getColumnPos(0));
        assertEquals("7 key fix 7 M1 [2]", -2, params.getColumnPos(1));
        assertEquals("7 key fix 7 M1 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key fix 7 M1 <4>", 0, params.getColumnPos(3));
        assertEquals("7 key fix 7 M1 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key fix 7 M1 [6]", 2, params.getColumnPos(5));
        assertEquals("7 key fix 7 M1 [7]", 3, params.getColumnPos(6));
        assertEquals("7 key fix 7 M1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 M1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [1] [2] [3] [4] <5> [6] [7] ___|
    @Test
    public void testLayout7KeyFix7R3() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R3);
        assertEquals("7 key fix 7 R3 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 R3 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 R3 left", 4, params.mLeftKeys);
        assertEquals("7 key fix 7 R3 right", 3, params.mRightKeys);
        assertEquals("7 key fix 7 R3 [1]", -4, params.getColumnPos(0));
        assertEquals("7 key fix 7 R3 [2]", -3, params.getColumnPos(1));
        assertEquals("7 key fix 7 R3 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key fix 7 R3 [4]", -1, params.getColumnPos(3));
        assertEquals("7 key fix 7 R3 <5>", 0, params.getColumnPos(4));
        assertEquals("7 key fix 7 R3 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key fix 7 R3 [7]", 2, params.getColumnPos(6));
        assertEquals("7 key fix 7 R3 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 R3 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [1] [2] [3] [4] [5] <6> [7] ___|
    @Test
    public void testLayout7KeyFix7R2() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R2);
        assertEquals("7 key fix 7 R2 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 R2 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 R2 left", 5, params.mLeftKeys);
        assertEquals("7 key fix 7 R2 right", 2, params.mRightKeys);
        assertEquals("7 key fix 7 R2 [1]", -5, params.getColumnPos(0));
        assertEquals("7 key fix 7 R2 [2]", -4, params.getColumnPos(1));
        assertEquals("7 key fix 7 R2 [3]", -3, params.getColumnPos(2));
        assertEquals("7 key fix 7 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("7 key fix 7 R2 [5]", -1, params.getColumnPos(4));
        assertEquals("7 key fix 7 R2 <6>", 0, params.getColumnPos(5));
        assertEquals("7 key fix 7 R2 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key fix 7 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 R2 default", WIDTH * 5, params.getDefaultKeyCoordX());
    }

    // [1] [2] [3] [4] [5] [6] <7> ___|
    @Test
    public void testLayout7KeyFix7R1() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R1);
        assertEquals("7 key fix 7 R1 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 R1 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 R1 left", 6, params.mLeftKeys);
        assertEquals("7 key fix 7 R1 right", 1, params.mRightKeys);
        assertEquals("7 key fix 7 R1 [1]", -6, params.getColumnPos(0));
        assertEquals("7 key fix 7 R1 [2]", -5, params.getColumnPos(1));
        assertEquals("7 key fix 7 R1 [3]", -4, params.getColumnPos(2));
        assertEquals("7 key fix 7 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key fix 7 R1 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key fix 7 R1 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key fix 7 R1 <7>", 0, params.getColumnPos(6));
        assertEquals("7 key fix 7 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 R1 default", WIDTH * 6, params.getDefaultKeyCoordX());
    }

    // [1] [2] [3] [4] [5] [6] <7>|
    @Test
    public void testLayout7KeyFix7R0() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R0);
        assertEquals("7 key fix 7 R0 columns", 7, params.mNumColumns);
        assertEquals("7 key fix 7 R0 rows", 1, params.mNumRows);
        assertEquals("7 key fix 7 R0 left", 6, params.mLeftKeys);
        assertEquals("7 key fix 7 R0 right", 1, params.mRightKeys);
        assertEquals("7 key fix 7 R0 [1]", -6, params.getColumnPos(0));
        assertEquals("7 key fix 7 R0 [2]", -5, params.getColumnPos(1));
        assertEquals("7 key fix 7 R0 [3]", -4, params.getColumnPos(2));
        assertEquals("7 key fix 7 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key fix 7 R0 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key fix 7 R0 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key fix 7 R0 <7>", 0, params.getColumnPos(6));
        assertEquals("7 key fix 7 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key fix 7 R0 default", WIDTH * 6, params.getDefaultKeyCoordX());
    }
}
