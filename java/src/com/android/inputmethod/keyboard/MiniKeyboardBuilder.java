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

import android.content.Context;
import android.content.res.Resources;

import java.util.List;

public class MiniKeyboardBuilder {
    private final Resources mRes;
    private final Keyboard mKeyboard;
    private final CharSequence[] mPopupCharacters;
    private final int mMaxColumns;
    private final int mNumRows;
    private int mColPos;
    private int mRowPos;
    private int mX;
    private int mY;

    public MiniKeyboardBuilder(Context context, int layoutTemplateResId, Key popupKey) {
        mRes = context.getResources();
        final Keyboard keyboard = new Keyboard(context, layoutTemplateResId, null);
        mKeyboard = keyboard;
        mPopupCharacters = popupKey.mPopupCharacters;
        final int numKeys = mPopupCharacters.length;
        final int maxColumns = popupKey.mMaxPopupColumn;
        int numRows = numKeys / maxColumns;
        if (numKeys % maxColumns != 0) numRows++;
        mMaxColumns = maxColumns;
        mNumRows = numRows;
        keyboard.setHeight((keyboard.getRowHeight() + keyboard.getVerticalGap()) * numRows
                - keyboard.getVerticalGap());
        // TODO: To determine key width we should pay attention to key label length.
        if (numRows > 1) {
            mColPos = numKeys % maxColumns;
            if (mColPos > 0) mColPos = maxColumns - mColPos;
            // Centering top-row keys.
            mX = mColPos * (keyboard.getKeyWidth() + keyboard.getHorizontalGap()) / 2;
        }
        mKeyboard.setMinWidth(0);
    }

    public Keyboard build() {
        final Keyboard keyboard = mKeyboard;
        final List<Key> keys = keyboard.getKeys();
        for (CharSequence label : mPopupCharacters) {
            refresh();
            final Key key = new Key(mRes, keyboard, label, mX, mY, getRowFlags());
            keys.add(key);
            advance();
        }
        return keyboard;
    }

    private int getRowFlags() {
        final int rowPos = mRowPos;
        int rowFlags = 0;
        if (rowPos == 0) rowFlags |= Keyboard.EDGE_TOP;
        if (rowPos == mNumRows - 1) rowFlags |= Keyboard.EDGE_BOTTOM;
        return rowFlags;
    }

    private void refresh() {
        if (mColPos >= mMaxColumns) {
            final Keyboard keyboard = mKeyboard;
            // TODO: Allocate key position depending the precedence of popup characters.
            mX = 0;
            mY += keyboard.getRowHeight() + keyboard.getVerticalGap();
            mColPos = 0;
            mRowPos++;
        }
    }

    private void advance() {
        final Keyboard keyboard = mKeyboard;
        // TODO: Allocate key position depending the precedence of popup characters.
        mX += keyboard.getKeyWidth() + keyboard.getHorizontalGap();
        if (mX > keyboard.getMinWidth())
            keyboard.setMinWidth(mX);
        mColPos++;
    }
}
