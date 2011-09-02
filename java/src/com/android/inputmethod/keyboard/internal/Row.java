/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.Xml;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.R;

/**
 * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
 * Some of the key size defaults can be overridden per row from what the {@link Keyboard}
 * defines.
 */
public class Row {
    /** Default width of a key in this row. */
    public final int mDefaultKeyWidth;
    /** Default height of a key in this row. */
    public final int mRowHeight;

    public final int mCurrentY;
    // Will be updated by {@link Key}'s constructor.
    public int mCurrentX;

    public Row(Resources res, KeyboardParams params, XmlResourceParser parser, int y) {
        final int keyboardWidth = params.mWidth;
        final int keyboardHeight = params.mHeight;
        TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        mDefaultKeyWidth = KeyboardBuilder.getDimensionOrFraction(a,
                R.styleable.Keyboard_keyWidth, keyboardWidth, params.mDefaultKeyWidth);
        mRowHeight = KeyboardBuilder.getDimensionOrFraction(a,
                R.styleable.Keyboard_rowHeight, keyboardHeight, params.mDefaultRowHeight);
        a.recycle();

        mCurrentY = y;
        mCurrentX = 0;
    }
}
