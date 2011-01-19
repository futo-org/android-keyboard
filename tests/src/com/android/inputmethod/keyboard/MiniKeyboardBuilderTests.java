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

import com.android.inputmethod.keyboard.MiniKeyboardBuilder.MiniKeyboardLayoutParams;

import android.test.AndroidTestCase;

public class MiniKeyboardBuilderTests extends AndroidTestCase {
    private static final int MAX_COLUMNS = 5;
    private static final int WIDTH = 10;
    private static final int HEIGHT = 10;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testLayoutError() {
        MiniKeyboardLayoutParams params = null;
        try {
            params = new MiniKeyboardLayoutParams(
                    10, MAX_COLUMNS + 1, WIDTH, HEIGHT,
                    WIDTH * 2, WIDTH * MAX_COLUMNS);
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
    public void testLayout1Key() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                1, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 5, WIDTH * 10);
        assertEquals("1 key columns", 1, params.mNumColumns);
        assertEquals("1 key rows", 1, params.mNumRows);
        assertEquals("1 key left", 0, params.mLeftKeys);
        assertEquals("1 key right", 1, params.mRightKeys);
        assertEquals("1 key [1]", 0, params.getColumnPos(0));
        assertEquals("1 key centering", false, params.mTopRowNeedsCentering);
        assertEquals("1 key default", 0, params.getDefaultKeyCoordX());
    }

