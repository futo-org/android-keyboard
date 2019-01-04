/*
 * Copyright (C) 2014 The Android Open Source Project
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
public class MoreKeysKeyboardBuilderAutoOrderTests {
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
                true /* isMoreKeysFixedColumn */, false /* isMoreKeysFixedOrder */,
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
    public void testLayout1KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_M0);
        assertEquals("1 key auto 5 M0 columns", 1, params.mNumColumns);
        assertEquals("1 key auto 5 M0 rows", 1, params.mNumRows);
        assertEquals("1 key auto 5 M0 left", 0, params.mLeftKeys);
        assertEquals("1 key auto 5 M0 right", 1, params.mRightKeys);
        assertEquals("1 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key auto 5 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |<1>
    @Test
    public void testLayout1KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_L0);
        assertEquals("1 key auto 5 L0 columns", 1, params.mNumColumns);
        assertEquals("1 key auto 5 L0 rows", 1, params.mNumRows);
        assertEquals("1 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("1 key auto 5 L0 right", 1, params.mRightKeys);
        assertEquals("1 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1>
    @Test
    public void testLayout1KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_L1);
        assertEquals("1 key auto 5 L1 columns", 1, params.mNumColumns);
        assertEquals("1 key auto 5 L1 rows", 1, params.mNumRows);
        assertEquals("1 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("1 key auto 5 L1 right", 1, params.mRightKeys);
        assertEquals("1 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key auto 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ <1>
    @Test
    public void testLayout1KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_L2);
        assertEquals("1 key auto 5 L2 columns", 1, params.mNumColumns);
        assertEquals("1 key auto 5 L2 rows", 1, params.mNumRows);
        assertEquals("1 key auto 5 L2 left", 0, params.mLeftKeys);
        assertEquals("1 key auto 5 L2 right", 1, params.mRightKeys);
        assertEquals("1 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key auto 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key auto 5 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1>|
    @Test
    public void testLayout1KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_R0);
        assertEquals("1 key auto 5 R0 columns", 1, params.mNumColumns);
        assertEquals("1 key auto 5 R0 rows", 1, params.mNumRows);
        assertEquals("1 key auto 5 R0 left", 0, params.mLeftKeys);
        assertEquals("1 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("1 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key auto 5 R0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1> ___|
    @Test
    public void testLayout1KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_R1);
        assertEquals("1 key auto 5 R1 columns", 1, params.mNumColumns);
        assertEquals("1 key auto 5 R1 rows", 1, params.mNumRows);
        assertEquals("1 key auto 5 R1 left", 0, params.mLeftKeys);
        assertEquals("1 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("1 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key auto 5 R1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1> ___ ___|
    @Test
    public void testLayout1KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(1, 5, XPOS_R2);
        assertEquals("1 key auto 5 R2 columns", 1, params.mNumColumns);
        assertEquals("1 key auto 5 R2 rows", 1, params.mNumRows);
        assertEquals("1 key auto 5 R2 left", 0, params.mLeftKeys);
        assertEquals("1 key auto 5 R2 right", 1, params.mRightKeys);
        assertEquals("1 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("1 key auto 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key auto 5 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // <1> [2]
    @Test
    public void testLayout2KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_M0);
        assertEquals("2 key auto 5 M0 columns", 2, params.mNumColumns);
        assertEquals("2 key auto 5 M0 rows", 1, params.mNumRows);
        assertEquals("2 key auto 5 M0 left", 0, params.mLeftKeys);
        assertEquals("2 key auto 5 M0 right", 2, params.mRightKeys);
        assertEquals("2 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key auto 5 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |<1> [2]
    @Test
    public void testLayout2KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_L0);
        assertEquals("2 key auto 5 L0 columns", 2, params.mNumColumns);
        assertEquals("2 key auto 5 L0 rows", 1, params.mNumRows);
        assertEquals("2 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("2 key auto 5 L0 right", 2, params.mRightKeys);
        assertEquals("2 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key auto 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2]
    @Test
    public void testLayout2KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_L1);
        assertEquals("2 key auto 5 L1 columns", 2, params.mNumColumns);
        assertEquals("2 key auto 5 L1 rows", 1, params.mNumRows);
        assertEquals("2 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("2 key auto 5 L1 right", 2, params.mRightKeys);
        assertEquals("2 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key auto 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key auto 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ <1> [2]
    @Test
    public void testLayout2KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_L2);
        assertEquals("2 key auto 5 L2 columns", 2, params.mNumColumns);
        assertEquals("2 key auto 5 L2 rows", 1, params.mNumRows);
        assertEquals("2 key auto 5 L2 left", 0, params.mLeftKeys);
        assertEquals("2 key auto 5 L2 right", 2, params.mRightKeys);
        assertEquals("2 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key auto 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key auto 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key auto 5 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [2] <1>|
    @Test
    public void testLayout2KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_R0);
        assertEquals("2 key auto 5 R0 columns", 2, params.mNumColumns);
        assertEquals("2 key auto 5 R0 rows", 1, params.mNumRows);
        assertEquals("2 key auto 5 R0 left", 1, params.mLeftKeys);
        assertEquals("2 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("2 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key auto 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("2 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key auto 5 R0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [2] <1> ___|
    @Test
    public void testLayout2KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_R1);
        assertEquals("2 key auto 5 R1 columns", 2, params.mNumColumns);
        assertEquals("2 key auto 5 R1 rows", 1, params.mNumRows);
        assertEquals("2 key auto 5 R1 left", 1, params.mLeftKeys);
        assertEquals("2 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("2 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key auto 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("2 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key auto 5 R1 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // <1> [2] ___|
    @Test
    public void testLayout2KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(2, 5, XPOS_R2);
        assertEquals("2 key auto 5 R2 columns", 2, params.mNumColumns);
        assertEquals("2 key auto 5 R2 rows", 1, params.mNumRows);
        assertEquals("2 key auto 5 R2 left", 0, params.mLeftKeys);
        assertEquals("2 key auto 5 R2 right", 2, params.mRightKeys);
        assertEquals("2 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("2 key auto 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key auto 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key auto 5 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [3] <1> [2]
    @Test
    public void testLayout3KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_M0);
        assertEquals("3 key auto 5 M0 columns", 3, params.mNumColumns);
        assertEquals("3 key auto 5 M0 rows", 1, params.mNumRows);
        assertEquals("3 key auto 5 M0 left", 1, params.mLeftKeys);
        assertEquals("3 key auto 5 M0 right", 2, params.mRightKeys);
        assertEquals("3 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 5 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3]
    @Test
    public void testLayout3KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_L0);
        assertEquals("3 key auto 5 L0 columns", 3, params.mNumColumns);
        assertEquals("3 key auto 5 L0 rows", 1, params.mNumRows);
        assertEquals("3 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("3 key auto 5 L0 right", 3, params.mRightKeys);
        assertEquals("3 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("3 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3]
    @Test
    public void testLayout3KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_L1);
        assertEquals("3 key auto 5 L1 columns", 3, params.mNumColumns);
        assertEquals("3 key auto 5 L1 rows", 1, params.mNumRows);
        assertEquals("3 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("3 key auto 5 L1 right", 3, params.mRightKeys);
        assertEquals("3 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("3 key auto 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2]
    @Test
    public void testLayout3KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_L2);
        assertEquals("3 key auto 5 L2 columns", 3, params.mNumColumns);
        assertEquals("3 key auto 5 L2 rows", 1, params.mNumRows);
        assertEquals("3 key auto 5 L2 left", 1, params.mLeftKeys);
        assertEquals("3 key auto 5 L2 right", 2, params.mRightKeys);
        assertEquals("3 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key auto 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3] [2] <1>|
    @Test
    public void testLayout3KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_R0);
        assertEquals("3 key auto 5 R0 columns", 3, params.mNumColumns);
        assertEquals("3 key auto 5 R0 rows", 1, params.mNumRows);
        assertEquals("3 key auto 5 R0 left", 2, params.mLeftKeys);
        assertEquals("3 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("3 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key auto 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("3 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 5 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [3] [2] <1> ___|
    @Test
    public void testLayout3KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_R1);
        assertEquals("3 key auto 5 R1 columns", 3, params.mNumColumns);
        assertEquals("3 key auto 5 R1 rows", 1, params.mNumRows);
        assertEquals("3 key auto 5 R1 left", 2, params.mLeftKeys);
        assertEquals("3 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("3 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key auto 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("3 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 5 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [3] <1> [2] ___|
    @Test
    public void testLayout3KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(3, 5, XPOS_R2);
        assertEquals("3 key auto 5 R2 columns", 3, params.mNumColumns);
        assertEquals("3 key auto 5 R2 rows", 1, params.mNumRows);
        assertEquals("3 key auto 5 R2 left", 1, params.mLeftKeys);
        assertEquals("3 key auto 5 R2 right", 2, params.mRightKeys);
        assertEquals("3 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key auto 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 5 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3]
    // <1> [2]
    @Test
    public void testLayout3KeyAuto2M0() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_M0);
        assertEquals("3 key auto 2 M0 columns", 2, params.mNumColumns);
        assertEquals("3 key auto 2 M0 rows", 2, params.mNumRows);
        assertEquals("3 key auto 2 M0 left", 0, params.mLeftKeys);
        assertEquals("3 key auto 2 M0 right", 2, params.mRightKeys);
        assertEquals("3 key auto 2 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 2 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 2 M0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key auto 2 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 2 M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |[3]
    // |<1> [2]
    @Test
    public void testLayout3KeyAuto2L0() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_L0);
        assertEquals("3 key auto 2 L0 columns", 2, params.mNumColumns);
        assertEquals("3 key auto 2 L0 rows", 2, params.mNumRows);
        assertEquals("3 key auto 2 L0 left", 0, params.mLeftKeys);
        assertEquals("3 key auto 2 L0 right", 2, params.mRightKeys);
        assertEquals("3 key auto 2 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 2 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 2 L0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key auto 2 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 2 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3]
    // |___ <1> [2]
    @Test
    public void testLayout3KeyAuto2L1() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_L1);
        assertEquals("3 key auto 2 L1 columns", 2, params.mNumColumns);
        assertEquals("3 key auto 2 L1 rows", 2, params.mNumRows);
        assertEquals("3 key auto 2 L1 left", 0, params.mLeftKeys);
        assertEquals("3 key auto 2 L1 right", 2, params.mRightKeys);
        assertEquals("3 key auto 2 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 2 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 2 L1 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key auto 2 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 2 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |        [3]
    // |___ ___ <1> [2]
    @Test
    public void testLayout3KeyAuto2L2() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_L2);
        assertEquals("3 key auto 2 L2 columns", 2, params.mNumColumns);
        assertEquals("3 key auto 2 L2 rows", 2, params.mNumRows);
        assertEquals("3 key auto 2 L2 left", 0, params.mLeftKeys);
        assertEquals("3 key auto 2 L2 right", 2, params.mRightKeys);
        assertEquals("3 key auto 2 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 2 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 2 L2 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key auto 2 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 2 L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    //     [3]|
    // [2] <1>|
    @Test
    public void testLayout3KeyAuto2R0() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_R0);
        assertEquals("3 key auto 2 R0 columns", 2, params.mNumColumns);
        assertEquals("3 key auto 2 R0 rows", 2, params.mNumRows);
        assertEquals("3 key auto 2 R0 left", 1, params.mLeftKeys);
        assertEquals("3 key auto 2 R0 right", 1, params.mRightKeys);
        assertEquals("3 key auto 2 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 2 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key auto 2 R0 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key auto 2 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 2 R0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [3]    |
    // [2] <1> ___|
    @Test
    public void testLayout3KeyAuto2R1() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_R1);
        assertEquals("3 key auto 2 R1 columns", 2, params.mNumColumns);
        assertEquals("3 key auto 2 R1 rows", 2, params.mNumRows);
        assertEquals("3 key auto 2 R1 left", 1, params.mLeftKeys);
        assertEquals("3 key auto 2 R1 right", 1, params.mRightKeys);
        assertEquals("3 key auto 2 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 2 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key auto 2 R1 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key auto 2 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 2 R1 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3]        |
    // <1> [2] ___|
    @Test
    public void testLayout3KeyAuto2R2() {
        MoreKeysKeyboardParams params = createParams(3, 2, XPOS_R2);
        assertEquals("3 key auto 2 R2 columns", 2, params.mNumColumns);
        assertEquals("3 key auto 2 R2 rows", 2, params.mNumRows);
        assertEquals("3 key auto 2 R2 left", 0, params.mLeftKeys);
        assertEquals("3 key auto 2 R2 right", 2, params.mRightKeys);
        assertEquals("3 key auto 2 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("3 key auto 2 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key auto 2 R2 [3]", 0, params.getColumnPos(2));
        assertEquals("3 key auto 2 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key auto 2 R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    //     [4]
    // [3] <1> [2]
    @Test
    public void testLayout4KeyAuto3M0() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_M0);
        assertEquals("4 key auto 3 M0 columns", 3, params.mNumColumns);
        assertEquals("4 key auto 3 M0 rows", 2, params.mNumRows);
        assertEquals("4 key auto 3 M0 left", 1, params.mLeftKeys);
        assertEquals("4 key auto 3 M0 right", 2, params.mRightKeys);
        assertEquals("4 key auto 3 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 3 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 3 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key auto 3 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key auto 3 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 3 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4]
    // |<1> [2] [3]
    @Test
    public void testLayout4KeyAuto3L0() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_L0);
        assertEquals("4 key auto 3 L0 columns", 3, params.mNumColumns);
        assertEquals("4 key auto 3 L0 rows", 2, params.mNumRows);
        assertEquals("4 key auto 3 L0 left", 0, params.mLeftKeys);
        assertEquals("4 key auto 3 L0 right", 3, params.mRightKeys);
        assertEquals("4 key auto 3 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 3 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 3 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key auto 3 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key auto 3 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 3 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4]
    // |___ <1> [2] [3]
    @Test
    public void testLayout4KeyAuto3L1() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_L1);
        assertEquals("4 key auto 3 L1 columns", 3, params.mNumColumns);
        assertEquals("4 key auto 3 L1 rows", 2, params.mNumRows);
        assertEquals("4 key auto 3 L1 left", 0, params.mLeftKeys);
        assertEquals("4 key auto 3 L1 right", 3, params.mRightKeys);
        assertEquals("4 key auto 3 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 3 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 3 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key auto 3 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key auto 3 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 3 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [4]
    // |___ [3] <1> [2]
    @Test
    public void testLayout4KeyAuto3L2() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_L2);
        assertEquals("4 key auto 3 L2 columns", 3, params.mNumColumns);
        assertEquals("4 key auto 3 L2 rows", 2, params.mNumRows);
        assertEquals("4 key auto 3 L2 left", 1, params.mLeftKeys);
        assertEquals("4 key auto 3 L2 right", 2, params.mRightKeys);
        assertEquals("4 key auto 3 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 3 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 3 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key auto 3 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key auto 3 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 3 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //         [4]|
    // [3] [2] <1>|
    @Test
    public void testLayout4KeyAuto3R0() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_R0);
        assertEquals("4 key auto 3 R0 columns", 3, params.mNumColumns);
        assertEquals("4 key auto 3 R0 rows", 2, params.mNumRows);
        assertEquals("4 key auto 3 R0 left", 2, params.mLeftKeys);
        assertEquals("4 key auto 3 R0 right", 1, params.mRightKeys);
        assertEquals("4 key auto 3 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 3 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key auto 3 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key auto 3 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key auto 3 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 3 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //         [4] ___|
    // [3] [2] <1> ___|
    @Test
    public void testLayout4KeyAuto3R1() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_R1);
        assertEquals("4 key auto 3 R1 columns", 3, params.mNumColumns);
        assertEquals("4 key auto 3 R1 rows", 2, params.mNumRows);
        assertEquals("4 key auto 3 R1 left", 2, params.mLeftKeys);
        assertEquals("4 key auto 3 R1 right", 1, params.mRightKeys);
        assertEquals("4 key auto 3 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 3 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key auto 3 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key auto 3 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key auto 3 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 3 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [4]     ___|
    // [3] <1> [2] ___|
    @Test
    public void testLayout4KeyAuto3R2() {
        MoreKeysKeyboardParams params = createParams(4, 3, XPOS_R2);
        assertEquals("4 key auto 3 R2 columns", 3, params.mNumColumns);
        assertEquals("4 key auto 3 R2 rows", 2, params.mNumRows);
        assertEquals("4 key auto 3 R2 left", 1, params.mLeftKeys);
        assertEquals("4 key auto 3 R2 right", 2, params.mRightKeys);
        assertEquals("4 key auto 3 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 3 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 3 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key auto 3 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("4 key auto 3 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 3 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3] <1> [2] [4]
    @Test
    public void testLayout4KeyAuto4M0() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_M0);
        assertEquals("4 key auto 4 M0 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 4 M0 rows", 1, params.mNumRows);
        assertEquals("4 key auto 4 M0 left", 1, params.mLeftKeys);
        assertEquals("4 key auto 4 M0 right", 3, params.mRightKeys);
        assertEquals("4 key auto 4 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 4 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 4 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key auto 4 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key auto 4 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4]
    @Test
    public void testLayout4KeyAuto4L0() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_L0);
        assertEquals("4 key auto 4 L0 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 4 L0 rows", 1, params.mNumRows);
        assertEquals("4 key auto 4 L0 left", 0, params.mLeftKeys);
        assertEquals("4 key auto 4 L0 right", 4, params.mRightKeys);
        assertEquals("4 key auto 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key auto 4 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key auto 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout4KeyAuto4L1() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_L1);
        assertEquals("4 key auto 4 L1 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 4 L1 rows", 1, params.mNumRows);
        assertEquals("4 key auto 4 L1 left", 0, params.mLeftKeys);
        assertEquals("4 key auto 4 L1 right", 4, params.mRightKeys);
        assertEquals("4 key auto 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key auto 4 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key auto 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2] [4]
    @Test
    public void testLayout4KeyAuto4L2() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_L2);
        assertEquals("4 key auto 4 L2 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 4 L2 rows", 1, params.mNumRows);
        assertEquals("4 key auto 4 L2 left", 1, params.mLeftKeys);
        assertEquals("4 key auto 4 L2 right", 3, params.mRightKeys);
        assertEquals("4 key auto 4 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 4 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 4 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key auto 4 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key auto 4 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] <1>|
    @Test
    public void testLayout4KeyAuto4R0() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_R0);
        assertEquals("4 key auto 4 R0 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 4 R0 rows", 1, params.mNumRows);
        assertEquals("4 key auto 4 R0 left", 3, params.mLeftKeys);
        assertEquals("4 key auto 4 R0 right", 1, params.mRightKeys);
        assertEquals("4 key auto 4 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 4 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key auto 4 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key auto 4 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key auto 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 4 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] <1> ___|
    @Test
    public void testLayout4KeyAuto4R1() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_R1);
        assertEquals("4 key auto 4 R1 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 4 R1 rows", 1, params.mNumRows);
        assertEquals("4 key auto 4 R1 left", 3, params.mLeftKeys);
        assertEquals("4 key auto 4 R1 right", 1, params.mRightKeys);
        assertEquals("4 key auto 4 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 4 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key auto 4 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key auto 4 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key auto 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 4 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] <1> [2] ___|
    @Test
    public void testLayout4KeyAuto4R2() {
        MoreKeysKeyboardParams params = createParams(4, 4, XPOS_R2);
        assertEquals("4 key auto 4 R2 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 4 R2 rows", 1, params.mNumRows);
        assertEquals("4 key auto 4 R2 left", 2, params.mLeftKeys);
        assertEquals("4 key auto 4 R2 right", 2, params.mRightKeys);
        assertEquals("4 key auto 4 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 4 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 4 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key auto 4 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("4 key auto 4 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 4 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [3] <1> [2] [4]
    @Test
    public void testLayout4KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_M0);
        assertEquals("4 key auto 5 M0 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 5 M0 rows", 1, params.mNumRows);
        assertEquals("4 key auto 5 M0 left", 1, params.mLeftKeys);
        assertEquals("4 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("4 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 5 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4]
    @Test
    public void testLayout4KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_L0);
        assertEquals("4 key auto 5 L0 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 5 L0 rows", 1, params.mNumRows);
        assertEquals("4 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("4 key auto 5 L0 right", 4, params.mRightKeys);
        assertEquals("4 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key auto 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout4KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_L1);
        assertEquals("4 key auto 5 L1 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 5 L1 rows", 1, params.mNumRows);
        assertEquals("4 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("4 key auto 5 L1 right", 4, params.mRightKeys);
        assertEquals("4 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key auto 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key auto 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2] [4]
    @Test
    public void testLayout4KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_L2);
        assertEquals("4 key auto 5 L2 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 5 L2 rows", 1, params.mNumRows);
        assertEquals("4 key auto 5 L2 left", 1, params.mLeftKeys);
        assertEquals("4 key auto 5 L2 right", 3, params.mRightKeys);
        assertEquals("4 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key auto 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key auto 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] <1>|
    @Test
    public void testLayout4KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_R0);
        assertEquals("4 key auto 5 R0 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 5 R0 rows", 1, params.mNumRows);
        assertEquals("4 key auto 5 R0 left", 3, params.mLeftKeys);
        assertEquals("4 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("4 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key auto 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key auto 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 5 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] <1> ___|
    @Test
    public void testLayout4KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_R1);
        assertEquals("4 key auto 5 R1 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 5 R1 rows", 1, params.mNumRows);
        assertEquals("4 key auto 5 R1 left", 3, params.mLeftKeys);
        assertEquals("4 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("4 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key auto 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key auto 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 5 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] <1> [2] ___|
    @Test
    public void testLayout4KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(4, 5, XPOS_R2);
        assertEquals("4 key auto 5 R2 columns", 4, params.mNumColumns);
        assertEquals("4 key auto 5 R2 rows", 1, params.mNumRows);
        assertEquals("4 key auto 5 R2 left", 2, params.mLeftKeys);
        assertEquals("4 key auto 5 R2 right", 2, params.mRightKeys);
        assertEquals("4 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("4 key auto 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key auto 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key auto 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("4 key auto 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key auto 5 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [4] [5]
    // [3] <1> [2]
    @Test
    public void testLayout5KeyAuto3M0() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_M0);
        assertEquals("5 key auto 3 M0 columns", 3, params.mNumColumns);
        assertEquals("5 key auto 3 M0 rows", 2, params.mNumRows);
        assertEquals("5 key auto 3 M0 left", 1, params.mLeftKeys);
        assertEquals("5 key auto 3 M0 right", 2, params.mRightKeys);
        assertEquals("5 key auto 3 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 3 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 3 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key auto 3 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key auto 3 M0 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key auto 3 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key auto 3 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4] [5]
    // |<1> [2] [3]
    @Test
    public void testLayout5KeyAuto3L0() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_L0);
        assertEquals("5 key auto 3 L0 columns", 3, params.mNumColumns);
        assertEquals("5 key auto 3 L0 rows", 2, params.mNumRows);
        assertEquals("5 key auto 3 L0 left", 0, params.mLeftKeys);
        assertEquals("5 key auto 3 L0 right", 3, params.mRightKeys);
        assertEquals("5 key auto 3 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 3 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 3 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key auto 3 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key auto 3 L0 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key auto 3 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 3 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4] [5]
    // |___ <1> [2] [3]
    @Test
    public void testLayout5KeyAuto3L1() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_L1);
        assertEquals("5 key auto 3 L1 columns", 3, params.mNumColumns);
        assertEquals("5 key auto 3 L1 rows", 2, params.mNumRows);
        assertEquals("5 key auto 3 L1 left", 0, params.mLeftKeys);
        assertEquals("5 key auto 3 L1 right", 3, params.mRightKeys);
        assertEquals("5 key auto 3 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 3 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 3 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key auto 3 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key auto 3 L1 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key auto 3 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 3 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [4] [5]
    // |___ [3] <1> [2]
    @Test
    public void testLayout5KeyAuto3L2() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_L2);
        assertEquals("5 key auto 3 L2 columns", 3, params.mNumColumns);
        assertEquals("5 key auto 3 L2 rows", 2, params.mNumRows);
        assertEquals("5 key auto 3 L2 left", 1, params.mLeftKeys);
        assertEquals("5 key auto 3 L2 right", 2, params.mRightKeys);
        assertEquals("5 key auto 3 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 3 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 3 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key auto 3 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key auto 3 L2 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key auto 3 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key auto 3 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [5] [4]|
    // [3] [2] <1>|
    @Test
    public void testLayout5KeyAuto3R0() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_R0);
        assertEquals("5 key auto 3 R0 columns", 3, params.mNumColumns);
        assertEquals("5 key auto 3 R0 rows", 2, params.mNumRows);
        assertEquals("5 key auto 3 R0 left", 2, params.mLeftKeys);
        assertEquals("5 key auto 3 R0 right", 1, params.mRightKeys);
        assertEquals("5 key auto 3 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 3 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key auto 3 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key auto 3 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key auto 3 R0 [5]", -1, params.getColumnPos(4));
        assertEquals("5 key auto 3 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 3 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [5] [4] ___|
    // [3] [2] <1> ___|
    @Test
    public void testLayout5KeyAuto3R1() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_R1);
        assertEquals("5 key auto 3 R1 columns", 3, params.mNumColumns);
        assertEquals("5 key auto 3 R1 rows", 2, params.mNumRows);
        assertEquals("5 key auto 3 R1 left", 2, params.mLeftKeys);
        assertEquals("5 key auto 3 R1 right", 1, params.mRightKeys);
        assertEquals("5 key auto 3 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 3 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key auto 3 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key auto 3 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key auto 3 R1 [5]", -1, params.getColumnPos(4));
        assertEquals("5 key auto 3 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 3 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [4] [5]   ___|
    // [3] <1> [2] ___|
    @Test
    public void testLayout5KeyAuto3R2() {
        MoreKeysKeyboardParams params = createParams(5, 3, XPOS_R2);
        assertEquals("5 key auto 3 R2 columns", 3, params.mNumColumns);
        assertEquals("5 key auto 3 R2 rows", 2, params.mNumRows);
        assertEquals("5 key auto 3 R2 left", 1, params.mLeftKeys);
        assertEquals("5 key auto 3 R2 right", 2, params.mRightKeys);
        assertEquals("5 key auto 3 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 3 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 3 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key auto 3 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("5 key auto 3 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("5 key auto 3 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("5 key auto 3 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [5]
    // [3] <1> [2] [4]
    @Test
    public void testLayout5KeyAuto4M0() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_M0);
        assertEquals("5 key auto 4 M0 columns", 4, params.mNumColumns);
        assertEquals("5 key auto 4 M0 rows", 2, params.mNumRows);
        assertEquals("5 key auto 4 M0 left", 1, params.mLeftKeys);
        assertEquals("5 key auto 4 M0 right", 3, params.mRightKeys);
        assertEquals("5 key auto 4 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 4 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 4 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key auto 4 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key auto 4 M0 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key auto 4 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5]
    // |<1> [2] [3] [4]
    @Test
    public void testLayout5KeyAuto4L0() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_L0);
        assertEquals("5 key auto 4 L0 columns", 4, params.mNumColumns);
        assertEquals("5 key auto 4 L0 rows", 2, params.mNumRows);
        assertEquals("5 key auto 4 L0 left", 0, params.mLeftKeys);
        assertEquals("5 key auto 4 L0 right", 4, params.mRightKeys);
        assertEquals("5 key auto 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key auto 4 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key auto 4 L0 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key auto 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5]
    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout5KeyAuto4L1() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_L1);
        assertEquals("5 key auto 4 L1 columns", 4, params.mNumColumns);
        assertEquals("5 key auto 4 L1 rows", 2, params.mNumRows);
        assertEquals("5 key auto 4 L1 left", 0, params.mLeftKeys);
        assertEquals("5 key auto 4 L1 right", 4, params.mRightKeys);
        assertEquals("5 key auto 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key auto 4 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key auto 4 L1 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key auto 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___     [5]
    // |___ [3] <1> [2] [4]
    @Test
    public void testLayout5KeyAuto4L2() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_L2);
        assertEquals("5 key auto 4 L2 columns", 4, params.mNumColumns);
        assertEquals("5 key auto 4 L2 rows", 2, params.mNumRows);
        assertEquals("5 key auto 4 L2 left", 1, params.mLeftKeys);
        assertEquals("5 key auto 4 L2 right", 3, params.mRightKeys);
        assertEquals("5 key auto 4 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 4 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 4 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key auto 4 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key auto 4 L2 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key auto 4 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //             [5]|
    // [4] [3] [2] <1>|
    @Test
    public void testLayout5KeyAuto4R0() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_R0);
        assertEquals("5 key auto 4 R0 columns", 4, params.mNumColumns);
        assertEquals("5 key auto 4 R0 rows", 2, params.mNumRows);
        assertEquals("5 key auto 4 R0 left", 3, params.mLeftKeys);
        assertEquals("5 key auto 4 R0 right", 1, params.mRightKeys);
        assertEquals("5 key auto 4 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 4 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key auto 4 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key auto 4 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("5 key auto 4 R0 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key auto 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 4 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //             [5] ___|
    // [4] [3] [2] <1> ___|
    @Test
    public void testLayout5KeyAuto4R1() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_R1);
        assertEquals("5 key auto 4 R1 columns", 4, params.mNumColumns);
        assertEquals("5 key auto 4 R1 rows", 2, params.mNumRows);
        assertEquals("5 key auto 4 R1 left", 3, params.mLeftKeys);
        assertEquals("5 key auto 4 R1 right", 1, params.mRightKeys);
        assertEquals("5 key auto 4 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 4 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key auto 4 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key auto 4 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("5 key auto 4 R1 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key auto 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 4 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //         [5]     ___|
    // [4] [3] <1> [2] ___|
    @Test
    public void testLayout5KeyAuto4R2() {
        MoreKeysKeyboardParams params = createParams(5, 4, XPOS_R2);
        assertEquals("5 key auto 4 R2 columns", 4, params.mNumColumns);
        assertEquals("5 key auto 4 R2 rows", 2, params.mNumRows);
        assertEquals("5 key auto 4 R2 left", 2, params.mLeftKeys);
        assertEquals("5 key auto 4 R2 right", 2, params.mRightKeys);
        assertEquals("5 key auto 4 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 4 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 4 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key auto 4 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("5 key auto 4 R2 [5]", 0, params.getColumnPos(4));
        assertEquals("5 key auto 4 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 4 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout5KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_M0);
        assertEquals("5 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("5 key auto 5 M0 rows", 1, params.mNumRows);
        assertEquals("5 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("5 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("5 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("5 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout5KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_L0);
        assertEquals("5 key auto 5 L0 columns", 5, params.mNumColumns);
        assertEquals("5 key auto 5 L0 rows", 1, params.mNumRows);
        assertEquals("5 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("5 key auto 5 L0 right", 5, params.mRightKeys);
        assertEquals("5 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key auto 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key auto 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("5 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout5KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_L1);
        assertEquals("5 key auto 5 L1 columns", 5, params.mNumColumns);
        assertEquals("5 key auto 5 L1 rows", 1, params.mNumRows);
        assertEquals("5 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("5 key auto 5 L1 right", 5, params.mRightKeys);
        assertEquals("5 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key auto 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key auto 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("5 key auto 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2] [4] [5]
    @Test
    public void testLayout5KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_L2);
        assertEquals("5 key auto 5 L2 columns", 5, params.mNumColumns);
        assertEquals("5 key auto 5 L2 rows", 1, params.mNumRows);
        assertEquals("5 key auto 5 L2 left", 1, params.mLeftKeys);
        assertEquals("5 key auto 5 L2 right", 4, params.mRightKeys);
        assertEquals("5 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key auto 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key auto 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("5 key auto 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [5] [4] [3] [2] <1>|
    @Test
    public void testLayout5KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_R0);
        assertEquals("5 key auto 5 R0 columns", 5, params.mNumColumns);
        assertEquals("5 key auto 5 R0 rows", 1, params.mNumRows);
        assertEquals("5 key auto 5 R0 left", 4, params.mLeftKeys);
        assertEquals("5 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("5 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key auto 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key auto 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("5 key auto 5 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("5 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout5KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_R1);
        assertEquals("5 key auto 5 R1 columns", 5, params.mNumColumns);
        assertEquals("5 key auto 5 R1 rows", 1, params.mNumRows);
        assertEquals("5 key auto 5 R1 left", 4, params.mLeftKeys);
        assertEquals("5 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("5 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key auto 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key auto 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("5 key auto 5 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("5 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout5KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(5, 5, XPOS_R2);
        assertEquals("5 key auto 5 R2 columns", 5, params.mNumColumns);
        assertEquals("5 key auto 5 R2 rows", 1, params.mNumRows);
        assertEquals("5 key auto 5 R2 left", 3, params.mLeftKeys);
        assertEquals("5 key auto 5 R2 right", 2, params.mRightKeys);
        assertEquals("5 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("5 key auto 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key auto 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key auto 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("5 key auto 5 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("5 key auto 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key auto 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //     [5] [6]
    // [3] <1> [2] [4]
    @Test
    public void testLayout6KeyAuto4M0() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_M0);
        assertEquals("6 key auto 4 M0 columns", 4, params.mNumColumns);
        assertEquals("6 key auto 4 M0 rows", 2, params.mNumRows);
        assertEquals("6 key auto 4 M0 left", 1, params.mLeftKeys);
        assertEquals("6 key auto 4 M0 right", 3, params.mRightKeys);
        assertEquals("6 key auto 4 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 4 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 4 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key auto 4 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("6 key auto 4 M0 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key auto 4 M0 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key auto 4 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 4 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5] [6]
    // |<1> [2] [3] [4]
    @Test
    public void testLayout6KeyAuto4L0() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_L0);
        assertEquals("6 key auto 4 L0 columns", 4, params.mNumColumns);
        assertEquals("6 key auto 4 L0 rows", 2, params.mNumRows);
        assertEquals("6 key auto 4 L0 left", 0, params.mLeftKeys);
        assertEquals("6 key auto 4 L0 right", 4, params.mRightKeys);
        assertEquals("6 key auto 4 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 4 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 4 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key auto 4 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("6 key auto 4 L0 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key auto 4 L0 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key auto 4 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 4 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6]
    // |___ <1> [2] [3] [4]
    @Test
    public void testLayout6KeyAuto4L1() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_L1);
        assertEquals("6 key auto 4 L1 columns", 4, params.mNumColumns);
        assertEquals("6 key auto 4 L1 rows", 2, params.mNumRows);
        assertEquals("6 key auto 4 L1 left", 0, params.mLeftKeys);
        assertEquals("6 key auto 4 L1 right", 4, params.mRightKeys);
        assertEquals("6 key auto 4 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 4 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 4 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key auto 4 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("6 key auto 4 L1 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key auto 4 L1 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key auto 4 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 4 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___     [5] [6]
    // |___ [3] <1> [2] [4]
    @Test
    public void testLayout6KeyAuto4L2() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_L2);
        assertEquals("6 key auto 4 L2 columns", 4, params.mNumColumns);
        assertEquals("6 key auto 4 L2 rows", 2, params.mNumRows);
        assertEquals("6 key auto 4 L2 left", 1, params.mLeftKeys);
        assertEquals("6 key auto 4 L2 right", 3, params.mRightKeys);
        assertEquals("6 key auto 4 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 4 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 4 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key auto 4 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("6 key auto 4 L2 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key auto 4 L2 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key auto 4 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 4 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //         [6] [5]|
    // [4] [3] [2] <1>|
    @Test
    public void testLayout6KeyAuto4R0() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_R0);
        assertEquals("6 key auto 4 R0 columns", 4, params.mNumColumns);
        assertEquals("6 key auto 4 R0 rows", 2, params.mNumRows);
        assertEquals("6 key auto 4 R0 left", 3, params.mLeftKeys);
        assertEquals("6 key auto 4 R0 right", 1, params.mRightKeys);
        assertEquals("6 key auto 4 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 4 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key auto 4 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key auto 4 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("6 key auto 4 R0 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key auto 4 R0 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key auto 4 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 4 R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //         [6] [5] ___|
    // [4] [3] [2] <1> ___|
    @Test
    public void testLayout6KeyAuto4R1() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_R1);
        assertEquals("6 key auto 4 R1 columns", 4, params.mNumColumns);
        assertEquals("6 key auto 4 R1 rows", 2, params.mNumRows);
        assertEquals("6 key auto 4 R1 left", 3, params.mLeftKeys);
        assertEquals("6 key auto 4 R1 right", 1, params.mRightKeys);
        assertEquals("6 key auto 4 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 4 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key auto 4 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key auto 4 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("6 key auto 4 R1 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key auto 4 R1 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key auto 4 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 4 R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //         [5] [6] ___|
    // [4] [3] <1> [2] ___|
    @Test
    public void testLayout6KeyAuto4R2() {
        MoreKeysKeyboardParams params = createParams(6, 4, XPOS_R2);
        assertEquals("6 key auto 4 R2 columns", 4, params.mNumColumns);
        assertEquals("6 key auto 4 R2 rows", 2, params.mNumRows);
        assertEquals("6 key auto 4 R2 left", 2, params.mLeftKeys);
        assertEquals("6 key auto 4 R2 right", 2, params.mRightKeys);
        assertEquals("6 key auto 4 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 4 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 4 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key auto 4 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("6 key auto 4 R2 [5]", 0, params.getColumnPos(4));
        assertEquals("6 key auto 4 R2 [6]", 1, params.getColumnPos(5));
        assertEquals("6 key auto 4 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 4 R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //         [6]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout6KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_M0);
        assertEquals("6 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("6 key auto 5 M0 rows", 2, params.mNumRows);
        assertEquals("6 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("6 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("6 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("6 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("6 key auto 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout6KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_L0);
        assertEquals("6 key auto 5 L0 columns", 5, params.mNumColumns);
        assertEquals("6 key auto 5 L0 rows", 2, params.mNumRows);
        assertEquals("6 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("6 key auto 5 L0 right", 5, params.mRightKeys);
        assertEquals("6 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key auto 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("6 key auto 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("6 key auto 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout6KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_L1);
        assertEquals("6 key auto 5 L1 columns", 5, params.mNumColumns);
        assertEquals("6 key auto 5 L1 rows", 2, params.mNumRows);
        assertEquals("6 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("6 key auto 5 L1 right", 5, params.mRightKeys);
        assertEquals("6 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key auto 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("6 key auto 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("6 key auto 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key auto 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___     [6]
    // |___ [3] <1> [2] [4] [5]
    @Test
    public void testLayout6KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_L2);
        assertEquals("6 key auto 5 L2 columns", 5, params.mNumColumns);
        assertEquals("6 key auto 5 L2 rows", 2, params.mNumRows);
        assertEquals("6 key auto 5 L2 left", 1, params.mLeftKeys);
        assertEquals("6 key auto 5 L2 right", 4, params.mRightKeys);
        assertEquals("6 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key auto 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("6 key auto 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("6 key auto 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key auto 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //                 [6]|
    // [5] [4] [3] [2] <1>|
    @Test
    public void testLayout6KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_R0);
        assertEquals("6 key auto 5 R0 columns", 5, params.mNumColumns);
        assertEquals("6 key auto 5 R0 rows", 2, params.mNumRows);
        assertEquals("6 key auto 5 R0 left", 4, params.mLeftKeys);
        assertEquals("6 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("6 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key auto 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key auto 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("6 key auto 5 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("6 key auto 5 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //                 [6] ___|
    // [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout6KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_R1);
        assertEquals("6 key auto 5 R1 columns", 5, params.mNumColumns);
        assertEquals("6 key auto 5 R1 rows", 2, params.mNumRows);
        assertEquals("6 key auto 5 R1 left", 4, params.mLeftKeys);
        assertEquals("6 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("6 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key auto 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key auto 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("6 key auto 5 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("6 key auto 5 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //             [6]     ___|
    // [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout6KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(6, 5, XPOS_R2);
        assertEquals("6 key auto 5 R2 columns", 5, params.mNumColumns);
        assertEquals("6 key auto 5 R2 rows", 2, params.mNumRows);
        assertEquals("6 key auto 5 R2 left", 3, params.mLeftKeys);
        assertEquals("6 key auto 5 R2 right", 2, params.mRightKeys);
        assertEquals("6 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("6 key auto 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key auto 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key auto 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("6 key auto 5 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("6 key auto 5 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("6 key auto 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key auto 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // |<1> [2] [3] [4] [5] [6] [7] ___ ___ ___|
    @Test
    public void testLayout7KeyAuto7L0() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L0);
        assertEquals("7 key auto 7 L0 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 L0 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 L0 left", 0, params.mLeftKeys);
        assertEquals("7 key auto 7 L0 right", 7, params.mRightKeys);
        assertEquals("7 key auto 7 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 7 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key auto 7 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key auto 7 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key auto 7 L0 [6]", 5, params.getColumnPos(5));
        assertEquals("7 key auto 7 L0 [7]", 6, params.getColumnPos(6));
        assertEquals("7 key auto 7 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ <1> [2] [3] [4] [5] [6] [7] ___ ___|
    @Test
    public void testLayout7KeyAuto7L1() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L1);
        assertEquals("7 key auto 7 L1 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 L1 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 L1 left", 0, params.mLeftKeys);
        assertEquals("7 key auto 7 L1 right", 7, params.mRightKeys);
        assertEquals("7 key auto 7 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 7 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key auto 7 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key auto 7 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key auto 7 L1 [6]", 5, params.getColumnPos(5));
        assertEquals("7 key auto 7 L1 [7]", 6, params.getColumnPos(6));
        assertEquals("7 key auto 7 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [3] <1> [2] [4] [5] [6] [7] ___ ___|
    @Test
    public void testLayout7KeyAuto7L2() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L2);
        assertEquals("7 key auto 7 L2 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 L2 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 L2 left", 1, params.mLeftKeys);
        assertEquals("7 key auto 7 L2 right", 6, params.mRightKeys);
        assertEquals("7 key auto 7 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 7 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 7 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key auto 7 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("7 key auto 7 L2 [6]", 4, params.getColumnPos(5));
        assertEquals("7 key auto 7 L2 [7]", 5, params.getColumnPos(6));
        assertEquals("7 key auto 7 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |___ [5] [3] <1> [2] [4] [6] [7] ___ ___|
    @Test
    public void testLayout7KeyAuto7L3() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_L3);
        assertEquals("7 key auto 7 L3 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 L3 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 L3 left", 2, params.mLeftKeys);
        assertEquals("7 key auto 7 L3 right", 5, params.mRightKeys);
        assertEquals("7 key auto 7 L3 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 L3 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 7 L3 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 7 L3 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key auto 7 L3 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key auto 7 L3 [6]", 3, params.getColumnPos(5));
        assertEquals("7 key auto 7 L3 [7]", 4, params.getColumnPos(6));
        assertEquals("7 key auto 7 L3 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 L3 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |___ [7] [5] [3] <1> [2] [4] [6] ___ ___|
    @Test
    public void testLayout7KeyAuto7M0() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_M0);
        assertEquals("7 key auto 7 M0 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 M0 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 M0 left", 3, params.mLeftKeys);
        assertEquals("7 key auto 7 M0 right", 4, params.mRightKeys);
        assertEquals("7 key auto 7 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 7 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 7 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key auto 7 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key auto 7 M0 [6]", 3, params.getColumnPos(5));
        assertEquals("7 key auto 7 M0 [7]", -3, params.getColumnPos(6));
        assertEquals("7 key auto 7 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 M0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [5] [3] <1> [2] [4] [6] ___|
    @Test
    public void testLayout7KeyAuto7M1() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_M1);
        assertEquals("7 key auto 7 M1 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 M1 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 M1 left", 3, params.mLeftKeys);
        assertEquals("7 key auto 7 M1 right", 4, params.mRightKeys);
        assertEquals("7 key auto 7 M1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 M1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 7 M1 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 7 M1 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key auto 7 M1 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key auto 7 M1 [6]", 3, params.getColumnPos(5));
        assertEquals("7 key auto 7 M1 [7]", -3, params.getColumnPos(6));
        assertEquals("7 key auto 7 M1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 M1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [6] [5] [3] <1> [2] [4] ___|
    @Test
    public void testLayout7KeyAuto7R3() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R3);
        assertEquals("7 key auto 7 R3 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 R3 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 R3 left", 4, params.mLeftKeys);
        assertEquals("7 key auto 7 R3 right", 3, params.mRightKeys);
        assertEquals("7 key auto 7 R3 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 R3 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 7 R3 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 7 R3 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key auto 7 R3 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key auto 7 R3 [6]", -3, params.getColumnPos(5));
        assertEquals("7 key auto 7 R3 [7]", -4, params.getColumnPos(6));
        assertEquals("7 key auto 7 R3 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 R3 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [6] [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout7KeyAuto7R2() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R2);
        assertEquals("7 key auto 7 R2 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 R2 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 R2 left", 5, params.mLeftKeys);
        assertEquals("7 key auto 7 R2 right", 2, params.mRightKeys);
        assertEquals("7 key auto 7 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 7 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 7 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("7 key auto 7 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("7 key auto 7 R2 [6]", -4, params.getColumnPos(5));
        assertEquals("7 key auto 7 R2 [7]", -5, params.getColumnPos(6));
        assertEquals("7 key auto 7 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 R2 default", WIDTH * 5, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [6] [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout7KeyAuto7R1() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R1);
        assertEquals("7 key auto 7 R1 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 R1 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 R1 left", 6, params.mLeftKeys);
        assertEquals("7 key auto 7 R1 right", 1, params.mRightKeys);
        assertEquals("7 key auto 7 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key auto 7 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key auto 7 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key auto 7 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("7 key auto 7 R1 [6]", -5, params.getColumnPos(5));
        assertEquals("7 key auto 7 R1 [7]", -6, params.getColumnPos(6));
        assertEquals("7 key auto 7 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 R1 default", WIDTH * 6, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [6] [5] [4] [3] [2] <1>|
    @Test
    public void testLayout7KeyAuto7R0() {
        MoreKeysKeyboardParams params = createParams(7, 7, XPOS_R0);
        assertEquals("7 key auto 7 R0 columns", 7, params.mNumColumns);
        assertEquals("7 key auto 7 R0 rows", 1, params.mNumRows);
        assertEquals("7 key auto 7 R0 left", 6, params.mLeftKeys);
        assertEquals("7 key auto 7 R0 right", 1, params.mRightKeys);
        assertEquals("7 key auto 7 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 7 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key auto 7 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key auto 7 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key auto 7 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("7 key auto 7 R0 [6]", -5, params.getColumnPos(5));
        assertEquals("7 key auto 7 R0 [7]", -6, params.getColumnPos(6));
        assertEquals("7 key auto 7 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 7 R0 default", WIDTH * 6, params.getDefaultKeyCoordX());
    }

    //       [6] [7]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout7KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_M0);
        assertEquals("7 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("7 key auto 5 M0 rows", 2, params.mNumRows);
        assertEquals("7 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("7 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("7 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key auto 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key auto 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key auto 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout7KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_L0);
        assertEquals("7 key auto 5 L0 columns", 5, params.mNumColumns);
        assertEquals("7 key auto 5 L0 rows", 2, params.mNumRows);
        assertEquals("7 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("7 key auto 5 L0 right", 5, params.mRightKeys);
        assertEquals("7 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key auto 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key auto 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key auto 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key auto 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout7KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_L1);
        assertEquals("7 key auto 5 L1 columns", 5, params.mNumColumns);
        assertEquals("7 key auto 5 L1 rows", 2, params.mNumRows);
        assertEquals("7 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("7 key auto 5 L1 right", 5, params.mRightKeys);
        assertEquals("7 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key auto 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key auto 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("7 key auto 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key auto 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key auto 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [6] [7]
    // |___ [3] <1> [2] [4] [5]
    @Test
    public void testLayout7KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_L2);
        assertEquals("7 key auto 5 L2 columns", 5, params.mNumColumns);
        assertEquals("7 key auto 5 L2 rows", 2, params.mNumRows);
        assertEquals("7 key auto 5 L2 left", 1, params.mLeftKeys);
        assertEquals("7 key auto 5 L2 right", 4, params.mRightKeys);
        assertEquals("7 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key auto 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("7 key auto 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key auto 5 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key auto 5 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key auto 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //             [7] [6]|
    // [5] [4] [3] [2] <1>|
    @Test
    public void testLayout7KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_R0);
        assertEquals("7 key auto 5 R0 columns", 5, params.mNumColumns);
        assertEquals("7 key auto 5 R0 rows", 2, params.mNumRows);
        assertEquals("7 key auto 5 R0 left", 4, params.mLeftKeys);
        assertEquals("7 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("7 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key auto 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key auto 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key auto 5 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("7 key auto 5 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key auto 5 R0 [7]", -1, params.getColumnPos(6));
        assertEquals("7 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //             [7] [6] ___|
    // [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout7KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_R1);
        assertEquals("7 key auto 5 R1 columns", 5, params.mNumColumns);
        assertEquals("7 key auto 5 R1 rows", 2, params.mNumRows);
        assertEquals("7 key auto 5 R1 left", 4, params.mLeftKeys);
        assertEquals("7 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("7 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key auto 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key auto 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key auto 5 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("7 key auto 5 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key auto 5 R1 [7]", -1, params.getColumnPos(6));
        assertEquals("7 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //           [6] [7]   ___|
    // [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout7KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(7, 5, XPOS_R2);
        assertEquals("7 key auto 5 R2 columns", 5, params.mNumColumns);
        assertEquals("7 key auto 5 R2 rows", 2, params.mNumRows);
        assertEquals("7 key auto 5 R2 left", 3, params.mLeftKeys);
        assertEquals("7 key auto 5 R2 right", 2, params.mRightKeys);
        assertEquals("7 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("7 key auto 5 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("7 key auto 5 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("7 key auto 5 R2 [7]", 1, params.getColumnPos(6));
        assertEquals("7 key auto 5 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key auto 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //     [7]
    // [6] [4] [5]
    // [3] <1> [2]
    @Test
    public void testLayout7KeyAuto3M0() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_M0);
        assertEquals("7 key auto 3 M0 columns", 3, params.mNumColumns);
        assertEquals("7 key auto 3 M0 rows", 3, params.mNumRows);
        assertEquals("7 key auto 3 M0 left", 1, params.mLeftKeys);
        assertEquals("7 key auto 3 M0 right", 2, params.mRightKeys);
        assertEquals("7 key auto 3 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 3 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 3 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 3 M0 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key auto 3 M0 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key auto 3 M0 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key auto 3 M0 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key auto 3 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 3 M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[7]
    // |[4] [5] [6]
    // |<1> [2] [3]
    @Test
    public void testLayout7KeyAuto3L0() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_L0);
        assertEquals("7 key auto 3 L0 columns", 3, params.mNumColumns);
        assertEquals("7 key auto 3 L0 rows", 3, params.mNumRows);
        assertEquals("7 key auto 3 L0 left", 0, params.mLeftKeys);
        assertEquals("7 key auto 3 L0 right", 3, params.mRightKeys);
        assertEquals("7 key auto 3 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 3 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 3 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key auto 3 L0 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key auto 3 L0 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key auto 3 L0 [6]", 2, params.getColumnPos(5));
        assertEquals("7 key auto 3 L0 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key auto 3 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 3 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [7]
    // |___ [4] [5] [6]
    // |___ <1> [2] [3]
    @Test
    public void testLayout7KeyAuto3L1() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_L1);
        assertEquals("7 key auto 3 L1 columns", 3, params.mNumColumns);
        assertEquals("7 key auto 3 L1 rows", 3, params.mNumRows);
        assertEquals("7 key auto 3 L1 left", 0, params.mLeftKeys);
        assertEquals("7 key auto 3 L1 right", 3, params.mRightKeys);
        assertEquals("7 key auto 3 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 3 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 3 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key auto 3 L1 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key auto 3 L1 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key auto 3 L1 [6]", 2, params.getColumnPos(5));
        assertEquals("7 key auto 3 L1 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key auto 3 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 3 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___     [7]
    // |___ [6] [4] [5]
    // |___ [3] <1> [2]
    @Test
    public void testLayout7KeyAuto3L2() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_L2);
        assertEquals("7 key auto 3 L2 columns", 3, params.mNumColumns);
        assertEquals("7 key auto 3 L2 rows", 3, params.mNumRows);
        assertEquals("7 key auto 3 L2 left", 1, params.mLeftKeys);
        assertEquals("7 key auto 3 L2 right", 2, params.mRightKeys);
        assertEquals("7 key auto 3 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 3 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 3 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 3 L2 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key auto 3 L2 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key auto 3 L2 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key auto 3 L2 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key auto 3 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 3 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //         [7]|
    // [6] [5] [4]|
    // [3] [2] <1>|
    @Test
    public void testLayout7KeyAuto3R0() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_R0);
        assertEquals("7 key auto 3 R0 columns", 3, params.mNumColumns);
        assertEquals("7 key auto 3 R0 rows", 3, params.mNumRows);
        assertEquals("7 key auto 3 R0 left", 2, params.mLeftKeys);
        assertEquals("7 key auto 3 R0 right", 1, params.mRightKeys);
        assertEquals("7 key auto 3 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 3 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key auto 3 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key auto 3 R0 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key auto 3 R0 [5]", -1, params.getColumnPos(4));
        assertEquals("7 key auto 3 R0 [6]", -2, params.getColumnPos(5));
        assertEquals("7 key auto 3 R0 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key auto 3 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 3 R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //         [7] ___|
    // [6] [5] [4] ___|
    // [3] [2] <1> ___|
    @Test
    public void testLayout7KeyAuto3R1() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_R1);
        assertEquals("7 key auto 3 R1 columns", 3, params.mNumColumns);
        assertEquals("7 key auto 3 R1 rows", 3, params.mNumRows);
        assertEquals("7 key auto 3 R1 left", 2, params.mLeftKeys);
        assertEquals("7 key auto 3 R1 right", 1, params.mRightKeys);
        assertEquals("7 key auto 3 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 3 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key auto 3 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key auto 3 R1 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key auto 3 R1 [5]", -1, params.getColumnPos(4));
        assertEquals("7 key auto 3 R1 [6]", -2, params.getColumnPos(5));
        assertEquals("7 key auto 3 R1 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key auto 3 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 3 R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [7]     ___|
    // [6] [4] [5] ___|
    // [3] <1> [2] ___|
    @Test
    public void testLayout7KeyAuto3R2() {
        MoreKeysKeyboardParams params = createParams(7, 3, XPOS_R2);
        assertEquals("7 key auto 3 R2 columns", 3, params.mNumColumns);
        assertEquals("7 key auto 3 R2 rows", 3, params.mNumRows);
        assertEquals("7 key auto 3 R2 left", 1, params.mLeftKeys);
        assertEquals("7 key auto 3 R2 right", 2, params.mRightKeys);
        assertEquals("7 key auto 3 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("7 key auto 3 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key auto 3 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key auto 3 R2 [4]", 0, params.getColumnPos(3));
        assertEquals("7 key auto 3 R2 [5]", 1, params.getColumnPos(4));
        assertEquals("7 key auto 3 R2 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key auto 3 R2 [7]", 0, params.getColumnPos(6));
        assertEquals("7 key auto 3 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key auto 3 R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [8] [6] [7]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout8KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_M0);
        assertEquals("8 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("8 key auto 5 M0 rows", 2, params.mNumRows);
        assertEquals("8 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("8 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("8 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("8 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("8 key auto 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key auto 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("8 key auto 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("8 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout8KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_L0);
        assertEquals("8 key auto 5 L0 columns", 5, params.mNumColumns);
        assertEquals("8 key auto 5 L0 rows", 2, params.mNumRows);
        assertEquals("8 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("8 key auto 5 L0 right", 5, params.mRightKeys);
        assertEquals("8 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key auto 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key auto 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key auto 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key auto 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("8 key auto 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key auto 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("8 key auto 5 L0 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout8KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_L1);
        assertEquals("8 key auto 5 L1 columns", 5, params.mNumColumns);
        assertEquals("8 key auto 5 L1 rows", 2, params.mNumRows);
        assertEquals("8 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("8 key auto 5 L1 right", 5, params.mRightKeys);
        assertEquals("8 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key auto 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key auto 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key auto 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key auto 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("8 key auto 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key auto 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("8 key auto 5 L1 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key auto 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [8] [6] [7]
    // |___ [3] <1> [2] [4] [5]
    @Test
    public void testLayout8KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_L2);
        assertEquals("8 key auto 5 L2 columns", 5, params.mNumColumns);
        assertEquals("8 key auto 5 L2 rows", 2, params.mNumRows);
        assertEquals("8 key auto 5 L2 left", 1, params.mLeftKeys);
        assertEquals("8 key auto 5 L2 right", 4, params.mRightKeys);
        assertEquals("8 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key auto 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key auto 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key auto 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("8 key auto 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("8 key auto 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key auto 5 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("8 key auto 5 L2 [8]", -1, params.getColumnPos(7));
        assertEquals("8 key auto 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key auto 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //         [8] [7] [6]|
    // [5] [4] [3] [2] <1>|
    @Test
    public void testLayout8KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_R0);
        assertEquals("8 key auto 5 R0 columns", 5, params.mNumColumns);
        assertEquals("8 key auto 5 R0 rows", 2, params.mNumRows);
        assertEquals("8 key auto 5 R0 left", 4, params.mLeftKeys);
        assertEquals("8 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("8 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key auto 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("8 key auto 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("8 key auto 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("8 key auto 5 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("8 key auto 5 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key auto 5 R0 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key auto 5 R0 [8]", -2, params.getColumnPos(7));
        assertEquals("8 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key auto 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //         [8] [7] [6] ___|
    // [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout8KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_R1);
        assertEquals("8 key auto 5 R1 columns", 5, params.mNumColumns);
        assertEquals("8 key auto 5 R1 rows", 2, params.mNumRows);
        assertEquals("8 key auto 5 R1 left", 4, params.mLeftKeys);
        assertEquals("8 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("8 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key auto 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("8 key auto 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("8 key auto 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("8 key auto 5 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("8 key auto 5 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key auto 5 R1 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key auto 5 R1 [8]", -2, params.getColumnPos(7));
        assertEquals("8 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key auto 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //         [8] [6] [7] ___|
    // [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout8KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(8, 5, XPOS_R2);
        assertEquals("8 key auto 5 R2 columns", 5, params.mNumColumns);
        assertEquals("8 key auto 5 R2 rows", 2, params.mNumRows);
        assertEquals("8 key auto 5 R2 left", 3, params.mLeftKeys);
        assertEquals("8 key auto 5 R2 right", 2, params.mRightKeys);
        assertEquals("8 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("8 key auto 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key auto 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key auto 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("8 key auto 5 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("8 key auto 5 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("8 key auto 5 R2 [7]", 1, params.getColumnPos(6));
        assertEquals("8 key auto 5 R2 [8]", -1, params.getColumnPos(7));
        assertEquals("8 key auto 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key auto 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //   [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout9KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_M0);
        assertEquals("9 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("9 key auto 5 M0 rows", 2, params.mNumRows);
        assertEquals("9 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("9 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("9 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("9 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("9 key auto 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key auto 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key auto 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("9 key auto 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("9 key auto 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8] [9]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout9KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_L0);
        assertEquals("9 key auto 5 L0 columns", 5, params.mNumColumns);
        assertEquals("9 key auto 5 L0 rows", 2, params.mNumRows);
        assertEquals("9 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("9 key auto 5 L0 right", 5, params.mRightKeys);
        assertEquals("9 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key auto 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key auto 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("9 key auto 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("9 key auto 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("9 key auto 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key auto 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key auto 5 L0 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key auto 5 L0 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8] [9]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout9KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_L1);
        assertEquals("9 key auto 5 L1 columns", 5, params.mNumColumns);
        assertEquals("9 key auto 5 L1 rows", 2, params.mNumRows);
        assertEquals("9 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("9 key auto 5 L1 right", 5, params.mRightKeys);
        assertEquals("9 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key auto 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key auto 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("9 key auto 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("9 key auto 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("9 key auto 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key auto 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key auto 5 L1 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key auto 5 L1 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key auto 5 L1 adjust",0, params.mTopRowAdjustment);
        assertEquals("9 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___   [6] [7] [8] [9]
    // |___ [3] <1> [2] [4] [5]
    @Test
    public void testLayout9KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_L2);
        assertEquals("9 key auto 5 L2 columns", 5, params.mNumColumns);
        assertEquals("9 key auto 5 L2 rows", 2, params.mNumRows);
        assertEquals("9 key auto 5 L2 left", 1, params.mLeftKeys);
        assertEquals("9 key auto 5 L2 right", 4, params.mRightKeys);
        assertEquals("9 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key auto 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key auto 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key auto 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("9 key auto 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("9 key auto 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key auto 5 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key auto 5 L2 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key auto 5 L2 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key auto 5 L2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key auto 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [9] [8] [7] [6]|
    // [5] [4] [3] [2] <1>|
    @Test
    public void testLayout9KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_R0);
        assertEquals("9 key auto 5 R0 columns", 5, params.mNumColumns);
        assertEquals("9 key auto 5 R0 rows", 2, params.mNumRows);
        assertEquals("9 key auto 5 R0 left", 4, params.mLeftKeys);
        assertEquals("9 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("9 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key auto 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("9 key auto 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("9 key auto 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("9 key auto 5 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("9 key auto 5 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key auto 5 R0 [7]", -1, params.getColumnPos(6));
        assertEquals("9 key auto 5 R0 [8]", -2, params.getColumnPos(7));
        assertEquals("9 key auto 5 R0 [9]", -3, params.getColumnPos(8));
        assertEquals("9 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key auto 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //     [9] [8] [7] [6] ___|
    // [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout9KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_R1);
        assertEquals("9 key auto 5 R1 columns", 5, params.mNumColumns);
        assertEquals("9 key auto 5 R1 rows", 2, params.mNumRows);
        assertEquals("9 key auto 5 R1 left", 4, params.mLeftKeys);
        assertEquals("9 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("9 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key auto 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("9 key auto 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("9 key auto 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("9 key auto 5 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("9 key auto 5 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key auto 5 R1 [7]", -1, params.getColumnPos(6));
        assertEquals("9 key auto 5 R1 [8]", -2, params.getColumnPos(7));
        assertEquals("9 key auto 5 R1 [9]", -3, params.getColumnPos(8));
        assertEquals("9 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key auto 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //   [9] [8] [6] [7]   ___|
    // [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout9KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(9, 5, XPOS_R2);
        assertEquals("9 key auto 5 R2 columns", 5, params.mNumColumns);
        assertEquals("9 key auto 5 R2 rows", 2, params.mNumRows);
        assertEquals("9 key auto 5 R2 left", 3, params.mLeftKeys);
        assertEquals("9 key auto 5 R2 right", 2, params.mRightKeys);
        assertEquals("9 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("9 key auto 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key auto 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key auto 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("9 key auto 5 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("9 key auto 5 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key auto 5 R2 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key auto 5 R2 [8]", -1, params.getColumnPos(7));
        assertEquals("9 key auto 5 R2 [9]", -2, params.getColumnPos(8));
        assertEquals("9 key auto 5 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key auto 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [A] [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout10KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_M0);
        assertEquals("10 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("10 key auto 5 M0 rows", 2, params.mNumRows);
        assertEquals("10 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("10 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("10 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("10 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("10 key auto 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key auto 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key auto 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key auto 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("10 key auto 5 M0 [A]", -2, params.getColumnPos(9));
        assertEquals("10 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8] [9] [A]
    // |<1> [2] [3] [4] [5]
    @Test
    public void testLayout10KeyAuto5L0() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_L0);
        assertEquals("10 key auto 5 L0 columns", 5, params.mNumColumns);
        assertEquals("10 key auto 5 L0 rows", 2, params.mNumRows);
        assertEquals("10 key auto 5 L0 left", 0, params.mLeftKeys);
        assertEquals("10 key auto 5 L0 right", 5, params.mRightKeys);
        assertEquals("10 key auto 5 L0 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key auto 5 L0 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key auto 5 L0 [3]", 2, params.getColumnPos(2));
        assertEquals("10 key auto 5 L0 [4]", 3, params.getColumnPos(3));
        assertEquals("10 key auto 5 L0 [5]", 4, params.getColumnPos(4));
        assertEquals("10 key auto 5 L0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key auto 5 L0 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key auto 5 L0 [8]", 2, params.getColumnPos(7));
        assertEquals("10 key auto 5 L0 [9]", 3, params.getColumnPos(8));
        assertEquals("10 key auto 5 L0 [A]", 4, params.getColumnPos(9));
        assertEquals("10 key auto 5 L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key auto 5 L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8] [9] [A]
    // |___ <1> [2] [3] [4] [5]
    @Test
    public void testLayout10KeyAuto5L1() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_L1);
        assertEquals("10 key auto 5 L1 columns", 5, params.mNumColumns);
        assertEquals("10 key auto 5 L1 rows", 2, params.mNumRows);
        assertEquals("10 key auto 5 L1 left", 0, params.mLeftKeys);
        assertEquals("10 key auto 5 L1 right", 5, params.mRightKeys);
        assertEquals("10 key auto 5 L1 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key auto 5 L1 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key auto 5 L1 [3]", 2, params.getColumnPos(2));
        assertEquals("10 key auto 5 L1 [4]", 3, params.getColumnPos(3));
        assertEquals("10 key auto 5 L1 [5]", 4, params.getColumnPos(4));
        assertEquals("10 key auto 5 L1 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key auto 5 L1 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key auto 5 L1 [8]", 2, params.getColumnPos(7));
        assertEquals("10 key auto 5 L1 [9]", 3, params.getColumnPos(8));
        assertEquals("10 key auto 5 L1 [A]", 4, params.getColumnPos(9));
        assertEquals("10 key auto 5 L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key auto 5 L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [8] [6] [7] [9] [A]
    // |___ [3] <1> [2] [4] [5]
    @Test
    public void testLayout10KeyAuto5L2() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_L2);
        assertEquals("10 key auto 5 L2 columns", 5, params.mNumColumns);
        assertEquals("10 key auto 5 L2 rows", 2, params.mNumRows);
        assertEquals("10 key auto 5 L2 left", 1, params.mLeftKeys);
        assertEquals("10 key auto 5 L2 right", 4, params.mRightKeys);
        assertEquals("10 key auto 5 L2 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key auto 5 L2 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key auto 5 L2 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key auto 5 L2 [4]", 2, params.getColumnPos(3));
        assertEquals("10 key auto 5 L2 [5]", 3, params.getColumnPos(4));
        assertEquals("10 key auto 5 L2 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key auto 5 L2 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key auto 5 L2 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key auto 5 L2 [9]", 2, params.getColumnPos(8));
        assertEquals("10 key auto 5 L2 [A]", 3, params.getColumnPos(9));
        assertEquals("10 key auto 5 L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key auto 5 L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [A] [9] [8] [7] [6]|
    // [5] [4] [3] [2] <1>|
    @Test
    public void testLayout10KeyAuto5R0() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_R0);
        assertEquals("10 key auto 5 R0 columns", 5, params.mNumColumns);
        assertEquals("10 key auto 5 R0 rows", 2, params.mNumRows);
        assertEquals("10 key auto 5 R0 left", 4, params.mLeftKeys);
        assertEquals("10 key auto 5 R0 right", 1, params.mRightKeys);
        assertEquals("10 key auto 5 R0 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key auto 5 R0 [2]", -1, params.getColumnPos(1));
        assertEquals("10 key auto 5 R0 [3]", -2, params.getColumnPos(2));
        assertEquals("10 key auto 5 R0 [4]", -3, params.getColumnPos(3));
        assertEquals("10 key auto 5 R0 [5]", -4, params.getColumnPos(4));
        assertEquals("10 key auto 5 R0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key auto 5 R0 [7]", -1, params.getColumnPos(6));
        assertEquals("10 key auto 5 R0 [8]", -2, params.getColumnPos(7));
        assertEquals("10 key auto 5 R0 [9]", -3, params.getColumnPos(8));
        assertEquals("10 key auto 5 R0 [A]", -4, params.getColumnPos(9));
        assertEquals("10 key auto 5 R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key auto 5 R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [A] [9] [8] [7] [6] ___|
    // [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout10KeyAuto5R1() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_R1);
        assertEquals("10 key auto 5 R1 columns", 5, params.mNumColumns);
        assertEquals("10 key auto 5 R1 rows", 2, params.mNumRows);
        assertEquals("10 key auto 5 R1 left", 4, params.mLeftKeys);
        assertEquals("10 key auto 5 R1 right", 1, params.mRightKeys);
        assertEquals("10 key auto 5 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key auto 5 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("10 key auto 5 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("10 key auto 5 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("10 key auto 5 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("10 key auto 5 R1 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key auto 5 R1 [7]", -1, params.getColumnPos(6));
        assertEquals("10 key auto 5 R1 [8]", -2, params.getColumnPos(7));
        assertEquals("10 key auto 5 R1 [9]", -3, params.getColumnPos(8));
        assertEquals("10 key auto 5 R1 [A]", -4, params.getColumnPos(9));
        assertEquals("10 key auto 5 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key auto 5 R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [A] [9] [8] [6] [7] ___|
    // [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout10KeyAuto5R2() {
        MoreKeysKeyboardParams params = createParams(10, 5, XPOS_R2);
        assertEquals("10 key auto 5 R2 columns", 5, params.mNumColumns);
        assertEquals("10 key auto 5 R2 rows", 2, params.mNumRows);
        assertEquals("10 key auto 5 R2 left", 3, params.mLeftKeys);
        assertEquals("10 key auto 5 R2 right", 2, params.mRightKeys);
        assertEquals("10 key auto 5 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("10 key auto 5 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key auto 5 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key auto 5 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("10 key auto 5 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("10 key auto 5 R2 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key auto 5 R2 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key auto 5 R2 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key auto 5 R2 [9]", -2, params.getColumnPos(8));
        assertEquals("10 key auto 5 R2 [A]", -3, params.getColumnPos(9));
        assertEquals("10 key auto 5 R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key auto 5 R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //         [B]
    // [A] [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout11KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(11, 5, XPOS_M0);
        assertEquals("11 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("11 key auto 5 M0 rows", 3, params.mNumRows);
        assertEquals("11 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("11 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("11 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("11 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("11 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("11 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("11 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("11 key auto 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("11 key auto 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("11 key auto 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("11 key auto 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("11 key auto 5 M0 [A]", -2, params.getColumnPos(9));
        assertEquals("11 key auto 5 M0 [B]", 0, params.getColumnPos(10));
        assertEquals("11 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("11 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //       [B] [C]
    // [A] [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout12KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(12, 5, XPOS_M0);
        assertEquals("12 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("12 key auto 5 M0 rows", 3, params.mNumRows);
        assertEquals("12 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("12 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("12 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("12 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("12 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("12 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("12 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("12 key auto 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("12 key auto 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("12 key auto 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("12 key auto 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("12 key auto 5 M0 [A]", -2, params.getColumnPos(9));
        assertEquals("12 key auto 5 M0 [B]", 0, params.getColumnPos(10));
        assertEquals("12 key auto 5 M0 [C]", 1, params.getColumnPos(11));
        assertEquals("12 key auto 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("12 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [D] [B] [C]
    // [A] [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout13KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(13, 5, XPOS_M0);
        assertEquals("13 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("13 key auto 5 M0 rows", 3, params.mNumRows);
        assertEquals("13 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("13 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("13 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("13 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("13 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("13 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("13 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("13 key auto 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("13 key auto 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("13 key auto 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("13 key auto 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("13 key auto 5 M0 [A]", -2, params.getColumnPos(9));
        assertEquals("13 key auto 5 M0 [B]", 0, params.getColumnPos(10));
        assertEquals("13 key auto 5 M0 [C]", 1, params.getColumnPos(11));
        assertEquals("13 key auto 5 M0 [D]", -1, params.getColumnPos(12));
        assertEquals("13 key auto 5 M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("13 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [D] [B] [C] [E]
    // [A] [8] [6] [7] [9]
    // [5] [3] <1> [2] [4]
    @Test
    public void testLayout14KeyAuto5M0() {
        MoreKeysKeyboardParams params = createParams(14, 5, XPOS_M0);
        assertEquals("13 key auto 5 M0 columns", 5, params.mNumColumns);
        assertEquals("13 key auto 5 M0 rows", 3, params.mNumRows);
        assertEquals("13 key auto 5 M0 left", 2, params.mLeftKeys);
        assertEquals("13 key auto 5 M0 right", 3, params.mRightKeys);
        assertEquals("13 key auto 5 M0 <1>", 0, params.getColumnPos(0));
        assertEquals("13 key auto 5 M0 [2]", 1, params.getColumnPos(1));
        assertEquals("13 key auto 5 M0 [3]", -1, params.getColumnPos(2));
        assertEquals("13 key auto 5 M0 [4]", 2, params.getColumnPos(3));
        assertEquals("13 key auto 5 M0 [5]", -2, params.getColumnPos(4));
        assertEquals("13 key auto 5 M0 [6]", 0, params.getColumnPos(5));
        assertEquals("13 key auto 5 M0 [7]", 1, params.getColumnPos(6));
        assertEquals("13 key auto 5 M0 [8]", -1, params.getColumnPos(7));
        assertEquals("13 key auto 5 M0 [9]", 2, params.getColumnPos(8));
        assertEquals("13 key auto 5 M0 [A]", -2, params.getColumnPos(9));
        assertEquals("13 key auto 5 M0 [B]", 0, params.getColumnPos(10));
        assertEquals("13 key auto 5 M0 [C]", 1, params.getColumnPos(11));
        assertEquals("13 key auto 5 M0 [D]", -1, params.getColumnPos(12));
        assertEquals("13 key auto 5 M0 [E]", 2, params.getColumnPos(13));
        assertEquals("13 key auto 5 M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("13 key auto 5 M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //                     [J] [I] [H] ___|
    // [G] [F] [E] [D] [C] [B] [A] [9] ___|
    // [8] [7] [6] [5] [4] [3] [2] <1> ___|
    @Test
    public void testLayout19KeyAuto8R1() {
        MoreKeysKeyboardParams params = createParams(19, 8, XPOS_R1);
        assertEquals("19 key auto 8 R1 columns", 8, params.mNumColumns);
        assertEquals("19 key auto 8 R1 rows", 3, params.mNumRows);
        assertEquals("19 key auto 8 R1 left", 7, params.mLeftKeys);
        assertEquals("19 key auto 8 R1 right", 1, params.mRightKeys);
        assertEquals("19 key auto 8 R1 <1>", 0, params.getColumnPos(0));
        assertEquals("19 key auto 8 R1 [2]", -1, params.getColumnPos(1));
        assertEquals("19 key auto 8 R1 [3]", -2, params.getColumnPos(2));
        assertEquals("19 key auto 8 R1 [4]", -3, params.getColumnPos(3));
        assertEquals("19 key auto 8 R1 [5]", -4, params.getColumnPos(4));
        assertEquals("19 key auto 8 R1 [6]", -5, params.getColumnPos(5));
        assertEquals("19 key auto 8 R1 [7]", -6, params.getColumnPos(6));
        assertEquals("19 key auto 8 R1 [8]", -7, params.getColumnPos(7));
        assertEquals("19 key auto 8 R1 [9]", 0, params.getColumnPos(8));
        assertEquals("19 key auto 8 R1 [A]", -1, params.getColumnPos(9));
        assertEquals("19 key auto 8 R1 [B]", -2, params.getColumnPos(10));
        assertEquals("19 key auto 8 R1 [C]", -3, params.getColumnPos(11));
        assertEquals("19 key auto 8 R1 [D]", -4, params.getColumnPos(12));
        assertEquals("19 key auto 8 R1 [E]", -5, params.getColumnPos(13));
        assertEquals("19 key auto 8 R1 [F]", -6, params.getColumnPos(14));
        assertEquals("19 key auto 8 R1 [G]", -7, params.getColumnPos(15));
        assertEquals("19 key auto 8 R1 [H]", 0, params.getColumnPos(16));
        assertEquals("19 key auto 8 R1 [I]", -1, params.getColumnPos(17));
        assertEquals("19 key auto 8 R1 [J]", -2, params.getColumnPos(18));
        assertEquals("19 key auto 8 R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("19 key auto 8 R1 default", WIDTH * 7, params.getDefaultKeyCoordX());
    }

    //                   [J] [H] [I]   ___|
    // [G] [F] [E] [D] [C] [B] [9] [A] ___|
    // [8] [7] [6] [5] [4] [3] <1> [2] ___|
    @Test
    public void testLayout19KeyAuto8R2() {
        MoreKeysKeyboardParams params = createParams(19, 8, XPOS_R2);
        assertEquals("19 key auto 8 R2 columns", 8, params.mNumColumns);
        assertEquals("19 key auto 8 R2 rows", 3, params.mNumRows);
        assertEquals("19 key auto 8 R2 left", 6, params.mLeftKeys);
        assertEquals("19 key auto 8 R2 right", 2, params.mRightKeys);
        assertEquals("19 key auto 8 R2 <1>", 0, params.getColumnPos(0));
        assertEquals("19 key auto 8 R2 [2]", 1, params.getColumnPos(1));
        assertEquals("19 key auto 8 R2 [3]", -1, params.getColumnPos(2));
        assertEquals("19 key auto 8 R2 [4]", -2, params.getColumnPos(3));
        assertEquals("19 key auto 8 R2 [5]", -3, params.getColumnPos(4));
        assertEquals("19 key auto 8 R2 [6]", -4, params.getColumnPos(5));
        assertEquals("19 key auto 8 R2 [7]", -5, params.getColumnPos(6));
        assertEquals("19 key auto 8 R2 [8]", -6, params.getColumnPos(7));
        assertEquals("19 key auto 8 R2 [9]", 0, params.getColumnPos(8));
        assertEquals("19 key auto 8 R2 [A]", 1, params.getColumnPos(9));
        assertEquals("19 key auto 8 R2 [B]", -1, params.getColumnPos(10));
        assertEquals("19 key auto 8 R2 [C]", -2, params.getColumnPos(11));
        assertEquals("19 key auto 8 R2 [D]", -3, params.getColumnPos(12));
        assertEquals("19 key auto 8 R2 [E]", -4, params.getColumnPos(13));
        assertEquals("19 key auto 8 R2 [F]", -5, params.getColumnPos(14));
        assertEquals("19 key auto 8 R2 [G]", -6, params.getColumnPos(15));
        assertEquals("19 key auto 8 R2 [H]", 0, params.getColumnPos(16));
        assertEquals("19 key auto 8 R2 [I]", 1, params.getColumnPos(17));
        assertEquals("19 key auto 8 R2 [J]", -1, params.getColumnPos(18));
        assertEquals("19 key auto 8 R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("19 key auto 8 R2 default", WIDTH * 6, params.getDefaultKeyCoordX());
    }

    //               [J] [H] [I]       ___|
    // [G] [F] [E] [D] [B] [9] [A] [C] ___|
    // [8] [7] [6] [5] [3] <1> [2] [4] ___|
    @Test
    public void testLayout19KeyAuto8R3() {
        MoreKeysKeyboardParams params = createParams(19, 8, XPOS_R3);
        assertEquals("19 key auto 8 R3 columns", 8, params.mNumColumns);
        assertEquals("19 key auto 8 R3 rows", 3, params.mNumRows);
        assertEquals("19 key auto 8 R3 left", 5, params.mLeftKeys);
        assertEquals("19 key auto 8 R3 right", 3, params.mRightKeys);
        assertEquals("19 key auto 8 R3 <1>", 0, params.getColumnPos(0));
        assertEquals("19 key auto 8 R3 [2]", 1, params.getColumnPos(1));
        assertEquals("19 key auto 8 R3 [3]", -1, params.getColumnPos(2));
        assertEquals("19 key auto 8 R3 [4]", 2, params.getColumnPos(3));
        assertEquals("19 key auto 8 R3 [5]", -2, params.getColumnPos(4));
        assertEquals("19 key auto 8 R3 [6]", -3, params.getColumnPos(5));
        assertEquals("19 key auto 8 R3 [7]", -4, params.getColumnPos(6));
        assertEquals("19 key auto 8 R3 [8]", -5, params.getColumnPos(7));
        assertEquals("19 key auto 8 R3 [9]", 0, params.getColumnPos(8));
        assertEquals("19 key auto 8 R3 [A]", 1, params.getColumnPos(9));
        assertEquals("19 key auto 8 R3 [B]", -1, params.getColumnPos(10));
        assertEquals("19 key auto 8 R3 [C]", 2, params.getColumnPos(11));
        assertEquals("19 key auto 8 R3 [D]", -2, params.getColumnPos(12));
        assertEquals("19 key auto 8 R3 [E]", -3, params.getColumnPos(13));
        assertEquals("19 key auto 8 R3 [F]", -4, params.getColumnPos(14));
        assertEquals("19 key auto 8 R3 [G]", -5, params.getColumnPos(15));
        assertEquals("19 key auto 8 R3 [H]", 0, params.getColumnPos(16));
        assertEquals("19 key auto 8 R3 [I]", 1, params.getColumnPos(17));
        assertEquals("19 key auto 8 R3 [J]", -1, params.getColumnPos(18));
        assertEquals("19 key auto 8 R3 adjust", -1, params.mTopRowAdjustment);
        assertEquals("19 key auto 8 R3 default", WIDTH * 5, params.getDefaultKeyCoordX());
    }
}
