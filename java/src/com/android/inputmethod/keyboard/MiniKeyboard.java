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

import android.graphics.Paint;

import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.keyboard.internal.MoreKeySpecParser;
import com.android.inputmethod.latin.R;

public class MiniKeyboard extends Keyboard {
    private final int mDefaultKeyCoordX;

    private MiniKeyboard(Builder.MiniKeyboardParams params) {
        super(params);
        mDefaultKeyCoordX = params.getDefaultKeyCoordX() + params.mDefaultKeyWidth / 2;
    }

    public int getDefaultCoordX() {
        return mDefaultKeyCoordX;
    }

    public static class Builder extends KeyboardBuilder<Builder.MiniKeyboardParams> {
        private final CharSequence[] mMoreKeys;

        public static class MiniKeyboardParams extends KeyboardParams {
            /* package */int mTopRowAdjustment;
            public int mNumRows;
            public int mNumColumns;
            public int mLeftKeys;
            public int mRightKeys; // includes default key.

            public MiniKeyboardParams() {
                super();
            }

            /* package for test */MiniKeyboardParams(int numKeys, int maxColumns, int keyWidth,
                    int rowHeight, int coordXInParent, int parentKeyboardWidth) {
                super();
                setParameters(numKeys, maxColumns, keyWidth, rowHeight, coordXInParent,
                        parentKeyboardWidth);
            }

            /**
             * Set keyboard parameters of mini keyboard.
             *
             * @param numKeys number of keys in this mini keyboard.
             * @param maxColumns number of maximum columns of this mini keyboard.
             * @param keyWidth mini keyboard key width in pixel, including horizontal gap.
             * @param rowHeight mini keyboard row height in pixel, including vertical gap.
             * @param coordXInParent coordinate x of the popup key in parent keyboard.
             * @param parentKeyboardWidth parent keyboard width in pixel.
             */
            public void setParameters(int numKeys, int maxColumns, int keyWidth, int rowHeight,
                    int coordXInParent, int parentKeyboardWidth) {
                if (parentKeyboardWidth / keyWidth < maxColumns) {
                    throw new IllegalArgumentException(
                            "Keyboard is too small to hold mini keyboard: " + parentKeyboardWidth
                                    + " " + keyWidth + " " + maxColumns);
                }
                mDefaultKeyWidth = keyWidth;
                mDefaultRowHeight = rowHeight;

                final int numRows = (numKeys + maxColumns - 1) / maxColumns;
                mNumRows = numRows;
                final int numColumns = getOptimizedColumns(numKeys, maxColumns);
                mNumColumns = numColumns;

                final int numLeftKeys = (numColumns - 1) / 2;
                final int numRightKeys = numColumns - numLeftKeys; // including default key.
                final int maxLeftKeys = coordXInParent / keyWidth;
                final int maxRightKeys = Math.max(1, (parentKeyboardWidth - coordXInParent)
                        / keyWidth);
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

                mBaseWidth = mOccupiedWidth = mNumColumns * mDefaultKeyWidth;
                // Need to subtract the bottom row's gutter only.
                mBaseHeight = mOccupiedHeight = mNumRows * mDefaultRowHeight - mVerticalGap
                        + mTopPadding + mBottomPadding;
            }

            // Return key position according to column count (0 is default).
            /* package */int getColumnPos(int n) {
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
                return mLeftKeys * mDefaultKeyWidth;
            }

            public int getX(int n, int row) {
                final int x = getColumnPos(n) * mDefaultKeyWidth + getDefaultKeyCoordX();
                if (isTopRow(row)) {
                    return x + mTopRowAdjustment * (mDefaultKeyWidth / 2);
                }
                return x;
            }

            public int getY(int row) {
                return (mNumRows - 1 - row) * mDefaultRowHeight + mTopPadding;
            }

            public void markAsEdgeKey(Key key, int row) {
                if (row == 0)
                    key.markAsTopEdge(this);
                if (isTopRow(row))
                    key.markAsBottomEdge(this);
            }

            private boolean isTopRow(int rowCount) {
                return rowCount == mNumRows - 1;
            }
        }

        public Builder(KeyboardView view, int xmlId, Key parentKey, Keyboard parentKeyboard) {
            super(view.getContext(), new MiniKeyboardParams());
            load(parentKeyboard.mId.cloneWithNewXml(mResources.getResourceEntryName(xmlId), xmlId));

            // TODO: Mini keyboard's vertical gap is currently calculated heuristically.
            // Should revise the algorithm.
            mParams.mVerticalGap = parentKeyboard.mVerticalGap / 2;
            mParams.mIsRtlKeyboard = parentKeyboard.mIsRtlKeyboard;
            mMoreKeys = parentKey.mMoreKeys;

            final int previewWidth = view.mKeyPreviewDrawParams.mPreviewBackgroundWidth;
            final int previewHeight = view.mKeyPreviewDrawParams.mPreviewBackgroundHeight;
            final int width, height;
            // Use pre-computed width and height if these values are available and mini keyboard
            // has only one key to mitigate visual flicker between key preview and mini keyboard.
            if (view.isKeyPreviewPopupEnabled() && mMoreKeys.length == 1 && previewWidth > 0
                    && previewHeight > 0) {
                width = previewWidth;
                height = previewHeight + mParams.mVerticalGap;
            } else {
                width = getMaxKeyWidth(view, parentKey.mMoreKeys, mParams.mDefaultKeyWidth);
                height = parentKeyboard.mMostCommonKeyHeight;
            }
            mParams.setParameters(mMoreKeys.length, parentKey.mMaxMoreKeysColumn, width, height,
                    parentKey.mX + (mParams.mDefaultKeyWidth - width) / 2, view.getMeasuredWidth());
        }

        private static int getMaxKeyWidth(KeyboardView view, CharSequence[] moreKeys,
                int minKeyWidth) {
            final int padding = (int) view.getContext().getResources()
                    .getDimension(R.dimen.mini_keyboard_key_horizontal_padding);
            Paint paint = null;
            int maxWidth = minKeyWidth;
            for (CharSequence moreKeySpec : moreKeys) {
                final CharSequence label = MoreKeySpecParser.getLabel(moreKeySpec.toString());
                // If the label is single letter, minKeyWidth is enough to hold
                // the label.
                if (label != null && label.length() > 1) {
                    if (paint == null) {
                        paint = new Paint();
                        paint.setAntiAlias(true);
                    }
                    final int width = (int)view.getDefaultLabelWidth(label, paint) + padding;
                    if (maxWidth < width) {
                        maxWidth = width;
                    }
                }
            }
            return maxWidth;
        }

        @Override
        public MiniKeyboard build() {
            final MiniKeyboardParams params = mParams;
            for (int n = 0; n < mMoreKeys.length; n++) {
                final String moreKeySpec = mMoreKeys[n].toString();
                final int row = n / params.mNumColumns;
                final Key key = new Key(mResources, params, moreKeySpec, params.getX(n, row),
                        params.getY(row), params.mDefaultKeyWidth, params.mDefaultRowHeight);
                params.markAsEdgeKey(key, row);
                params.onAddKey(key);
            }
            return new MiniKeyboard(params);
        }
    }
}