    // [1] [2]
    public void testLayout2Key() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                2, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 5, WIDTH * 10);
        assertEquals("2 key columns", 2, params.mNumColumns);
        assertEquals("2 key rows", 1, params.mNumRows);
        assertEquals("2 key left", 0, params.mLeftKeys);
        assertEquals("2 key right", 2, params.mRightKeys);
        assertEquals("2 key [1]", 0, params.getColumnPos(0));
        assertEquals("2 key [2]", 1, params.getColumnPos(1));
        assertEquals("2 key centering", false, params.mTopRowNeedsCentering);
        assertEquals("2 key default", 0, params.getDefaultKeyCoordX());
    }

    // [3] [1] [2]
    public void testLayout3Key() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                3, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 5, WIDTH * 10);
        assertEquals("3 key columns", 3, params.mNumColumns);
        assertEquals("3 key rows", 1, params.mNumRows);
        assertEquals("3 key left", 1, params.mLeftKeys);
        assertEquals("3 key right", 2, params.mRightKeys);
        assertEquals("3 key [1]", 0, params.getColumnPos(0));
        assertEquals("3 key [2]", 1, params.getColumnPos(1));
        assertEquals("3 key [3]", -1, params.getColumnPos(2));
        assertEquals("3 key centering", false, params.mTopRowNeedsCentering);
        assertEquals("3 key default", WIDTH, params.getDefaultKeyCoordX());
    }

    // [3] [1] [2] [4]
    public void testLayout4Key() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                4, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 5, WIDTH * 10);
        assertEquals("4 key columns", 4, params.mNumColumns);
        assertEquals("4 key rows", 1, params.mNumRows);
        assertEquals("4 key left", 1, params.mLeftKeys);
        assertEquals("4 key right", 3, params.mRightKeys);
        assertEquals("4 key [1]", 0, params.getColumnPos(0));
        assertEquals("4 key [2]", 1, params.getColumnPos(1));
        assertEquals("4 key [3]", -1, params.getColumnPos(2));
        assertEquals("4 key [4]", 2, params.getColumnPos(3));
        assertEquals("4 key centering", false, params.mTopRowNeedsCentering);
        assertEquals("4 key default", WIDTH, params.getDefaultKeyCoordX());
    }

    // [5] [3] [1] [2] [4]
    public void testLayout5Key() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                5, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 5, WIDTH * 10);
        assertEquals("5 key columns", 5, params.mNumColumns);
        assertEquals("5 key rows", 1, params.mNumRows);
        assertEquals("5 key left", 2, params.mLeftKeys);
        assertEquals("5 key right", 3, params.mRightKeys);
        assertEquals("5 key [1]", 0, params.getColumnPos(0));
        assertEquals("5 key [2]", 1, params.getColumnPos(1));
        assertEquals("5 key [3]", -1, params.getColumnPos(2));
        assertEquals("5 key [4]", 2, params.getColumnPos(3));
        assertEquals("5 key [5]", -2, params.getColumnPos(4));
        assertEquals("5 key centering", false, params.mTopRowNeedsCentering);
        assertEquals("5 key default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //         [6]
    // [5] [3] [1] [2] [4]
    public void testLayout6Key() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                6, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 5, WIDTH * 10);
        assertEquals("6 key columns", 5, params.mNumColumns);
        assertEquals("6 key rows", 2, params.mNumRows);
        assertEquals("6 key left", 2, params.mLeftKeys);
        assertEquals("6 key right", 3, params.mRightKeys);
        assertEquals("6 key [1]", 0, params.getColumnPos(0));
        assertEquals("6 key [2]", 1, params.getColumnPos(1));
        assertEquals("6 key [3]", -1, params.getColumnPos(2));
        assertEquals("6 key [4]", 2, params.getColumnPos(3));
        assertEquals("6 key [5]", -2, params.getColumnPos(4));
        assertEquals("6 key [6]", 0, params.getColumnPos(5));
        assertEquals("6 key centering", false, params.mTopRowNeedsCentering);
        assertEquals("6 key default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //       [6] [7]
    // [5] [3] [1] [2] [4]
    public void testLayout7Key() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                7, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 5, WIDTH * 10);
        assertEquals("7 key columns", 5, params.mNumColumns);
        assertEquals("7 key rows", 2, params.mNumRows);
        assertEquals("7 key left", 2, params.mLeftKeys);
        assertEquals("7 key right", 3, params.mRightKeys);
        assertEquals("7 key [1]", 0, params.getColumnPos(0));
        assertEquals("7 key [2]", 1, params.getColumnPos(1));
        assertEquals("7 key [3]", -1, params.getColumnPos(2));
        assertEquals("7 key [4]", 2, params.getColumnPos(3));
        assertEquals("7 key [5]", -2, params.getColumnPos(4));
        assertEquals("7 key [6]", 0, params.getColumnPos(5));
        assertEquals("7 key [7]", 1, params.getColumnPos(6));
        assertEquals("7 key centering", true, params.mTopRowNeedsCentering);
        assertEquals("7 key default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //     [8] [6] [7]
    // [5] [3] [1] [2] [4]
    public void testLayout8Key() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                8, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 5, WIDTH * 10);
        assertEquals("8 key columns", 5, params.mNumColumns);
        assertEquals("8 key rows", 2, params.mNumRows);
        assertEquals("8 key left", 2, params.mLeftKeys);
        assertEquals("8 key right", 3, params.mRightKeys);
        assertEquals("8 key [1]", 0, params.getColumnPos(0));
        assertEquals("8 key [2]", 1, params.getColumnPos(1));
        assertEquals("8 key [3]", -1, params.getColumnPos(2));
        assertEquals("8 key [4]", 2, params.getColumnPos(3));
        assertEquals("8 key [5]", -2, params.getColumnPos(4));
        assertEquals("8 key [6]", 0, params.getColumnPos(5));
        assertEquals("8 key [7]", 1, params.getColumnPos(6));
        assertEquals("8 key [8]", -1, params.getColumnPos(7));
        assertEquals("8 key centering", false, params.mTopRowNeedsCentering);
        assertEquals("8 key default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    //   [8] [6] [7] [9]
    // [5] [3] [1] [2] [4]
    public void testLayout9Key() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                9, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 5, WIDTH * 10);
        assertEquals("9 key columns", 5, params.mNumColumns);
        assertEquals("9 key rows", 2, params.mNumRows);
        assertEquals("9 key left", 2, params.mLeftKeys);
        assertEquals("9 key right", 3, params.mRightKeys);
        assertEquals("9 key [1]", 0, params.getColumnPos(0));
        assertEquals("9 key [2]", 1, params.getColumnPos(1));
        assertEquals("9 key [3]", -1, params.getColumnPos(2));
        assertEquals("9 key [4]", 2, params.getColumnPos(3));
        assertEquals("9 key [5]", -2, params.getColumnPos(4));
        assertEquals("9 key [6]", 0, params.getColumnPos(5));
        assertEquals("9 key [7]", 1, params.getColumnPos(6));
        assertEquals("9 key [8]", -1, params.getColumnPos(7));
        assertEquals("9 key [9]", 2, params.getColumnPos(8));
        assertEquals("9 key centering", true, params.mTopRowNeedsCentering);
        assertEquals("9 key default", WIDTH * 2, params.getDefaultKeyCoordX());
    }

    // Nine keys test.  There is no key space for mini keyboard at left of the parent key.
    //   [6] [7] [8] [9]
    // [1] [2] [3] [4] [5]
    public void testLayout9KeyLeft() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                9, MAX_COLUMNS, WIDTH, HEIGHT,
                0, WIDTH * 10);
        assertEquals("9 key left columns", 5, params.mNumColumns);
        assertEquals("9 key left rows", 2, params.mNumRows);
        assertEquals("9 key left left", 0, params.mLeftKeys);
        assertEquals("9 key left right", 5, params.mRightKeys);
        assertEquals("9 key left [1]", 0, params.getColumnPos(0));
        assertEquals("9 key left [2]", 1, params.getColumnPos(1));
        assertEquals("9 key left [3]", 2, params.getColumnPos(2));
        assertEquals("9 key left [4]", 3, params.getColumnPos(3));
        assertEquals("9 key left [5]", 4, params.getColumnPos(4));
        assertEquals("9 key left [6]", 0, params.getColumnPos(5));
        assertEquals("9 key left [7]", 1, params.getColumnPos(6));
        assertEquals("9 key left [8]", 2, params.getColumnPos(7));
        assertEquals("9 key left [9]", 3, params.getColumnPos(8));
        assertEquals("9 key left centering", true, params.mTopRowNeedsCentering);
        assertEquals("9 key left default", 0, params.getDefaultKeyCoordX());
    }

    // Nine keys test.  There is only one key space for mini keyboard at left of the parent key.
    //   [8] [6] [7] [9]
    // [3] [1] [2] [4] [5]
    public void testLayout9KeyNearLeft() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                9, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH, WIDTH * 10);
        assertEquals("9 key near left columns", 5, params.mNumColumns);
        assertEquals("9 key near left rows", 2, params.mNumRows);
        assertEquals("9 key near left left", 1, params.mLeftKeys);
        assertEquals("9 key near left right", 4, params.mRightKeys);
        assertEquals("9 key near left [1]", 0, params.getColumnPos(0));
        assertEquals("9 key near left [2]", 1, params.getColumnPos(1));
        assertEquals("9 key near left [3]", -1, params.getColumnPos(2));
        assertEquals("9 key near left [4]", 2, params.getColumnPos(3));
        assertEquals("9 key near left [5]", 3, params.getColumnPos(4));
        assertEquals("9 key near left [6]", 0, params.getColumnPos(5));
        assertEquals("9 key near left [7]", 1, params.getColumnPos(6));
        assertEquals("9 key near left [8]", -1, params.getColumnPos(7));
        assertEquals("9 key near left [9]", 2, params.getColumnPos(8));
        assertEquals("9 key near left centering", true, params.mTopRowNeedsCentering);
        assertEquals("9 key near left default", WIDTH, params.getDefaultKeyCoordX());
    }


    // Nine keys test.  There is no key space for mini keyboard at right of the parent key.
    //   [9] [8] [7] [6]
    // [5] [4] [3] [2] [1]
    public void testLayout9KeyRight() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                9, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 9, WIDTH * 10);
        assertEquals("9 key right columns", 5, params.mNumColumns);
        assertEquals("9 key right rows", 2, params.mNumRows);
        assertEquals("9 key right left", 4, params.mLeftKeys);
        assertEquals("9 key right right", 1, params.mRightKeys);
        assertEquals("9 key right [1]", 0, params.getColumnPos(0));
        assertEquals("9 key right [2]", -1, params.getColumnPos(1));
        assertEquals("9 key right [3]", -2, params.getColumnPos(2));
        assertEquals("9 key right [4]", -3, params.getColumnPos(3));
        assertEquals("9 key right [5]", -4, params.getColumnPos(4));
        assertEquals("9 key right [6]", 0, params.getColumnPos(5));
        assertEquals("9 key right [7]", -1, params.getColumnPos(6));
        assertEquals("9 key right [8]", -2, params.getColumnPos(7));
        assertEquals("9 key right [9]", -3, params.getColumnPos(8));
        assertEquals("9 key right centering", true, params.mTopRowNeedsCentering);
        assertEquals("9 key right default", WIDTH * 4, params.getDefaultKeyCoordX());
    }

    // Nine keys test.  There is only one key space for mini keyboard at right of the parent key.
    //   [9] [8] [6] [7]
    // [5] [4] [3] [1] [2]
    public void testLayout9KeyNearRight() {
        MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                9, MAX_COLUMNS, WIDTH, HEIGHT,
                WIDTH * 8, WIDTH * 10);
        assertEquals("9 key near right columns", 5, params.mNumColumns);
        assertEquals("9 key near right rows", 2, params.mNumRows);
        assertEquals("9 key near right left", 3, params.mLeftKeys);
        assertEquals("9 key near right right", 2, params.mRightKeys);
        assertEquals("9 key near right [1]", 0, params.getColumnPos(0));
        assertEquals("9 key near right [2]", 1, params.getColumnPos(1));
        assertEquals("9 key near right [3]", -1, params.getColumnPos(2));
        assertEquals("9 key near right [4]", -2, params.getColumnPos(3));
        assertEquals("9 key near right [5]", -3, params.getColumnPos(4));
        assertEquals("9 key near right [6]", 0, params.getColumnPos(5));
        assertEquals("9 key near right [7]", 1, params.getColumnPos(6));
        assertEquals("9 key near right [8]", -1, params.getColumnPos(7));
        assertEquals("9 key near right [9]", -2, params.getColumnPos(8));
        assertEquals("9 key near right centering", true, params.mTopRowNeedsCentering);
        assertEquals("9 key near right default", WIDTH * 3, params.getDefaultKeyCoordX());
    }
}
