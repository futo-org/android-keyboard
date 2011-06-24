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
    public final int mDefaultWidth;
    /** Default height of a key in this row. */
    public final int mDefaultHeight;
    /** Default horizontal gap between keys in this row. */
    public final int mDefaultHorizontalGap;
    /** Vertical gap following this row. */
    public final int mVerticalGap;
    /**
     * Edge flags for this row of keys. Possible values that can be assigned are
     * {@link Keyboard#EDGE_TOP EDGE_TOP} and {@link Keyboard#EDGE_BOTTOM EDGE_BOTTOM}
     */
    public final int mRowEdgeFlags;

    private final Keyboard mKeyboard;

    public Row(Resources res, Keyboard keyboard, XmlResourceParser parser) {
        this.mKeyboard = keyboard;
        final int keyboardWidth = keyboard.getDisplayWidth();
        final int keyboardHeight = keyboard.getKeyboardHeight();
        TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        mDefaultWidth = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_keyWidth, keyboardWidth, keyboard.getKeyWidth());
        mDefaultHeight = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_rowHeight, keyboardHeight, keyboard.getRowHeight());
        mDefaultHorizontalGap = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_horizontalGap, keyboardWidth, keyboard.getHorizontalGap());
        mVerticalGap = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_verticalGap, keyboardHeight, keyboard.getVerticalGap());
        a.recycle();
        a = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Row);
        mRowEdgeFlags = a.getInt(R.styleable.Keyboard_Row_rowEdgeFlags, 0);
        a.recycle();
    }

    public Keyboard getKeyboard() {
        return mKeyboard;
    }
}
