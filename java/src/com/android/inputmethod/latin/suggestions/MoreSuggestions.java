/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.TypefaceUtils;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.Utils;

public final class MoreSuggestions extends Keyboard {
    public static final int SUGGESTION_CODE_BASE = 1024;

    public final SuggestedWords mSuggestedWords;

    public static abstract class MoreSuggestionsListener extends KeyboardActionListener.Adapter {
        public abstract void onSuggestionSelected(final int index, final SuggestedWordInfo info);
    }

    MoreSuggestions(final MoreSuggestionsParam params, final SuggestedWords suggestedWords) {
        super(params);
        mSuggestedWords = suggestedWords;
    }

    private static final class MoreSuggestionsParam extends KeyboardParams {
        private final int[] mWidths = new int[SuggestionStripView.MAX_SUGGESTIONS];
        private final int[] mRowNumbers = new int[SuggestionStripView.MAX_SUGGESTIONS];
        private final int[] mColumnOrders = new int[SuggestionStripView.MAX_SUGGESTIONS];
        private final int[] mNumColumnsInRow = new int[SuggestionStripView.MAX_SUGGESTIONS];
        private static final int MAX_COLUMNS_IN_ROW = 3;
        private int mNumRows;
        public Drawable mDivider;
        public int mDividerWidth;

        public MoreSuggestionsParam() {
            super();
        }

