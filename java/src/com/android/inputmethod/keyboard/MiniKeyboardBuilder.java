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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Rect;

import java.util.List;

public class MiniKeyboardBuilder {
    private final Resources mRes;
    private final Keyboard mKeyboard;
    private final CharSequence[] mPopupCharacters;
    private final int mMiniKeyboardKeyHorizontalPadding;
    private final int mKeyWidth;
    private final int mMaxColumns;
    private final int mNumRows;
    private int mColPos;
    private int mRowPos;
    private int mX;
    private int mY;

    public MiniKeyboardBuilder(KeyboardView view, int layoutTemplateResId, Key popupKey) {
        final Context context = view.getContext();
        mRes = context.getResources();
        final Keyboard keyboard = new Keyboard(context, layoutTemplateResId, null);
        mKeyboard = keyboard;
        mPopupCharacters = popupKey.mPopupCharacters;
        mMiniKeyboardKeyHorizontalPadding = (int)mRes.getDimension(
                R.dimen.mini_keyboard_key_horizontal_padding);
        mKeyWidth = getMaxKeyWidth(view, mPopupCharacters, mKeyboard.getKeyWidth());
        final int maxColumns = popupKey.mMaxPopupColumn;
        mMaxColumns = maxColumns;
        final int numKeys = mPopupCharacters.length;
        int numRows = numKeys / maxColumns;
        if (numKeys % maxColumns != 0) numRows++;
        mNumRows = numRows;
        keyboard.setHeight((keyboard.getRowHeight() + keyboard.getVerticalGap()) * numRows
                - keyboard.getVerticalGap());
        if (numRows > 1) {
            mColPos = numKeys % maxColumns;
            if (mColPos > 0) mColPos = maxColumns - mColPos;
            // Centering top-row keys.
            mX = mColPos * (mKeyWidth + keyboard.getHorizontalGap()) / 2;
        }
        mKeyboard.setMinWidth(0);
    }

    private int getMaxKeyWidth(KeyboardView view, CharSequence[] popupCharacters, int minKeyWidth) {
        Paint paint = null;
        Rect bounds = null;
        int maxWidth = 0;
        for (CharSequence popupSpec : popupCharacters) {
            final CharSequence label = PopupCharactersParser.getLabel(popupSpec.toString());
            // If the label is single letter, minKeyWidth is enough to hold the label.
            if (label.length() > 1) {
                if (paint == null) {
                    paint = new Paint();
                    paint.setAntiAlias(true);
                }
                final int labelSize = view.getLabelSizeAndSetPaint(label, 0, paint);
                paint.setTextSize(labelSize);
                if (bounds == null) bounds = new Rect();
                paint.getTextBounds(label.toString(), 0, label.length(), bounds);
                if (maxWidth < bounds.width())
                    maxWidth = bounds.width();
            }
        }
        return Math.max(minKeyWidth, maxWidth + mMiniKeyboardKeyHorizontalPadding);
    }

    public Keyboard build() {
        final Keyboard keyboard = mKeyboard;
        final List<Key> keys = keyboard.getKeys();
        for (CharSequence label : mPopupCharacters) {
            refresh();
            final Key key = new Key(mRes, keyboard, label, mX, mY, mKeyWidth, getRowFlags());
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
        mX += mKeyWidth + keyboard.getHorizontalGap();
        if (mX > keyboard.getMinWidth())
            keyboard.setMinWidth(mX);
        mColPos++;
    }
}
