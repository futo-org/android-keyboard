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

package com.android.inputmethod.latin;

import android.graphics.Paint;
import android.text.TextUtils;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

public class MoreSuggestions extends Keyboard {
    private static final boolean DBG = LatinImeLogger.sDBG;

    public static final int SUGGESTION_CODE_BASE = 1024;

    private MoreSuggestions(Builder.MoreSuggestionsParam params) {
        super(params);
    }

    public static class Builder extends KeyboardBuilder<Builder.MoreSuggestionsParam> {
        private final MoreSuggestionsView mPaneView;
        private SuggestedWords mSuggestions;
        private int mFromPos;
        private int mToPos;

        public static class MoreSuggestionsParam extends KeyboardParams {
            private final int[] mWidths = new int[SuggestionsView.MAX_SUGGESTIONS];
            private final int[] mRowNumbers = new int[SuggestionsView.MAX_SUGGESTIONS];
            private final int[] mColumnOrders = new int[SuggestionsView.MAX_SUGGESTIONS];
            private final int[] mNumColumnsInRow = new int[SuggestionsView.MAX_SUGGESTIONS];
            private static final int MAX_COLUMNS_IN_ROW = 3;
            private int mNumRows;

            public int layout(SuggestedWords suggestions, int fromPos, int maxWidth, int maxHeight,
                    KeyboardView view) {
                clearKeys();
                final Paint paint = new Paint();
                paint.setAntiAlias(true);
                final int padding = (int) view.getContext().getResources()
                        .getDimension(R.dimen.more_suggestions_key_horizontal_padding);

                int row = 0;
                int pos = fromPos, rowStartPos = fromPos;
                final int size = Math.min(suggestions.size(), SuggestionsView.MAX_SUGGESTIONS);
                while (pos < size) {
                    final CharSequence word = suggestions.getWord(pos);
                    // TODO: Should take care of text x-scaling.
                    mWidths[pos] = (int)view.getDefaultLabelWidth(word, paint) + padding;
                    final int numColumn = pos - rowStartPos + 1;
                    if (numColumn > MAX_COLUMNS_IN_ROW
                            || !fitInWidth(rowStartPos, pos + 1, maxWidth / numColumn)) {
                        if ((row + 1) * mDefaultRowHeight > maxHeight) {
                            break;
                        }
                        mNumColumnsInRow[row] = pos - rowStartPos;
                        rowStartPos = pos;
                        row++;
                    }
                    mColumnOrders[pos] = pos - rowStartPos;
                    mRowNumbers[pos] = row;
                    pos++;
                }
                mNumColumnsInRow[row] = pos - rowStartPos;
                mNumRows = row + 1;
                mWidth = mOccupiedWidth = calcurateMaxRowWidth(fromPos, pos);
                mHeight = mOccupiedHeight = mNumRows * mDefaultRowHeight + mVerticalGap;
                return pos - fromPos;
            }

            private boolean fitInWidth(int startPos, int endPos, int width) {
                for (int pos = startPos; pos < endPos; pos++) {
                    if (mWidths[pos] > width)
                        return false;
                }
                return true;
            }

            private int calcurateMaxRowWidth(int startPos, int endPos) {
                int maxRowWidth = 0;
                int pos = startPos;
                for (int row = 0; row < mNumRows; row++) {
                    final int numColumn = mNumColumnsInRow[row];
                    int maxKeyWidth = 0;
                    while (pos < endPos && mRowNumbers[pos] == row) {
                        maxKeyWidth = Math.max(maxKeyWidth, mWidths[pos]);
                        pos++;
                    }
                    maxRowWidth = Math.max(maxRowWidth, maxKeyWidth * numColumn);
                }
                return maxRowWidth;
            }

            private static final int[][] COLUMN_ORDER_TO_NUMBER = {
                { 0, },
                { 1, 0, },
                { 2, 0, 1},
            };

            private int getColumnNumber(int pos) {
                final int columnOrder = mColumnOrders[pos];
                final int numColumn = mNumColumnsInRow[mRowNumbers[pos]];
                return COLUMN_ORDER_TO_NUMBER[numColumn - 1][columnOrder];
            }

            public int getX(int pos) {
                final int columnNumber = getColumnNumber(pos);
                return columnNumber * getWidth(pos);
            }

            public int getY(int pos) {
                final int row = mRowNumbers[pos];
                return (mNumRows -1 - row) * mDefaultRowHeight + mTopPadding;
            }

            public int getWidth(int pos) {
                final int row = mRowNumbers[pos];
                final int numColumn = mNumColumnsInRow[row];
                return mWidth / numColumn;
            }

            public int getFlags(int pos) {
                int rowFlags = 0;

                final int row = mRowNumbers[pos];
                if (row == 0)
                    rowFlags |= Keyboard.EDGE_BOTTOM;
                if (row == mNumRows - 1)
                    rowFlags |= Keyboard.EDGE_TOP;

                final int numColumn = mNumColumnsInRow[row];
                final int column = getColumnNumber(pos);
                if (column == 0)
                    rowFlags |= Keyboard.EDGE_LEFT;
                if (column == numColumn - 1)
                    rowFlags |= Keyboard.EDGE_RIGHT;

                return rowFlags;
            }
        }

        public Builder(MoreSuggestionsView paneView) {
            super(paneView.getContext(), new MoreSuggestionsParam());
            mPaneView = paneView;
        }

        public Builder layout(SuggestedWords suggestions, int fromPos, int maxWidth,
                int maxHeight) {
            final Keyboard keyboard = KeyboardSwitcher.getInstance().getLatinKeyboard();
            final int xmlId = R.xml.kbd_suggestions_pane_template;
            load(keyboard.mId.cloneWithNewXml(mResources.getResourceEntryName(xmlId), xmlId));
            mParams.mVerticalGap = mParams.mTopPadding = keyboard.mVerticalGap / 2;

            final int count = mParams.layout(suggestions, fromPos, maxWidth, maxHeight, mPaneView);
            mFromPos = fromPos;
            mToPos = fromPos + count;
            mSuggestions = suggestions;
            return this;
        }

        private static String getDebugInfo(SuggestedWords suggestions, int pos) {
            if (!DBG) return null;
            final SuggestedWordInfo wordInfo = suggestions.getInfo(pos);
            if (wordInfo == null) return null;
            final String info = wordInfo.getDebugString();
            if (TextUtils.isEmpty(info)) return null;
            return info;
        }

        @Override
        public MoreSuggestions build() {
            final MoreSuggestionsParam params = mParams;
            for (int pos = mFromPos; pos < mToPos; pos++) {
                final String word = mSuggestions.getWord(pos).toString();
                final String info = getDebugInfo(mSuggestions, pos);
                final int index = pos + SUGGESTION_CODE_BASE;
                final Key key = new Key(
                        params, word, info, null, index, null, params.getX(pos), params.getY(pos),
                        params.getWidth(pos), params.mDefaultRowHeight, params.getFlags(pos));
                params.onAddKey(key);
            }
            return new MoreSuggestions(params);
        }
    }
}