        public int layout(final SuggestedWords suggestedWords, final int fromPos,
                final int maxWidth, final int minWidth, final int maxRow, final Paint paint,
                final Resources res) {
            clearKeys();
            mDivider = res.getDrawable(R.drawable.more_suggestions_divider);
            mDividerWidth = mDivider.getIntrinsicWidth();
            final float padding = res.getDimension(R.dimen.more_suggestions_key_horizontal_padding);

            int row = 0;
            int pos = fromPos, rowStartPos = fromPos;
            final int size = Math.min(suggestedWords.size(), SuggestionStripView.MAX_SUGGESTIONS);
            while (pos < size) {
                final String word = suggestedWords.getWord(pos);
                // TODO: Should take care of text x-scaling.
                mWidths[pos] = (int)(TypefaceUtils.getLabelWidth(word, paint) + padding);
                final int numColumn = pos - rowStartPos + 1;
                final int columnWidth =
                        (maxWidth - mDividerWidth * (numColumn - 1)) / numColumn;
                if (numColumn > MAX_COLUMNS_IN_ROW
                        || !fitInWidth(rowStartPos, pos + 1, columnWidth)) {
                    if ((row + 1) >= maxRow) {
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
            mBaseWidth = mOccupiedWidth = Math.max(
                    minWidth, calcurateMaxRowWidth(fromPos, pos));
            mBaseHeight = mOccupiedHeight = mNumRows * mDefaultRowHeight + mVerticalGap;
            return pos - fromPos;
        }

        private boolean fitInWidth(final int startPos, final int endPos, final int width) {
            for (int pos = startPos; pos < endPos; pos++) {
                if (mWidths[pos] > width)
                    return false;
            }
            return true;
        }

        private int calcurateMaxRowWidth(final int startPos, final int endPos) {
            int maxRowWidth = 0;
            int pos = startPos;
            for (int row = 0; row < mNumRows; row++) {
                final int numColumnInRow = mNumColumnsInRow[row];
                int maxKeyWidth = 0;
                while (pos < endPos && mRowNumbers[pos] == row) {
                    maxKeyWidth = Math.max(maxKeyWidth, mWidths[pos]);
                    pos++;
                }
                maxRowWidth = Math.max(maxRowWidth,
                        maxKeyWidth * numColumnInRow + mDividerWidth * (numColumnInRow - 1));
            }
            return maxRowWidth;
        }

        private static final int[][] COLUMN_ORDER_TO_NUMBER = {
            { 0, },
            { 1, 0, },
            { 2, 0, 1},
        };

        public int getNumColumnInRow(final int pos) {
            return mNumColumnsInRow[mRowNumbers[pos]];
        }

        public int getColumnNumber(final int pos) {
            final int columnOrder = mColumnOrders[pos];
            final int numColumn = getNumColumnInRow(pos);
            return COLUMN_ORDER_TO_NUMBER[numColumn - 1][columnOrder];
        }

        public int getX(final int pos) {
            final int columnNumber = getColumnNumber(pos);
            return columnNumber * (getWidth(pos) + mDividerWidth);
        }

        public int getY(final int pos) {
            final int row = mRowNumbers[pos];
            return (mNumRows -1 - row) * mDefaultRowHeight + mTopPadding;
        }

        public int getWidth(final int pos) {
            final int numColumnInRow = getNumColumnInRow(pos);
            return (mOccupiedWidth - mDividerWidth * (numColumnInRow - 1)) / numColumnInRow;
        }

        public void markAsEdgeKey(final Key key, final int pos) {
            final int row = mRowNumbers[pos];
            if (row == 0)
                key.markAsBottomEdge(this);
            if (row == mNumRows - 1)
                key.markAsTopEdge(this);

            final int numColumnInRow = mNumColumnsInRow[row];
            final int column = getColumnNumber(pos);
            if (column == 0)
                key.markAsLeftEdge(this);
            if (column == numColumnInRow - 1)
                key.markAsRightEdge(this);
        }
    }

    public static final class Builder extends KeyboardBuilder<MoreSuggestionsParam> {
        private final MoreSuggestionsView mPaneView;
        private SuggestedWords mSuggestedWords;
        private int mFromPos;
        private int mToPos;

        public Builder(final Context context, final MoreSuggestionsView paneView) {
            super(context, new MoreSuggestionsParam());
            mPaneView = paneView;
        }

        public Builder layout(final SuggestedWords suggestedWords, final int fromPos,
                final int maxWidth, final int minWidth, final int maxRow,
                final Keyboard parentKeyboard) {
            final int xmlId = R.xml.kbd_suggestions_pane_template;
            load(xmlId, parentKeyboard.mId);
            mParams.mVerticalGap = mParams.mTopPadding = parentKeyboard.mVerticalGap / 2;

            mPaneView.updateKeyboardGeometry(mParams.mDefaultRowHeight);
            final int count = mParams.layout(suggestedWords, fromPos, maxWidth, minWidth, maxRow,
                    mPaneView.newLabelPaint(null /* key */), mResources);
            mFromPos = fromPos;
            mToPos = fromPos + count;
            mSuggestedWords = suggestedWords;
            return this;
        }

        @Override
        public MoreSuggestions build() {
            final MoreSuggestionsParam params = mParams;
            for (int pos = mFromPos; pos < mToPos; pos++) {
                final int x = params.getX(pos);
                final int y = params.getY(pos);
                final int width = params.getWidth(pos);
                final String word = mSuggestedWords.getWord(pos);
                final String info = Utils.getDebugInfo(mSuggestedWords, pos);
                final int index = pos + SUGGESTION_CODE_BASE;
                final Key key = new Key(
                        params, word, info, KeyboardIconsSet.ICON_UNDEFINED, index, null, x, y,
                        width, params.mDefaultRowHeight, 0);
                params.markAsEdgeKey(key, pos);
                params.onAddKey(key);
                final int columnNumber = params.getColumnNumber(pos);
                final int numColumnInRow = params.getNumColumnInRow(pos);
                if (columnNumber < numColumnInRow - 1) {
                    final Divider divider = new Divider(params, params.mDivider, x + width, y,
                            params.mDividerWidth, params.mDefaultRowHeight);
                    params.onAddKey(divider);
                }
            }
            return new MoreSuggestions(params, mSuggestedWords);
        }
    }

    private static final class Divider extends Key.Spacer {
        private final Drawable mIcon;

        public Divider(final KeyboardParams params, final Drawable icon, final int x,
                final int y, final int width, final int height) {
            super(params, x, y, width, height);
            mIcon = icon;
        }

        @Override
        public Drawable getIcon(final KeyboardIconsSet iconSet, final int alpha) {
            // KeyboardIconsSet and alpha are unused. Use the icon that has been passed to the
            // constructor.
            // TODO: Drawable itself should have an alpha value.
            mIcon.setAlpha(128);
            return mIcon;
        }
    }
}
