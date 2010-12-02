/*
 * Copyright (C) 2010 Google Inc.
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

import com.android.inputmethod.latin.R;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.Xml;

/**
 * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
 * Some of the key size defaults can be overridden per row from what the {@link Keyboard}
 * defines.
 */
public class Row {
    /** Default width of a key in this row. */
    public int defaultWidth;
    /** Default height of a key in this row. */
    public int defaultHeight;
    /** Default horizontal gap between keys in this row. */
    public int defaultHorizontalGap;
    /** Vertical gap following this row. */
    public int verticalGap;
    /**
     * Edge flags for this row of keys. Possible values that can be assigned are
     * {@link Keyboard#EDGE_TOP EDGE_TOP} and {@link Keyboard#EDGE_BOTTOM EDGE_BOTTOM}
     */
    public int rowEdgeFlags;

    final Keyboard parent;

    Row(Keyboard parent) {
        this.parent = parent;
    }

    public Row(Resources res, Keyboard parent, XmlResourceParser parser) {
        this.parent = parent;
        TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        defaultWidth = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_keyWidth,
                parent.mDisplayWidth, parent.mDefaultWidth);
        defaultHeight = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_keyHeight,
                parent.mDisplayHeight, parent.mDefaultHeight);
        defaultHorizontalGap = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_horizontalGap,
                parent.mDisplayWidth, parent.mDefaultHorizontalGap);
        verticalGap = KeyboardParser.getDimensionOrFraction(a,
                R.styleable.Keyboard_verticalGap,
                parent.mDisplayHeight, parent.mDefaultVerticalGap);
        a.recycle();
        a = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Row);
        rowEdgeFlags = a.getInt(R.styleable.Keyboard_Row_rowEdgeFlags, 0);
    }
}
