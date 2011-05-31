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

import android.content.Context;

import java.util.List;

public class MiniKeyboard extends Keyboard {
    private int mDefaultKeyCoordX;

    public MiniKeyboard(Context context, int xmlLayoutResId, KeyboardId id) {
        super(context, xmlLayoutResId, id);
    }

    public void setDefaultCoordX(int pos) {
        mDefaultKeyCoordX = pos;
    }

    public int getDefaultCoordX() {
        return mDefaultKeyCoordX;
    }

    public boolean isOneRowKeys() {
        final List<Key> keys = getKeys();
        if (keys.size() == 0) return false;
        final int edgeFlags = keys.get(0).mEdgeFlags;
        // HACK: The first key of mini keyboard which was inflated from xml and has multiple rows,
        // does not have both top and bottom edge flags on at the same time.  On the other hand,
        // the first key of mini keyboard that was created with popupCharacters must have both top
        // and bottom edge flags on.
        // When you want to use one row mini-keyboard from xml file, make sure that the row has
        // both top and bottom edge flags set.
        return (edgeFlags & Keyboard.EDGE_TOP) != 0
                && (edgeFlags & Keyboard.EDGE_BOTTOM) != 0;
    }
}
