/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard;

import com.android.inputmethod.keyboard.MiniKeyboard.Builder.MiniKeyboardParams;

import android.test.AndroidTestCase;

public class MiniKeyboardBuilderTests extends AndroidTestCase {
    private static final int MAX_COLUMNS = 5;
    private static final int WIDTH = 10;
    private static final int HEIGHT = 10;

    private static final int KEYBOARD_WIDTH = WIDTH * 10;
    private static final int XPOS_L0 = WIDTH * 0;
    private static final int XPOS_L1 = WIDTH * 1;
    private static final int XPOS_L2 = WIDTH * 2;
    private static final int XPOS_M0 = WIDTH * 5;
    private static final int XPOS_R3 = WIDTH * 6;
    private static final int XPOS_R2 = WIDTH * 7;
    private static final int XPOS_R1 = WIDTH * 8;
    private static final int XPOS_R0 = WIDTH * 9;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testLayoutError() {
        MiniKeyboardParams params = null;
        try {
            params = new MiniKeyboardParams(10, MAX_COLUMNS + 1, WIDTH, HEIGHT, WIDTH * 2,
                    WIDTH * MAX_COLUMNS);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Too small keyboard to hold mini keyboard.
        }
        assertNull("Too small keyboard to hold mini keyboard", params);
    }

    // Mini keyboard layout test.
    // "[n]" represents n-th key position in mini keyboard.
    // "[1]" is the default key.

