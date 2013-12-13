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
import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.utils.TypefaceUtils;

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

        public int layout(final SuggestedWords suggestedWords, final int fromIndex,
                final int maxWidth, final int minWidth, final int maxRow, final Paint paint,
                final Resources res) {
            clearKeys();
            mDivider = res.getDrawable(R.drawable.more_suggestions_divider);
            mDividerWidth = mDivider.getIntrinsicWidth();
            final float padding = res.getDimension(R.dimen.more_suggestions_key_horizontal_padding);

            int row = 0;
            int index = fromIndex;
            int rowStartIndex = fromIndex;
            final int size = Math.min(suggestedWords.size(), SuggestionStripView.MAX_SUGGESTIONS);
            while (index < size) {
                final String word = suggestedWords.getWord(index);
                // TODO: Should take care of text x-scaling.
                mWidths[index] = (int)(TypefaceUtils.getLabelWidth(word, paint) + padding);
                final int numColumn = index - rowStartIndex + 1;
                final int columnWidth =
                        (maxWidth - mDividerWidth * (numColumn - 1)) / numColumn;
                if (numColumn > MAX_COLUMNS_IN_ROW
                        || !fitInWidth(rowStartIndex, index + 1, columnWidth)) {
                    if ((row + 1) >= maxRow) {
                        break;
                    }
                    mNumColumnsInRow[row] = index - rowStartIndex;
                    rowStartIndex = index;
                    row++;
                }
                mColumnOrders[index] = index - rowStartIndex;
                mRowNumbers[index] = row;
                index++;
            }
            mNumColumnsInRow[row] = index - rowStartIndex;
            mNumRows = row + 1;
            mBaseWidth = mOccupiedWidth = Math.max(
                    minWidth, calcurateMaxRowWidth(fromIndex, index));
            mBaseHeight = mOccupiedHeight = mNumRows * mDefaultRowHeight + mVerticalGap;
            return index - fromIndex;
        }

        private boolean fitInWidth(final int startIndex, final int endIndex, final int width) {
            for (int index = startIndex; index < endIndex; index++) {
                if (mWidths[index] > width)
                    return false;
            }
            return true;
        }

        private int calcurateMaxRowWidth(final int startIndex, final int endIndex) {
            int maxRowWidth = 0;
            int index = startIndex;
            for (int row = 0; row < mNumRows; row++) {
                final int numColumnInRow = mNumColumnsInRow[row];
                int maxKeyWidth = 0;
                while (index < endIndex && mRowNumbers[index] == row) {
                    maxKeyWidth = Math.max(maxKeyWidth, mWidths[index]);
                    index++;
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

        public int getNumColumnInRow(final int index) {
            return mNumColumnsInRow[mRowNumbers[index]];
        }

        public int getColumnNumber(final int index) {
            final int columnOrder = mColumnOrders[index];
            final int numColumn = getNumColumnInRow(index);
            return COLUMN_ORDER_TO_NUMBER[numColumn - 1][columnOrder];
        }

        public int getX(final int index) {
            final int columnNumber = getColumnNumber(index);
            return columnNumber * (getWidth(index) + mDividerWidth);
        }

        public int getY(final int index) {
            final int row = mRowNumbers[index];
            return (mNumRows -1 - row) * mDefaultRowHeight + mTopPadding;
        }

        public int getWidth(final int index) {
            final int numColumnInRow = getNumColumnInRow(index);
            return (mOccupiedWidth - mDividerWidth * (numColumnInRow - 1)) / numColumnInRow;
        }

        public void markAsEdgeKey(final Key key, final int index) {
            final int row = mRowNumbers[index];
            if (row == 0)
                key.markAsBottomEdge(this);
            if (row == mNumRows - 1)
                key.markAsTopEdge(this);

            final int numColumnInRow = mNumColumnsInRow[row];
            final int column = getColumnNumber(index);
            if (column == 0)
                key.markAsLeftEdge(this);
            if (column == numColumnInRow - 1)
                key.markAsRightEdge(this);
        }
    }

    public static final class Builder extends KeyboardBuilder<MoreSuggestionsParam> {
        private final MoreSuggestionsView mPaneView;
        private SuggestedWords mSuggestedWords;
        private int mFromIndex;
        private int mToIndex;

        public Builder(final Context context, final MoreSuggestionsView paneView) {
            super(context, new MoreSuggestionsParam());
            mPaneView = paneView;
        }

        public Builder layout(final SuggestedWords suggestedWords, final int fromIndex,
                final int maxWidth, final int minWidth, final int maxRow,
                final Keyboard parentKeyboard) {
            final int xmlId = R.xml.kbd_suggestions_pane_template;
            load(xmlId, parentKeyboard.mId);
            mParams.mVerticalGap = mParams.mTopPadding = parentKeyboard.mVerticalGap / 2;

            mPaneView.updateKeyboardGeometry(mParams.mDefaultRowHeight);
            final int count = mParams.layout(suggestedWords, fromIndex, maxWidth, minWidth, maxRow,
                    mPaneView.newLabelPaint(null /* key */), mResources);
            mFromIndex = fromIndex;
            mToIndex = fromIndex + count;
            mSuggestedWords = suggestedWords;
            return this;
        }

        @Override
        public MoreSuggestions build() {
            final MoreSuggestionsParam params = mParams;
            for (int index = mFromIndex; index < mToIndex; index++) {
                final int x = params.getX(index);
                final int y = params.getY(index);
                final int width = params.getWidth(index);
                final String word = mSuggestedWords.getWord(index);
                final String info = mSuggestedWords.getDebugString(index);
                final int indexInMoreSuggestions = index + SUGGESTION_CODE_BASE;
                final Key key = new Key(
                        params, word, info, KeyboardIconsSet.ICON_UNDEFINED, indexInMoreSuggestions,
                        null /* outputText */, x, y, width, params.mDefaultRowHeight,
                        0 /* labelFlags */, Key.BACKGROUND_TYPE_NORMAL);
                params.markAsEdgeKey(key, index);
                params.onAddKey(key);
                final int columnNumber = params.getColumnNumber(index);
                final int numColumnInRow = params.getNumColumnInRow(index);
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
