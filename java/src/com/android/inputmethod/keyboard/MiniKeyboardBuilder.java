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

package com.android.inputmethod.keyboard;

import com.android.inputmethod.latin.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Rect;

import java.util.List;

public class MiniKeyboardBuilder {
    private final Resources mRes;
    private final MiniKeyboard mKeyboard;
    private final CharSequence[] mPopupCharacters;
    private final MiniKeyboardLayoutParams mParams;

    /* package */ static class MiniKeyboardLayoutParams {
        public final int mKeyWidth;
        public final int mRowHeight;
        /* package */ final int mTopRowAdjustment;
        public final int mNumRows;
        public final int mNumColumns;
        public final int mLeftKeys;
        public final int mRightKeys; // includes default key.

        /**
         * The object holding mini keyboard layout parameters.
         *
         * @param numKeys number of keys in this mini keyboard.
         * @param maxColumns number of maximum columns of this mini keyboard.
         * @param keyWidth mini keyboard key width in pixel, including horizontal gap.
         * @param rowHeight mini keyboard row height in pixel, including vertical gap.
         * @param coordXInParent coordinate x of the popup key in parent keyboard.
         * @param parentKeyboardWidth parent keyboard width in pixel.
         */
        public MiniKeyboardLayoutParams(int numKeys, int maxColumns, int keyWidth, int rowHeight,
                int coordXInParent, int parentKeyboardWidth) {
            if (parentKeyboardWidth / keyWidth < maxColumns)
                throw new IllegalArgumentException("Keyboard is too small to hold mini keyboard: "
                        + parentKeyboardWidth + " " + keyWidth + " " + maxColumns);
            mKeyWidth = keyWidth;
            mRowHeight = rowHeight;

            final int numRows = (numKeys + maxColumns - 1) / maxColumns;
            mNumRows = numRows;
            final int numColumns = getOptimizedColumns(numKeys, maxColumns);
            mNumColumns = numColumns;

            final int numLeftKeys = (numColumns - 1) / 2;
            final int numRightKeys = numColumns - numLeftKeys; // including default key.
            final int maxLeftKeys = coordXInParent / keyWidth;
            final int maxRightKeys = Math.max(1, (parentKeyboardWidth - coordXInParent) / keyWidth);
            int leftKeys, rightKeys;
            if (numLeftKeys > maxLeftKeys) {
                leftKeys = maxLeftKeys;
                rightKeys = numColumns - maxLeftKeys;
            } else if (numRightKeys > maxRightKeys) {
                leftKeys = numColumns - maxRightKeys;
                rightKeys = maxRightKeys;
            } else {
                leftKeys = numLeftKeys;
                rightKeys = numRightKeys;
            }
            // Shift right if the left edge of mini keyboard is on the edge of parent keyboard
            // unless the parent key is on the left edge.
            if (leftKeys * keyWidth >= coordXInParent && leftKeys > 0) {
                leftKeys--;
                rightKeys++;
            }
            // Shift left if the right edge of mini keyboard is on the edge of parent keyboard
            // unless the parent key is on the right edge.
            if (rightKeys * keyWidth + coordXInParent >= parentKeyboardWidth && rightKeys > 1) {
                leftKeys++;
                rightKeys--;
            }
            mLeftKeys = leftKeys;
            mRightKeys = rightKeys;

            // Centering of the top row.
            final boolean onEdge = (leftKeys == 0 || rightKeys == 1);
            if (numRows < 2 || onEdge || getTopRowEmptySlots(numKeys, numColumns) % 2 == 0) {
                mTopRowAdjustment = 0;
            } else if (mLeftKeys < mRightKeys - 1) {
                mTopRowAdjustment = 1;
            } else {
                mTopRowAdjustment = -1;
            }
        }

        // Return key position according to column count (0 is default).
        /* package */ int getColumnPos(int n) {
            final int col = n % mNumColumns;
            if (col == 0) {
                // default position.
                return 0;
            }
            int pos = 0;
            int right = 1; // include default position key.
            int left = 0;
            int i = 0;
            while (true) {
                // Assign right key if available.
                if (right < mRightKeys) {
                    pos = right;
                    right++;
                    i++;
                }
                if (i >= col)
                    break;
                // Assign left key if available.
                if (left < mLeftKeys) {
                    left++;
                    pos = -left;
                    i++;
                }
                if (i >= col)
                    break;
            }
            return pos;
        }

        private static int getTopRowEmptySlots(int numKeys, int numColumns) {
            final int remainingKeys = numKeys % numColumns;
            if (remainingKeys == 0) {
                return 0;
            } else {
                return numColumns - remainingKeys;
            }
        }

        private int getOptimizedColumns(int numKeys, int maxColumns) {
            int numColumns = Math.min(numKeys, maxColumns);
            while (getTopRowEmptySlots(numKeys, numColumns) >= mNumRows) {
                numColumns--;
            }
            return numColumns;
        }

        public int getDefaultKeyCoordX() {
            return mLeftKeys * mKeyWidth;
        }

        public int getX(int n, int row) {
            final int x = getColumnPos(n) * mKeyWidth + getDefaultKeyCoordX();
            if (isTopRow(row)) {
                return x + mTopRowAdjustment * (mKeyWidth / 2);
            }
            return x;
        }

        public int getY(int row) {
            return (mNumRows - 1 - row) * mRowHeight;
        }

        public int getRowFlags(int row) {
            int rowFlags = 0;
            if (row == 0) rowFlags |= Keyboard.EDGE_TOP;
            if (isTopRow(row)) rowFlags |= Keyboard.EDGE_BOTTOM;
            return rowFlags;
        }

        private boolean isTopRow(int rowCount) {
            return rowCount == mNumRows - 1;
        }
    }