    // [1]
    public void testLayout1KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(1, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("1 key M0 columns", 1, params.mNumColumns);
        assertEquals("1 key M0 rows", 1, params.mNumRows);
        assertEquals("1 key M0 left", 0, params.mLeftKeys);
        assertEquals("1 key M0 right", 1, params.mRightKeys);
        assertEquals("1 key M0 [1]", 0, params.getColumnPos(0));
        assertEquals("1 key M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |[1]
    public void testLayout1KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(1, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("1 key L0 columns", 1, params.mNumColumns);
        assertEquals("1 key L0 rows", 1, params.mNumRows);
        assertEquals("1 key L0 left", 0, params.mLeftKeys);
        assertEquals("1 key L0 right", 1, params.mRightKeys);
        assertEquals("1 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("1 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [1]
    public void testLayout1KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(1, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("1 key L1 columns", 1, params.mNumColumns);
        assertEquals("1 key L1 rows", 1, params.mNumRows);
        assertEquals("1 key L1 left", 0, params.mLeftKeys);
        assertEquals("1 key L1 right", 1, params.mRightKeys);
        assertEquals("1 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("1 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [1]
    public void testLayout1KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(1, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("1 key L2 columns", 1, params.mNumColumns);
        assertEquals("1 key L2 rows", 1, params.mNumRows);
        assertEquals("1 key L2 left", 0, params.mLeftKeys);
        assertEquals("1 key L2 right", 1, params.mRightKeys);
        assertEquals("1 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("1 key L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [1]|
    public void testLayout1KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(1, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("1 key R0 columns", 1, params.mNumColumns);
        assertEquals("1 key R0 rows", 1, params.mNumRows);
        assertEquals("1 key R0 left", 0, params.mLeftKeys);
        assertEquals("1 key R0 right", 1, params.mRightKeys);
        assertEquals("1 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("1 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key R0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [1] ___|
    public void testLayout1KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(1, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("1 key R1 columns", 1, params.mNumColumns);
        assertEquals("1 key R1 rows", 1, params.mNumRows);
        assertEquals("1 key R1 left", 0, params.mLeftKeys);
        assertEquals("1 key R1 right", 1, params.mRightKeys);
        assertEquals("1 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("1 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key R1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [1] ___ ___|
    public void testLayout1KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(1, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("1 key R2 columns", 1, params.mNumColumns);
        assertEquals("1 key R2 rows", 1, params.mNumRows);
        assertEquals("1 key R2 left", 0, params.mLeftKeys);
        assertEquals("1 key R2 right", 1, params.mRightKeys);
        assertEquals("1 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("1 key R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("1 key R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [1] [2]
    public void testLayout2KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(2, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("2 key M0 columns", 2, params.mNumColumns);
        assertEquals("2 key M0 rows", 1, params.mNumRows);
        assertEquals("2 key M0 left", 0, params.mLeftKeys);
        assertEquals("2 key M0 right", 2, params.mRightKeys);
        assertEquals("2 key M0 [1]", 0, params.getColumnPos(0));
        assertEquals("2 key M0 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key M0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |[1] [2]
    public void testLayout2KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(2, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("2 key L0 columns", 2, params.mNumColumns);
        assertEquals("2 key L0 rows", 1, params.mNumRows);
        assertEquals("2 key L0 left", 0, params.mLeftKeys);
        assertEquals("2 key L0 right", 2, params.mRightKeys);
        assertEquals("2 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("2 key L0 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [1] [2]
    public void testLayout2KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(2, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("2 key L1 columns", 2, params.mNumColumns);
        assertEquals("2 key L1 rows", 1, params.mNumRows);
        assertEquals("2 key L1 left", 0, params.mLeftKeys);
        assertEquals("2 key L1 right", 2, params.mRightKeys);
        assertEquals("2 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("2 key L1 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [1] [2]
    public void testLayout2KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(2, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("2 key L2 columns", 2, params.mNumColumns);
        assertEquals("2 key L2 rows", 1, params.mNumRows);
        assertEquals("2 key L2 left", 0, params.mLeftKeys);
        assertEquals("2 key L2 right", 2, params.mRightKeys);
        assertEquals("2 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("2 key L2 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key L2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [2] [1]|
    public void testLayout2KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(2, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("2 key R0 columns", 2, params.mNumColumns);
        assertEquals("2 key R0 rows", 1, params.mNumRows);
        assertEquals("2 key R0 left", 1, params.mLeftKeys);
        assertEquals("2 key R0 right", 1, params.mRightKeys);
        assertEquals("2 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("2 key R0 [2]", -1, params.getColumnPos(1));
        assertEquals("2 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key R0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [2] [1] ___|
    public void testLayout2KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(2, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("2 key R1 columns", 2, params.mNumColumns);
        assertEquals("2 key R1 rows", 1, params.mNumRows);
        assertEquals("2 key R1 left", 1, params.mLeftKeys);
        assertEquals("2 key R1 right", 1, params.mRightKeys);
        assertEquals("2 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("2 key R1 [2]", -1, params.getColumnPos(1));
        assertEquals("2 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key R1 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [1] [2] ___ ___|
    public void testLayout2KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(2, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("2 key R2 columns", 2, params.mNumColumns);
        assertEquals("2 key R2 rows", 1, params.mNumRows);
        assertEquals("2 key R2 left", 0, params.mLeftKeys);
        assertEquals("2 key R2 right", 2, params.mRightKeys);
        assertEquals("2 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("2 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("2 key R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("2 key R2 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // [3] [1] [2]
    public void testLayout3KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(3, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("3 key columns", 3, params.mNumColumns);
        assertEquals("3 key rows", 1, params.mNumRows);
        assertEquals("3 key left", 1, params.mLeftKeys);
        assertEquals("3 key right", 2, params.mRightKeys);
        assertEquals("3 key [1]", 0, params.getColumnPos(0));
        assertEquals("3 key [2]", 1, params.getColumnPos(1));
        assertEquals("3 key [3]", -1, params.getColumnPos(2));
        assertEquals("3 key adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[1] [2] [3]
    public void testLayout3KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(3, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("3 key L0 columns", 3, params.mNumColumns);
        assertEquals("3 key L0 rows", 1, params.mNumRows);
        assertEquals("3 key L0 left", 0, params.mLeftKeys);
        assertEquals("3 key L0 right", 3, params.mRightKeys);
        assertEquals("3 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("3 key L0 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key L0 [3]", 2, params.getColumnPos(2));
        assertEquals("3 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [1] [2] [3]
    public void testLayout3KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(3, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("3 key L1 columns", 3, params.mNumColumns);
        assertEquals("3 key L1 rows", 1, params.mNumRows);
        assertEquals("3 key L1 left", 0, params.mLeftKeys);
        assertEquals("3 key L1 right", 3, params.mRightKeys);
        assertEquals("3 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("3 key L1 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key L1 [3]", 2, params.getColumnPos(2));
        assertEquals("3 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [3] [1] [2]
    public void testLayout3KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(3, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("3 key L2 columns", 3, params.mNumColumns);
        assertEquals("3 key L2 rows", 1, params.mNumRows);
        assertEquals("3 key L2 left", 1, params.mLeftKeys);
        assertEquals("3 key L2 right", 2, params.mRightKeys);
        assertEquals("3 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("3 key L2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key L2 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3] [2] [1]|
    public void testLayout3KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(3, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("3 key R0 columns", 3, params.mNumColumns);
        assertEquals("3 key R0 rows", 1, params.mNumRows);
        assertEquals("3 key R0 left", 2, params.mLeftKeys);
        assertEquals("3 key R0 right", 1, params.mRightKeys);
        assertEquals("3 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("3 key R0 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key R0 [3]", -2, params.getColumnPos(2));
        assertEquals("3 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [3] [2] [1] ___|
    public void testLayout3KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(3, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("3 key R1 columns", 3, params.mNumColumns);
        assertEquals("3 key R1 rows", 1, params.mNumRows);
        assertEquals("3 key R1 left", 2, params.mLeftKeys);
        assertEquals("3 key R1 right", 1, params.mRightKeys);
        assertEquals("3 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("3 key R1 [2]", -1, params.getColumnPos(1));
        assertEquals("3 key R1 [3]", -2, params.getColumnPos(2));
        assertEquals("3 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [3] [1] [2] ___ ___|
    public void testLayout3KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(3, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("3 key R2 columns", 3, params.mNumColumns);
        assertEquals("3 key R2 rows", 1, params.mNumRows);
        assertEquals("3 key R2 left", 1, params.mLeftKeys);
        assertEquals("3 key R2 right", 2, params.mRightKeys);
        assertEquals("3 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("3 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("3 key R2 [3]", -1, params.getColumnPos(2));
        assertEquals("3 key R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("3 key R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [3] [1] [2] [4]
    public void testLayout4KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(4, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("4 key columns", 4, params.mNumColumns);
        assertEquals("4 key rows", 1, params.mNumRows);
        assertEquals("4 key left", 1, params.mLeftKeys);
        assertEquals("4 key right", 3, params.mRightKeys);
        assertEquals("4 key [1]", 0, params.getColumnPos(0));
        assertEquals("4 key [2]", 1, params.getColumnPos(1));
        assertEquals("4 key [3]", -1, params.getColumnPos(2));
        assertEquals("4 key [4]", 2, params.getColumnPos(3));
        assertEquals("4 key adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[1] [2] [3] [4]
    public void testLayout4KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(4, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("4 key L0 columns", 4, params.mNumColumns);
        assertEquals("4 key L0 rows", 1, params.mNumRows);
        assertEquals("4 key L0 left", 0, params.mLeftKeys);
        assertEquals("4 key L0 right", 4, params.mRightKeys);
        assertEquals("4 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("4 key L0 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key L0 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key L0 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [1] [2] [3] [4]
    public void testLayout4KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(4, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("4 key L1 columns", 4, params.mNumColumns);
        assertEquals("4 key L1 rows", 1, params.mNumRows);
        assertEquals("4 key L1 left", 0, params.mLeftKeys);
        assertEquals("4 key L1 right", 4, params.mRightKeys);
        assertEquals("4 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("4 key L1 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key L1 [3]", 2, params.getColumnPos(2));
        assertEquals("4 key L1 [4]", 3, params.getColumnPos(3));
        assertEquals("4 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [3] [1] [2] [4]
    public void testLayout4KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(4, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("4 key L2 columns", 4, params.mNumColumns);
        assertEquals("4 key L2 rows", 1, params.mNumRows);
        assertEquals("4 key L2 left", 1, params.mLeftKeys);
        assertEquals("4 key L2 right", 3, params.mRightKeys);
        assertEquals("4 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("4 key L2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key L2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key L2 [4]", 2, params.getColumnPos(3));
        assertEquals("4 key L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] [1]|
    public void testLayout4KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(4, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("4 key R0 columns", 4, params.mNumColumns);
        assertEquals("4 key R0 rows", 1, params.mNumRows);
        assertEquals("4 key R0 left", 3, params.mLeftKeys);
        assertEquals("4 key R0 right", 1, params.mRightKeys);
        assertEquals("4 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("4 key R0 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key R0 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key R0 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] [2] [1] ___|
    public void testLayout4KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(4, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("4 key R1 columns", 4, params.mNumColumns);
        assertEquals("4 key R1 rows", 1, params.mNumRows);
        assertEquals("4 key R1 left", 3, params.mLeftKeys);
        assertEquals("4 key R1 right", 1, params.mRightKeys);
        assertEquals("4 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("4 key R1 [2]", -1, params.getColumnPos(1));
        assertEquals("4 key R1 [3]", -2, params.getColumnPos(2));
        assertEquals("4 key R1 [4]", -3, params.getColumnPos(3));
        assertEquals("4 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [4] [3] [1] [2] ___ ___|
    public void testLayout4KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(4, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("4 key R2 columns", 4, params.mNumColumns);
        assertEquals("4 key R2 rows", 1, params.mNumRows);
        assertEquals("4 key R2 left", 2, params.mLeftKeys);
        assertEquals("4 key R2 right", 2, params.mRightKeys);
        assertEquals("4 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("4 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("4 key R2 [3]", -1, params.getColumnPos(2));
        assertEquals("4 key R2 [4]", -2, params.getColumnPos(3));
        assertEquals("4 key R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("4 key R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [5] [3] [1] [2] [4]
    public void testLayout5KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(5, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("5 key columns", 5, params.mNumColumns);
        assertEquals("5 key rows", 1, params.mNumRows);
        assertEquals("5 key left", 2, params.mLeftKeys);
        assertEquals("5 key right", 3, params.mRightKeys);
        assertEquals("5 key [1]", 0, params.getColumnPos(0));
        assertEquals("5 key [2]", 1, params.getColumnPos(1));
        assertEquals("5 key [3]", -1, params.getColumnPos(2));
        assertEquals("5 key [4]", 2, params.getColumnPos(3));
        assertEquals("5 key [5]", -2, params.getColumnPos(4));
        assertEquals("5 key adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[1] [2] [3] [4] [5]
    public void testLayout5KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(5, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("5 key L0 columns", 5, params.mNumColumns);
        assertEquals("5 key L0 rows", 1, params.mNumRows);
        assertEquals("5 key L0 left", 0, params.mLeftKeys);
        assertEquals("5 key L0 right", 5, params.mRightKeys);
        assertEquals("5 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("5 key L0 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key L0 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key L0 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key L0 [5]", 4, params.getColumnPos(4));
        assertEquals("5 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [1] [2] [3] [4] [5]
    public void testLayout5KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(5, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("5 key L1 columns", 5, params.mNumColumns);
        assertEquals("5 key L1 rows", 1, params.mNumRows);
        assertEquals("5 key L1 left", 0, params.mLeftKeys);
        assertEquals("5 key L1 right", 5, params.mRightKeys);
        assertEquals("5 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("5 key L1 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key L1 [3]", 2, params.getColumnPos(2));
        assertEquals("5 key L1 [4]", 3, params.getColumnPos(3));
        assertEquals("5 key L1 [5]", 4, params.getColumnPos(4));
        assertEquals("5 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [3] [1] [2] [4] [5]
    public void testLayout5KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(5, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("5 key L2 columns", 5, params.mNumColumns);
        assertEquals("5 key L2 rows", 1, params.mNumRows);
        assertEquals("5 key L2 left", 1, params.mLeftKeys);
        assertEquals("5 key L2 right", 4, params.mRightKeys);
        assertEquals("5 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("5 key L2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key L2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key L2 [4]", 2, params.getColumnPos(3));
        assertEquals("5 key L2 [5]", 3, params.getColumnPos(4));
        assertEquals("5 key L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [5] [4] [3] [2] [1]|
    public void testLayout5KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(5, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("5 key R0 columns", 5, params.mNumColumns);
        assertEquals("5 key R0 rows", 1, params.mNumRows);
        assertEquals("5 key R0 left", 4, params.mLeftKeys);
        assertEquals("5 key R0 right", 1, params.mRightKeys);
        assertEquals("5 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("5 key R0 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key R0 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key R0 [4]", -3, params.getColumnPos(3));
        assertEquals("5 key R0 [5]", -4, params.getColumnPos(4));
        assertEquals("5 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [5] [4] [3] [2] [1] ___|
    public void testLayout5KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(5, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("5 key R1 columns", 5, params.mNumColumns);
        assertEquals("5 key R1 rows", 1, params.mNumRows);
        assertEquals("5 key R1 left", 4, params.mLeftKeys);
        assertEquals("5 key R1 right", 1, params.mRightKeys);
        assertEquals("5 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("5 key R1 [2]", -1, params.getColumnPos(1));
        assertEquals("5 key R1 [3]", -2, params.getColumnPos(2));
        assertEquals("5 key R1 [4]", -3, params.getColumnPos(3));
        assertEquals("5 key R1 [5]", -4, params.getColumnPos(4));
        assertEquals("5 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [5] [4] [3] [1] [2] ___ ___|
    public void testLayout5KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(5, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("5 key R2 columns", 5, params.mNumColumns);
        assertEquals("5 key R2 rows", 1, params.mNumRows);
        assertEquals("5 key R2 left", 3, params.mLeftKeys);
        assertEquals("5 key R2 right", 2, params.mRightKeys);
        assertEquals("5 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("5 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("5 key R2 [3]", -1, params.getColumnPos(2));
        assertEquals("5 key R2 [4]", -2, params.getColumnPos(3));
        assertEquals("5 key R2 [5]", -3, params.getColumnPos(4));
        assertEquals("5 key R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("5 key R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [6] [4] [5]
    // [3] [1] [2]
    public void testLayout6KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(6, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("6 key columns", 3, params.mNumColumns);
        assertEquals("6 key rows", 2, params.mNumRows);
        assertEquals("6 key left", 1, params.mLeftKeys);
        assertEquals("6 key right", 2, params.mRightKeys);
        assertEquals("6 key [1]", 0, params.getColumnPos(0));
        assertEquals("6 key [2]", 1, params.getColumnPos(1));
        assertEquals("6 key [3]", -1, params.getColumnPos(2));
        assertEquals("6 key [4]", 0, params.getColumnPos(3));
        assertEquals("6 key [5]", 1, params.getColumnPos(4));
        assertEquals("6 key [6]", -1, params.getColumnPos(5));
        assertEquals("6 key adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[4] [5] [6]
    // |[1] [2] [3]
    public void testLayout6KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(6, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("6 key L0 columns", 3, params.mNumColumns);
        assertEquals("6 key L0 rows", 2, params.mNumRows);
        assertEquals("6 key L0 left", 0, params.mLeftKeys);
        assertEquals("6 key L0 right", 3, params.mRightKeys);
        assertEquals("6 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("6 key L0 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key L0 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key L0 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key L0 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key L0 [6]", 2, params.getColumnPos(5));
        assertEquals("6 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [4] [5] [6]
    // |___ [1] [2] [3]
    public void testLayout6KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(6, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("6 key L1 columns", 3, params.mNumColumns);
        assertEquals("6 key L1 rows", 2, params.mNumRows);
        assertEquals("6 key L1 left", 0, params.mLeftKeys);
        assertEquals("6 key L1 right", 3, params.mRightKeys);
        assertEquals("6 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("6 key L1 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key L1 [3]", 2, params.getColumnPos(2));
        assertEquals("6 key L1 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key L1 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key L1 [6]", 2, params.getColumnPos(5));
        assertEquals("6 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [6] [4] [5]
    // |___ ___ [3] [1] [2]
    public void testLayout6KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(6, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("6 key L2 columns", 3, params.mNumColumns);
        assertEquals("6 key L2 rows", 2, params.mNumRows);
        assertEquals("6 key L2 left", 1, params.mLeftKeys);
        assertEquals("6 key L2 right", 2, params.mRightKeys);
        assertEquals("6 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("6 key L2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key L2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key L2 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key L2 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key L2 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [6] [5] [4]|
    // [3] [2] [1]|
    public void testLayout6KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(6, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("6 key R0 columns", 3, params.mNumColumns);
        assertEquals("6 key R0 rows", 2, params.mNumRows);
        assertEquals("6 key R0 left", 2, params.mLeftKeys);
        assertEquals("6 key R0 right", 1, params.mRightKeys);
        assertEquals("6 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("6 key R0 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key R0 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key R0 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key R0 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key R0 [6]", -2, params.getColumnPos(5));
        assertEquals("6 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key R0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [6] [5] [4] ___|
    // [3] [2] [1] ___|
    public void testLayout6KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(6, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("6 key R1 columns", 3, params.mNumColumns);
        assertEquals("6 key R1 rows", 2, params.mNumRows);
        assertEquals("6 key R1 left", 2, params.mLeftKeys);
        assertEquals("6 key R1 right", 1, params.mRightKeys);
        assertEquals("6 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("6 key R1 [2]", -1, params.getColumnPos(1));
        assertEquals("6 key R1 [3]", -2, params.getColumnPos(2));
        assertEquals("6 key R1 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key R1 [5]", -1, params.getColumnPos(4));
        assertEquals("6 key R1 [6]", -2, params.getColumnPos(5));
        assertEquals("6 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key R1 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [6] [4] [5] ___ ___|
    // [3] [1] [2] ___ ___|
    public void testLayout6KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(6, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("6 key R2 columns", 3, params.mNumColumns);
        assertEquals("6 key R2 rows", 2, params.mNumRows);
        assertEquals("6 key R2 left", 1, params.mLeftKeys);
        assertEquals("6 key R2 right", 2, params.mRightKeys);
        assertEquals("6 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("6 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("6 key R2 [3]", -1, params.getColumnPos(2));
        assertEquals("6 key R2 [4]", 0, params.getColumnPos(3));
        assertEquals("6 key R2 [5]", 1, params.getColumnPos(4));
        assertEquals("6 key R2 [6]", -1, params.getColumnPos(5));
        assertEquals("6 key R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("6 key R2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //   [7] [5] [6]
    // [3] [1] [2] [4]
    public void testLayout7KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(7, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("7 key columns", 4, params.mNumColumns);
        assertEquals("7 key rows", 2, params.mNumRows);
        assertEquals("7 key left", 1, params.mLeftKeys);
        assertEquals("7 key right", 3, params.mRightKeys);
        assertEquals("7 key [1]", 0, params.getColumnPos(0));
        assertEquals("7 key [2]", 1, params.getColumnPos(1));
        assertEquals("7 key [3]", -1, params.getColumnPos(2));
        assertEquals("7 key [4]", 2, params.getColumnPos(3));
        assertEquals("7 key [5]", 0, params.getColumnPos(4));
        assertEquals("7 key [6]", 1, params.getColumnPos(5));
        assertEquals("7 key [7]", -1, params.getColumnPos(6));
        assertEquals("7 key adjust", 1, params.mTopRowAdjustment);
        assertEquals("7 key default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5] [6] [7]
    // |[1] [2] [3] [4]
    public void testLayout7KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(7, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("7 key L0 columns", 4, params.mNumColumns);
        assertEquals("7 key L0 rows", 2, params.mNumRows);
        assertEquals("7 key L0 left", 0, params.mLeftKeys);
        assertEquals("7 key L0 right", 4, params.mRightKeys);
        assertEquals("7 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("7 key L0 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key L0 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key L0 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key L0 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key L0 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key L0 [7]", 2, params.getColumnPos(6));
        assertEquals("7 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6] [7]
    // |___ [1] [2] [3] [4]
    public void testLayout7KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(7, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("7 key L1 columns", 4, params.mNumColumns);
        assertEquals("7 key L1 rows", 2, params.mNumRows);
        assertEquals("7 key L1 left", 0, params.mLeftKeys);
        assertEquals("7 key L1 right", 4, params.mRightKeys);
        assertEquals("7 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("7 key L1 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key L1 [3]", 2, params.getColumnPos(2));
        assertEquals("7 key L1 [4]", 3, params.getColumnPos(3));
        assertEquals("7 key L1 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key L1 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key L1 [7]", 2, params.getColumnPos(6));
        assertEquals("7 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___   [7] [5] [6]
    // |___ ___ [3] [1] [2] [4]
    public void testLayout7KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(7, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("7 key L2 columns", 4, params.mNumColumns);
        assertEquals("7 key L2 rows", 2, params.mNumRows);
        assertEquals("7 key L2 left", 1, params.mLeftKeys);
        assertEquals("7 key L2 right", 3, params.mRightKeys);
        assertEquals("7 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("7 key L2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key L2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key L2 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key L2 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key L2 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key L2 [7]", -1, params.getColumnPos(6));
        assertEquals("7 key L2 adjust", 1, params.mTopRowAdjustment);
        assertEquals("7 key L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [7] [6] [5]|
    // [4] [3] [2] [1]|
    public void testLayout7KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(7, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("7 key R0 columns", 4, params.mNumColumns);
        assertEquals("7 key R0 rows", 2, params.mNumRows);
        assertEquals("7 key R0 left", 3, params.mLeftKeys);
        assertEquals("7 key R0 right", 1, params.mRightKeys);
        assertEquals("7 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("7 key R0 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key R0 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key R0 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key R0 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key R0 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key R0 [7]", -2, params.getColumnPos(6));
        assertEquals("7 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //     [7] [6] [5] ___|
    // [4] [3] [2] [1] ___|
    public void testLayout7KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(7, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("7 key R1 columns", 4, params.mNumColumns);
        assertEquals("7 key R1 rows", 2, params.mNumRows);
        assertEquals("7 key R1 left", 3, params.mLeftKeys);
        assertEquals("7 key R1 right", 1, params.mRightKeys);
        assertEquals("7 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("7 key R1 [2]", -1, params.getColumnPos(1));
        assertEquals("7 key R1 [3]", -2, params.getColumnPos(2));
        assertEquals("7 key R1 [4]", -3, params.getColumnPos(3));
        assertEquals("7 key R1 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key R1 [6]", -1, params.getColumnPos(5));
        assertEquals("7 key R1 [7]", -2, params.getColumnPos(6));
        assertEquals("7 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //   [7] [5] [6]   ___ ___|
    // [4] [3] [1] [2] ___ ___|
    public void testLayout7KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(7, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("7 key R2 columns", 4, params.mNumColumns);
        assertEquals("7 key R2 rows", 2, params.mNumRows);
        assertEquals("7 key R2 left", 2, params.mLeftKeys);
        assertEquals("7 key R2 right", 2, params.mRightKeys);
        assertEquals("7 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("7 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key R2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key R2 [4]", -2, params.getColumnPos(3));
        assertEquals("7 key R2 [5]", 0, params.getColumnPos(4));
        assertEquals("7 key R2 [6]", 1, params.getColumnPos(5));
        assertEquals("7 key R2 [7]", -1, params.getColumnPos(6));
        assertEquals("7 key R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("7 key R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // [7] [6] [5] [3] [1] [2] [4] ___|
    public void testLayout7KeyR3Max7() {
        MiniKeyboardParams params = new MiniKeyboardParams(7, 7, WIDTH,
                HEIGHT, XPOS_R3, KEYBOARD_WIDTH);
        assertEquals("7 key R2 columns", 7, params.mNumColumns);
        assertEquals("7 key R2 rows", 1, params.mNumRows);
        assertEquals("7 key R2 left", 4, params.mLeftKeys);
        assertEquals("7 key R2 right", 3, params.mRightKeys);
        assertEquals("7 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("7 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("7 key R2 [3]", -1, params.getColumnPos(2));
        assertEquals("7 key R2 [4]", 2, params.getColumnPos(3));
        assertEquals("7 key R2 [5]", -2, params.getColumnPos(4));
        assertEquals("7 key R2 [6]", -3, params.getColumnPos(5));
        assertEquals("7 key R2 [7]", -4, params.getColumnPos(6));
        assertEquals("7 key R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("7 key R2 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [7] [5] [6] [8]
    // [3] [1] [2] [4]
    public void testLayout8KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(8, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("8 key M0 columns", 4, params.mNumColumns);
        assertEquals("8 key M0 rows", 2, params.mNumRows);
        assertEquals("8 key M0 left", 1, params.mLeftKeys);
        assertEquals("8 key M0 right", 3, params.mRightKeys);
        assertEquals("8 key M0 [1]", 0, params.getColumnPos(0));
        assertEquals("8 key M0 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key M0 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key M0 [4]", 2, params.getColumnPos(3));
        assertEquals("8 key M0 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key M0 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key M0 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key M0 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // |[5] [6] [7] [8]
    // |[1] [2] [3] [4]
    public void testLayout8KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(8, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("8 key L0 columns", 4, params.mNumColumns);
        assertEquals("8 key L0 rows", 2, params.mNumRows);
        assertEquals("8 key L0 left", 0, params.mLeftKeys);
        assertEquals("8 key L0 right", 4, params.mRightKeys);
        assertEquals("8 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("8 key L0 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key L0 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key L0 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key L0 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key L0 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key L0 [7]", 2, params.getColumnPos(6));
        assertEquals("8 key L0 [8]", 3, params.getColumnPos(7));
        assertEquals("8 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [5] [6] [7] [8]
    // |___ [1] [2] [3] [4]
    public void testLayout8KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(8, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("8 key L1 columns", 4, params.mNumColumns);
        assertEquals("8 key L1 rows", 2, params.mNumRows);
        assertEquals("8 key L1 left", 0, params.mLeftKeys);
        assertEquals("8 key L1 right", 4, params.mRightKeys);
        assertEquals("8 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("8 key L1 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key L1 [3]", 2, params.getColumnPos(2));
        assertEquals("8 key L1 [4]", 3, params.getColumnPos(3));
        assertEquals("8 key L1 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key L1 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key L1 [7]", 2, params.getColumnPos(6));
        assertEquals("8 key L1 [8]", 3, params.getColumnPos(7));
        assertEquals("8 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [7] [5] [6] [8]
    // |___ ___ [3] [1] [2] [4]
    public void testLayout8KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(8, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("8 key L2 columns", 4, params.mNumColumns);
        assertEquals("8 key L2 rows", 2, params.mNumRows);
        assertEquals("8 key L2 left", 1, params.mLeftKeys);
        assertEquals("8 key L2 right", 3, params.mRightKeys);
        assertEquals("8 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("8 key L2 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key L2 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key L2 [4]", 2, params.getColumnPos(3));
        assertEquals("8 key L2 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key L2 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key L2 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key L2 [8]", 2, params.getColumnPos(7));
        assertEquals("8 key L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [8] [7] [6] [5]|
    // [4] [3] [2] [1]|
    public void testLayout8KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(8, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("8 key R0 columns", 4, params.mNumColumns);
        assertEquals("8 key R0 rows", 2, params.mNumRows);
        assertEquals("8 key R0 left", 3, params.mLeftKeys);
        assertEquals("8 key R0 right", 1, params.mRightKeys);
        assertEquals("8 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("8 key R0 [2]", -1, params.getColumnPos(1));
        assertEquals("8 key R0 [3]", -2, params.getColumnPos(2));
        assertEquals("8 key R0 [4]", -3, params.getColumnPos(3));
        assertEquals("8 key R0 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key R0 [6]", -1, params.getColumnPos(5));
        assertEquals("8 key R0 [7]", -2, params.getColumnPos(6));
        assertEquals("8 key R0 [8]", -3, params.getColumnPos(7));
        assertEquals("8 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key R0 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [8] [7] [6] [5] ___|
    // [4] [3] [2] [1] ___|
    public void testLayout8KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(8, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("8 key R1 columns", 4, params.mNumColumns);
        assertEquals("8 key R1 rows", 2, params.mNumRows);
        assertEquals("8 key R1 left", 3, params.mLeftKeys);
        assertEquals("8 key R1 right", 1, params.mRightKeys);
        assertEquals("8 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("8 key R1 [2]", -1, params.getColumnPos(1));
        assertEquals("8 key R1 [3]", -2, params.getColumnPos(2));
        assertEquals("8 key R1 [4]", -3, params.getColumnPos(3));
        assertEquals("8 key R1 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key R1 [6]", -1, params.getColumnPos(5));
        assertEquals("8 key R1 [7]", -2, params.getColumnPos(6));
        assertEquals("8 key R1 [8]", -3, params.getColumnPos(7));
        assertEquals("8 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key R1 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [8] [7] [5] [6] ___ ___|
    // [4] [3] [1] [2] ___ ___|
    public void testLayout8KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(8, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("8 key R2 columns", 4, params.mNumColumns);
        assertEquals("8 key R2 rows", 2, params.mNumRows);
        assertEquals("8 key R2 left", 2, params.mLeftKeys);
        assertEquals("8 key R2 right", 2, params.mRightKeys);
        assertEquals("8 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("8 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("8 key R2 [3]", -1, params.getColumnPos(2));
        assertEquals("8 key R2 [4]", -2, params.getColumnPos(3));
        assertEquals("8 key R2 [5]", 0, params.getColumnPos(4));
        assertEquals("8 key R2 [6]", 1, params.getColumnPos(5));
        assertEquals("8 key R2 [7]", -1, params.getColumnPos(6));
        assertEquals("8 key R2 [8]", -2, params.getColumnPos(7));
        assertEquals("8 key R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("8 key R2 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [8] [6] [7] [9]
    // [5] [3] [1] [2] [4]
    public void testLayout9KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(9, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("9 key M0 columns", 5, params.mNumColumns);
        assertEquals("9 key M0 rows", 2, params.mNumRows);
        assertEquals("9 key M0 left", 2, params.mLeftKeys);
        assertEquals("9 key M0 right", 3, params.mRightKeys);
        assertEquals("9 key M0 [1]", 0, params.getColumnPos(0));
        assertEquals("9 key M0 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key M0 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key M0 [4]", 2, params.getColumnPos(3));
        assertEquals("9 key M0 [5]", -2, params.getColumnPos(4));
        assertEquals("9 key M0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key M0 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key M0 [8]", -1, params.getColumnPos(7));
        assertEquals("9 key M0 [9]", 2, params.getColumnPos(8));
        assertEquals("9 key M0 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8] [9]
    // |[1] [2] [3] [4] [5]
    public void testLayout9KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(9, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("9 key L0 columns", 5, params.mNumColumns);
        assertEquals("9 key L0 rows", 2, params.mNumRows);
        assertEquals("9 key L0 left", 0, params.mLeftKeys);
        assertEquals("9 key L0 right", 5, params.mRightKeys);
        assertEquals("9 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("9 key L0 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key L0 [3]", 2, params.getColumnPos(2));
        assertEquals("9 key L0 [4]", 3, params.getColumnPos(3));
        assertEquals("9 key L0 [5]", 4, params.getColumnPos(4));
        assertEquals("9 key L0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key L0 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key L0 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key L0 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8] [9]
    // |___ [1] [2] [3] [4] [5]
    public void testLayout9KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(9, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("9 key L1 columns", 5, params.mNumColumns);
        assertEquals("9 key L1 rows", 2, params.mNumRows);
        assertEquals("9 key L1 left", 0, params.mLeftKeys);
        assertEquals("9 key L1 right", 5, params.mRightKeys);
        assertEquals("9 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("9 key L1 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key L1 [3]", 2, params.getColumnPos(2));
        assertEquals("9 key L1 [4]", 3, params.getColumnPos(3));
        assertEquals("9 key L1 [5]", 4, params.getColumnPos(4));
        assertEquals("9 key L1 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key L1 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key L1 [8]", 2, params.getColumnPos(7));
        assertEquals("9 key L1 [9]", 3, params.getColumnPos(8));
        assertEquals("9 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___   [8] [6] [7] [9]
    // |___ ___ [3] [1] [2] [4] [5]
    public void testLayout9KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(9, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("9 key L2 columns", 5, params.mNumColumns);
        assertEquals("9 key L2 rows", 2, params.mNumRows);
        assertEquals("9 key L2 left", 1, params.mLeftKeys);
        assertEquals("9 key L2 right", 4, params.mRightKeys);
        assertEquals("9 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("9 key L2 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key L2 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key L2 [4]", 2, params.getColumnPos(3));
        assertEquals("9 key L2 [5]", 3, params.getColumnPos(4));
        assertEquals("9 key L2 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key L2 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key L2 [8]", -1, params.getColumnPos(7));
        assertEquals("9 key L2 [9]", 2, params.getColumnPos(8));
        assertEquals("9 key L2 adjust", 1, params.mTopRowAdjustment);
        assertEquals("9 key L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    //     [9] [8] [7] [6]|
    // [5] [4] [3] [2] [1]|
    public void testLayout9KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(9, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("9 key R0 columns", 5, params.mNumColumns);
        assertEquals("9 key R0 rows", 2, params.mNumRows);
        assertEquals("9 key R0 left", 4, params.mLeftKeys);
        assertEquals("9 key R0 right", 1, params.mRightKeys);
        assertEquals("9 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("9 key R0 [2]", -1, params.getColumnPos(1));
        assertEquals("9 key R0 [3]", -2, params.getColumnPos(2));
        assertEquals("9 key R0 [4]", -3, params.getColumnPos(3));
        assertEquals("9 key R0 [5]", -4, params.getColumnPos(4));
        assertEquals("9 key R0 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key R0 [7]", -1, params.getColumnPos(6));
        assertEquals("9 key R0 [8]", -2, params.getColumnPos(7));
        assertEquals("9 key R0 [9]", -3, params.getColumnPos(8));
        assertEquals("9 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //     [9] [8] [7] [6] ___|
    // [5] [4] [3] [2] [1] ___|
    public void testLayout9KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(9, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("9 key R1 columns", 5, params.mNumColumns);
        assertEquals("9 key R1 rows", 2, params.mNumRows);
        assertEquals("9 key R1 left", 4, params.mLeftKeys);
        assertEquals("9 key R1 right", 1, params.mRightKeys);
        assertEquals("9 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("9 key R1 [2]", -1, params.getColumnPos(1));
        assertEquals("9 key R1 [3]", -2, params.getColumnPos(2));
        assertEquals("9 key R1 [4]", -3, params.getColumnPos(3));
        assertEquals("9 key R1 [5]", -4, params.getColumnPos(4));
        assertEquals("9 key R1 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key R1 [7]", -1, params.getColumnPos(6));
        assertEquals("9 key R1 [8]", -2, params.getColumnPos(7));
        assertEquals("9 key R1 [9]", -3, params.getColumnPos(8));
        assertEquals("9 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("9 key R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    //   [9] [8] [6] [7]   ___ ___|
    // [5] [4] [3] [1] [2] ___ ___|
    public void testLayout9KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(9, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("9 key R2 columns", 5, params.mNumColumns);
        assertEquals("9 key R2 rows", 2, params.mNumRows);
        assertEquals("9 key R2 left", 3, params.mLeftKeys);
        assertEquals("9 key R2 right", 2, params.mRightKeys);
        assertEquals("9 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("9 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("9 key R2 [3]", -1, params.getColumnPos(2));
        assertEquals("9 key R2 [4]", -2, params.getColumnPos(3));
        assertEquals("9 key R2 [5]", -3, params.getColumnPos(4));
        assertEquals("9 key R2 [6]", 0, params.getColumnPos(5));
        assertEquals("9 key R2 [7]", 1, params.getColumnPos(6));
        assertEquals("9 key R2 [8]", -1, params.getColumnPos(7));
        assertEquals("9 key R2 [9]", -2, params.getColumnPos(8));
        assertEquals("9 key R2 adjust", -1, params.mTopRowAdjustment);
        assertEquals("9 key R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    // [A] [8] [6] [7] [9]
    // [5] [3] [1] [2] [4]
    public void testLayout10KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(10, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("10 key M0 columns", 5, params.mNumColumns);
        assertEquals("10 key M0 rows", 2, params.mNumRows);
        assertEquals("10 key M0 left", 2, params.mLeftKeys);
        assertEquals("10 key M0 right", 3, params.mRightKeys);
        assertEquals("10 key M0 [1]", 0, params.getColumnPos(0));
        assertEquals("10 key M0 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key M0 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key M0 [4]", 2, params.getColumnPos(3));
        assertEquals("10 key M0 [5]", -2, params.getColumnPos(4));
        assertEquals("10 key M0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key M0 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key M0 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key M0 [9]", 2, params.getColumnPos(8));
        assertEquals("10 key M0 [A]", -2, params.getColumnPos(9));
        assertEquals("10 key M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // |[6] [7] [8] [9] [A]
    // |[1] [2] [3] [4] [5]
    public void testLayout10KeyL0() {
        MiniKeyboardParams params = new MiniKeyboardParams(10, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L0, KEYBOARD_WIDTH);
        assertEquals("10 key L0 columns", 5, params.mNumColumns);
        assertEquals("10 key L0 rows", 2, params.mNumRows);
        assertEquals("10 key L0 left", 0, params.mLeftKeys);
        assertEquals("10 key L0 right", 5, params.mRightKeys);
        assertEquals("10 key L0 [1]", 0, params.getColumnPos(0));
        assertEquals("10 key L0 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key L0 [3]", 2, params.getColumnPos(2));
        assertEquals("10 key L0 [4]", 3, params.getColumnPos(3));
        assertEquals("10 key L0 [5]", 4, params.getColumnPos(4));
        assertEquals("10 key L0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key L0 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key L0 [8]", 2, params.getColumnPos(7));
        assertEquals("10 key L0 [9]", 3, params.getColumnPos(8));
        assertEquals("10 key L0 [A]", 4, params.getColumnPos(9));
        assertEquals("10 key L0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key L0 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ [6] [7] [8] [9] [A]
    // |___ [1] [2] [3] [4] [5]
    public void testLayout10KeyL1() {
        MiniKeyboardParams params = new MiniKeyboardParams(10, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L1, KEYBOARD_WIDTH);
        assertEquals("10 key L1 columns", 5, params.mNumColumns);
        assertEquals("10 key L1 rows", 2, params.mNumRows);
        assertEquals("10 key L1 left", 0, params.mLeftKeys);
        assertEquals("10 key L1 right", 5, params.mRightKeys);
        assertEquals("10 key L1 [1]", 0, params.getColumnPos(0));
        assertEquals("10 key L1 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key L1 [3]", 2, params.getColumnPos(2));
        assertEquals("10 key L1 [4]", 3, params.getColumnPos(3));
        assertEquals("10 key L1 [5]", 4, params.getColumnPos(4));
        assertEquals("10 key L1 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key L1 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key L1 [8]", 2, params.getColumnPos(7));
        assertEquals("10 key L1 [9]", 3, params.getColumnPos(8));
        assertEquals("10 key L1 [A]", 4, params.getColumnPos(9));
        assertEquals("10 key L1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key L1 default", WIDTH * 0, params.getDefaultKeyCoordX());
    }

    // |___ ___ [8] [6] [7] [9] [A]
    // |___ ___ [3] [1] [2] [4] [5]
    public void testLayout10KeyL2() {
        MiniKeyboardParams params = new MiniKeyboardParams(10, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_L2, KEYBOARD_WIDTH);
        assertEquals("10 key L2 columns", 5, params.mNumColumns);
        assertEquals("10 key L2 rows", 2, params.mNumRows);
        assertEquals("10 key L2 left", 1, params.mLeftKeys);
        assertEquals("10 key L2 right", 4, params.mRightKeys);
        assertEquals("10 key L2 [1]", 0, params.getColumnPos(0));
        assertEquals("10 key L2 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key L2 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key L2 [4]", 2, params.getColumnPos(3));
        assertEquals("10 key L2 [5]", 3, params.getColumnPos(4));
        assertEquals("10 key L2 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key L2 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key L2 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key L2 [9]", 2, params.getColumnPos(8));
        assertEquals("10 key L2 [A]", 3, params.getColumnPos(9));
        assertEquals("10 key L2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key L2 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [A] [9] [8] [7] [6]|
    // [5] [4] [3] [2] [1]|
    public void testLayout10KeyR0() {
        MiniKeyboardParams params = new MiniKeyboardParams(10, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R0, KEYBOARD_WIDTH);
        assertEquals("10 key R0 columns", 5, params.mNumColumns);
        assertEquals("10 key R0 rows", 2, params.mNumRows);
        assertEquals("10 key R0 left", 4, params.mLeftKeys);
        assertEquals("10 key R0 right", 1, params.mRightKeys);
        assertEquals("10 key R0 [1]", 0, params.getColumnPos(0));
        assertEquals("10 key R0 [2]", -1, params.getColumnPos(1));
        assertEquals("10 key R0 [3]", -2, params.getColumnPos(2));
        assertEquals("10 key R0 [4]", -3, params.getColumnPos(3));
        assertEquals("10 key R0 [5]", -4, params.getColumnPos(4));
        assertEquals("10 key R0 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key R0 [7]", -1, params.getColumnPos(6));
        assertEquals("10 key R0 [8]", -2, params.getColumnPos(7));
        assertEquals("10 key R0 [9]", -3, params.getColumnPos(8));
        assertEquals("10 key R0 [A]", -4, params.getColumnPos(9));
        assertEquals("10 key R0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key R0 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [A] [9] [8] [7] [6] ___|
    // [5] [4] [3] [2] [1] ___|
    public void testLayout10KeyR1() {
        MiniKeyboardParams params = new MiniKeyboardParams(10, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R1, KEYBOARD_WIDTH);
        assertEquals("10 key R1 columns", 5, params.mNumColumns);
        assertEquals("10 key R1 rows", 2, params.mNumRows);
        assertEquals("10 key R1 left", 4, params.mLeftKeys);
        assertEquals("10 key R1 right", 1, params.mRightKeys);
        assertEquals("10 key R1 [1]", 0, params.getColumnPos(0));
        assertEquals("10 key R1 [2]", -1, params.getColumnPos(1));
        assertEquals("10 key R1 [3]", -2, params.getColumnPos(2));
        assertEquals("10 key R1 [4]", -3, params.getColumnPos(3));
        assertEquals("10 key R1 [5]", -4, params.getColumnPos(4));
        assertEquals("10 key R1 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key R1 [7]", -1, params.getColumnPos(6));
        assertEquals("10 key R1 [8]", -2, params.getColumnPos(7));
        assertEquals("10 key R1 [9]", -3, params.getColumnPos(8));
        assertEquals("10 key R1 [A]", -4, params.getColumnPos(9));
        assertEquals("10 key R1 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key R1 default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // [A] [9] [8] [6] [7] ___ ___|
    // [5] [4] [3] [1] [2] ___ ___|
    public void testLayout10KeyR2() {
        MiniKeyboardParams params = new MiniKeyboardParams(10, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_R2, KEYBOARD_WIDTH);
        assertEquals("10 key R2 columns", 5, params.mNumColumns);
        assertEquals("10 key R2 rows", 2, params.mNumRows);
        assertEquals("10 key R2 left", 3, params.mLeftKeys);
        assertEquals("10 key R2 right", 2, params.mRightKeys);
        assertEquals("10 key R2 [1]", 0, params.getColumnPos(0));
        assertEquals("10 key R2 [2]", 1, params.getColumnPos(1));
        assertEquals("10 key R2 [3]", -1, params.getColumnPos(2));
        assertEquals("10 key R2 [4]", -2, params.getColumnPos(3));
        assertEquals("10 key R2 [5]", -3, params.getColumnPos(4));
        assertEquals("10 key R2 [6]", 0, params.getColumnPos(5));
        assertEquals("10 key R2 [7]", 1, params.getColumnPos(6));
        assertEquals("10 key R2 [8]", -1, params.getColumnPos(7));
        assertEquals("10 key R2 [9]", -2, params.getColumnPos(8));
        assertEquals("10 key R2 [A]", -3, params.getColumnPos(9));
        assertEquals("10 key R2 adjust", 0, params.mTopRowAdjustment);
        assertEquals("10 key R2 default", WIDTH * 3, params.getDefaultKeyCoordX());
    }

    //   [B] [9] [A]
    // [7] [5] [6] [8]
    // [3] [1] [2] [4]
    public void testLayout11KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(11, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("11 key M0 columns", 4, params.mNumColumns);
        assertEquals("11 key M0 rows", 3, params.mNumRows);
        assertEquals("11 key M0 left", 1, params.mLeftKeys);
        assertEquals("11 key M0 right", 3, params.mRightKeys);
        assertEquals("11 key M0 [1]", 0, params.getColumnPos(0));
        assertEquals("11 key M0 [2]", 1, params.getColumnPos(1));
        assertEquals("11 key M0 [3]", -1, params.getColumnPos(2));
        assertEquals("11 key M0 [4]", 2, params.getColumnPos(3));
        assertEquals("11 key M0 [5]", 0, params.getColumnPos(4));
        assertEquals("11 key M0 [6]", 1, params.getColumnPos(5));
        assertEquals("11 key M0 [7]", -1, params.getColumnPos(6));
        assertEquals("11 key M0 [8]", 2, params.getColumnPos(7));
        assertEquals("11 key M0 [9]", 0, params.getColumnPos(8));
        assertEquals("11 key M0 [A]", 1, params.getColumnPos(9));
        assertEquals("11 key M0 [B]", -1, params.getColumnPos(10));
        assertEquals("11 key M0 adjust", 1, params.mTopRowAdjustment);
        assertEquals("11 key M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }

    // [B] [9] [A] [C]
    // [7] [5] [6] [8]
    // [3] [1] [2] [4]
    public void testLayout12KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(12, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("12 key M0 columns", 4, params.mNumColumns);
        assertEquals("12 key M0 rows", 3, params.mNumRows);
        assertEquals("12 key M0 left", 1, params.mLeftKeys);
        assertEquals("12 key M0 right", 3, params.mRightKeys);
        assertEquals("12 key M0 [1]", 0, params.getColumnPos(0));
        assertEquals("12 key M0 [2]", 1, params.getColumnPos(1));
        assertEquals("12 key M0 [3]", -1, params.getColumnPos(2));
        assertEquals("12 key M0 [4]", 2, params.getColumnPos(3));
        assertEquals("12 key M0 [5]", 0, params.getColumnPos(4));
        assertEquals("12 key M0 [6]", 1, params.getColumnPos(5));
        assertEquals("12 key M0 [7]", -1, params.getColumnPos(6));
        assertEquals("12 key M0 [8]", 2, params.getColumnPos(7));
        assertEquals("12 key M0 [9]", 0, params.getColumnPos(8));
        assertEquals("12 key M0 [A]", 1, params.getColumnPos(9));
        assertEquals("12 key M0 [B]", -1, params.getColumnPos(10));
        assertEquals("12 key M0 [C]", 2, params.getColumnPos(11));
        assertEquals("12 key M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("12 key M0 default", WIDTH * 1, params.getDefaultKeyCoordX());
    }


    //     [D] [B] [C]
    // [A] [8] [6] [7] [9]
    // [5] [3] [1] [2] [4]
    public void testLayout13KeyM0() {
        MiniKeyboardParams params = new MiniKeyboardParams(13, MAX_COLUMNS, WIDTH,
                HEIGHT, XPOS_M0, KEYBOARD_WIDTH);
        assertEquals("13 key M0 columns", 5, params.mNumColumns);
        assertEquals("13 key M0 rows", 3, params.mNumRows);
        assertEquals("13 key M0 left", 2, params.mLeftKeys);
        assertEquals("13 key M0 right", 3, params.mRightKeys);
        assertEquals("13 key M0 [1]", 0, params.getColumnPos(0));
        assertEquals("13 key M0 [2]", 1, params.getColumnPos(1));
        assertEquals("13 key M0 [3]", -1, params.getColumnPos(2));
        assertEquals("13 key M0 [4]", 2, params.getColumnPos(3));
        assertEquals("13 key M0 [5]", -2, params.getColumnPos(4));
        assertEquals("13 key M0 [6]", 0, params.getColumnPos(5));
        assertEquals("13 key M0 [7]", 1, params.getColumnPos(6));
        assertEquals("13 key M0 [8]", -1, params.getColumnPos(7));
        assertEquals("13 key M0 [9]", 2, params.getColumnPos(8));
        assertEquals("13 key M0 [A]", -2, params.getColumnPos(9));
        assertEquals("13 key M0 [B]", 0, params.getColumnPos(10));
        assertEquals("13 key M0 [C]", 1, params.getColumnPos(11));
        assertEquals("13 key M0 [D]", -1, params.getColumnPos(12));
        assertEquals("13 key M0 adjust", 0, params.mTopRowAdjustment);
        assertEquals("13 key M0 default", WIDTH * 2, params.getDefaultKeyCoordX());
    }
}