    public MiniKeyboardBuilder(KeyboardView view, int layoutTemplateResId, Key parentKey,
            Keyboard parentKeyboard) {
        final Context context = view.getContext();
        mRes = context.getResources();
        final MiniKeyboard keyboard = new MiniKeyboard(context, layoutTemplateResId, null);
        mKeyboard = keyboard;
        mPopupCharacters = parentKey.mPopupCharacters;

        final int keyWidth = getMaxKeyWidth(view, mPopupCharacters, keyboard.getKeyWidth());
        final MiniKeyboardLayoutParams params = new MiniKeyboardLayoutParams(
                mPopupCharacters.length, parentKey.mMaxPopupColumn,
                keyWidth, parentKeyboard.getRowHeight(),
                parentKey.mX + (parentKey.mWidth + parentKey.mGap) / 2 - keyWidth / 2,
                view.getMeasuredWidth());
        mParams = params;

        keyboard.setRowHeight(params.mRowHeight);
        keyboard.setHeight(params.mNumRows * params.mRowHeight);
        keyboard.setMinWidth(params.mNumColumns * params.mKeyWidth);
        keyboard.setDefaultCoordX(params.getDefaultKeyCoordX() + params.mKeyWidth / 2);
    }

    private static int getMaxKeyWidth(KeyboardView view, CharSequence[] popupCharacters,
            int minKeyWidth) {
        Paint paint = null;
        Rect bounds = null;
        int maxWidth = 0;
        for (CharSequence popupSpec : popupCharacters) {
            final CharSequence label = PopupCharactersParser.getLabel(popupSpec.toString());
            // If the label is single letter, minKeyWidth is enough to hold the label.
            if (label != null && label.length() > 1) {
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
        final int horizontalPadding = (int)view.getContext().getResources().getDimension(
                R.dimen.mini_keyboard_key_horizontal_padding);
        return Math.max(minKeyWidth, maxWidth + horizontalPadding);
    }

    public MiniKeyboard build() {
        final MiniKeyboard keyboard = mKeyboard;
        final List<Key> keys = keyboard.getKeys();
        final MiniKeyboardLayoutParams params = mParams;
        for (int n = 0; n < mPopupCharacters.length; n++) {
            final CharSequence label = mPopupCharacters[n];
            final int row = n / params.mNumColumns;
            final Key key = new Key(mRes, keyboard, label, params.getX(n, row), params.getY(row),
                    params.mKeyWidth, params.mRowHeight, params.getRowFlags(row));
            keys.add(key);
        }
        return keyboard;
    }
}
