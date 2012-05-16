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
import android.graphics.drawable.Drawable;
import android.view.View;

import com.android.inputmethod.keyboard.internal.KeySpecParser.MoreKeySpec;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StringUtils;

public class MoreKeysKeyboard extends Keyboard {
    private final int mDefaultKeyCoordX;

    MoreKeysKeyboard(Builder.MoreKeysKeyboardParams params) {
        super(params);
        mDefaultKeyCoordX = params.getDefaultKeyCoordX() + params.mDefaultKeyWidth / 2;
    }

    public int getDefaultCoordX() {
        return mDefaultKeyCoordX;
    }

    public static class Builder extends Keyboard.Builder<Builder.MoreKeysKeyboardParams> {
        private final Key mParentKey;
        private final Drawable mDivider;

        private static final float LABEL_PADDING_RATIO = 0.2f;
        private static final float DIVIDER_RATIO = 0.2f;

        public static class MoreKeysKeyboardParams extends Keyboard.Params {
            public boolean mIsFixedOrder;
            /* package */int mTopRowAdjustment;
            public int mNumRows;
            public int mNumColumns;
            public int mTopKeys;
            public int mLeftKeys;
            public int mRightKeys; // includes default key.
            public int mDividerWidth;
            public int mColumnWidth;

            public MoreKeysKeyboardParams() {
                super();
            }

            /**
             * Set keyboard parameters of more keys keyboard.
             *
             * @param numKeys number of keys in this more keys keyboard.
             * @param maxColumns number of maximum columns of this more keys keyboard.
             * @param keyWidth more keys keyboard key width in pixel, including horizontal gap.
             * @param rowHeight more keys keyboard row height in pixel, including vertical gap.
             * @param coordXInParent coordinate x of the key preview in parent keyboard.
             * @param parentKeyboardWidth parent keyboard width in pixel.
             * @param isFixedColumnOrder if true, more keys should be laid out in fixed order.
             * @param dividerWidth width of divider, zero for no dividers.
             */
            public void setParameters(int numKeys, int maxColumns, int keyWidth, int rowHeight,
                    int coordXInParent, int parentKeyboardWidth, boolean isFixedColumnOrder,
                    int dividerWidth) {
                mIsFixedOrder = isFixedColumnOrder;
                if (parentKeyboardWidth / keyWidth < maxColumns) {
                    throw new IllegalArgumentException(
                            "Keyboard is too small to hold more keys keyboard: "
                                    + parentKeyboardWidth + " " + keyWidth + " " + maxColumns);
                }
                mDefaultKeyWidth = keyWidth;
                mDefaultRowHeight = rowHeight;

                final int numRows = (numKeys + maxColumns - 1) / maxColumns;
                mNumRows = numRows;
                final int numColumns = mIsFixedOrder ? Math.min(numKeys, maxColumns)
                        : getOptimizedColumns(numKeys, maxColumns);
                mNumColumns = numColumns;
                final int topKeys = numKeys % numColumns;
                mTopKeys = topKeys == 0 ? numColumns : topKeys;

                final int numLeftKeys = (numColumns - 1) / 2;
                final int numRightKeys = numColumns - numLeftKeys; // including default key.
                // Maximum number of keys we can layout both side of the parent key
                final int maxLeftKeys = coordXInParent / keyWidth;
                final int maxRightKeys = (parentKeyboardWidth - coordXInParent) / keyWidth;
                int leftKeys, rightKeys;
                if (numLeftKeys > maxLeftKeys) {
                    leftKeys = maxLeftKeys;
                    rightKeys = numColumns - leftKeys;
                } else if (numRightKeys > maxRightKeys + 1) {
                    rightKeys = maxRightKeys + 1; // include default key
                    leftKeys = numColumns - rightKeys;
                } else {
                    leftKeys = numLeftKeys;
                    rightKeys = numRightKeys;
                }
                // If the left keys fill the left side of the parent key, entire more keys keyboard
                // should be shifted to the right unless the parent key is on the left edge.
                if (maxLeftKeys == leftKeys && leftKeys > 0) {
                    leftKeys--;
                    rightKeys++;
                }
                // If the right keys fill the right side of the parent key, entire more keys
                // should be shifted to the left unless the parent key is on the right edge.
                if (maxRightKeys == rightKeys - 1 && rightKeys > 1) {
                    leftKeys++;
                    rightKeys--;
                }
                mLeftKeys = leftKeys;
                mRightKeys = rightKeys;

                // Adjustment of the top row.
                mTopRowAdjustment = mIsFixedOrder ? getFixedOrderTopRowAdjustment()
                        : getAutoOrderTopRowAdjustment();
                mDividerWidth = dividerWidth;
                mColumnWidth = mDefaultKeyWidth + mDividerWidth;
                mBaseWidth = mOccupiedWidth = mNumColumns * mColumnWidth - mDividerWidth;
                // Need to subtract the bottom row's gutter only.
                mBaseHeight = mOccupiedHeight = mNumRows * mDefaultRowHeight - mVerticalGap
                        + mTopPadding + mBottomPadding;
            }

            private int getFixedOrderTopRowAdjustment() {
                if (mNumRows == 1 || mTopKeys % 2 == 1 || mTopKeys == mNumColumns
                        || mLeftKeys == 0  || mRightKeys == 1) {
                    return 0;
                }
                return -1;
            }

            private int getAutoOrderTopRowAdjustment() {
                if (mNumRows == 1 || mTopKeys == 1 || mNumColumns % 2 == mTopKeys % 2
                        || mLeftKeys == 0 || mRightKeys == 1) {
                    return 0;
                }
                return -1;
            }

            // Return key position according to column count (0 is default).
            /* package */int getColumnPos(int n) {
                return mIsFixedOrder ? getFixedOrderColumnPos(n) : getAutomaticColumnPos(n);
            }

            private int getFixedOrderColumnPos(int n) {
                final int col = n % mNumColumns;
                final int row = n / mNumColumns;
                if (!isTopRow(row)) {
                    return col - mLeftKeys;
                }
                final int rightSideKeys = mTopKeys / 2;
                final int leftSideKeys = mTopKeys - (rightSideKeys + 1);
                final int pos = col - leftSideKeys;
                final int numLeftKeys = mLeftKeys + mTopRowAdjustment;
                final int numRightKeys = mRightKeys - 1;
                if (numRightKeys >= rightSideKeys && numLeftKeys >= leftSideKeys) {
                    return pos;
                } else if (numRightKeys < rightSideKeys) {
                    return pos - (rightSideKeys - numRightKeys);
                } else { // numLeftKeys < leftSideKeys
                    return pos + (leftSideKeys - numLeftKeys);
                }
            }

            private int getAutomaticColumnPos(int n) {
                final int col = n % mNumColumns;
                final int row = n / mNumColumns;
                int leftKeys = mLeftKeys;
                if (isTopRow(row)) {
                    leftKeys += mTopRowAdjustment;
                }
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
                    if (left < leftKeys) {
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
                final int remainings = numKeys % numColumns;
                return remainings == 0 ? 0 : numColumns - remainings;
            }

            private int getOptimizedColumns(int numKeys, int maxColumns) {
                int numColumns = Math.min(numKeys, maxColumns);
                while (getTopRowEmptySlots(numKeys, numColumns) >= mNumRows) {
                    numColumns--;
                }
                return numColumns;
            }

            public int getDefaultKeyCoordX() {
                return mLeftKeys * mColumnWidth;
            }

            public int getX(int n, int row) {
                final int x = getColumnPos(n) * mColumnWidth + getDefaultKeyCoordX();
                if (isTopRow(row)) {
                    return x + mTopRowAdjustment * (mColumnWidth / 2);
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
                return mNumRows > 1 && rowCount == mNumRows - 1;
            }
        }

        /**
         * The builder of MoreKeysKeyboard.
         * @param containerView the container of {@link MoreKeysKeyboardView}.
         * @param parentKey the {@link Key} that invokes more keys keyboard.
         * @param parentKeyboardView the {@link KeyboardView} that contains the parentKey.
         */
        public Builder(View containerView, Key parentKey, KeyboardView parentKeyboardView) {
            super(containerView.getContext(), new MoreKeysKeyboardParams());
            final Keyboard parentKeyboard = parentKeyboardView.getKeyboard();
            load(parentKeyboard.mMoreKeysTemplate, parentKeyboard.mId);

            // TODO: More keys keyboard's vertical gap is currently calculated heuristically.
            // Should revise the algorithm.
            mParams.mVerticalGap = parentKeyboard.mVerticalGap / 2;
            mParentKey = parentKey;

            final int width, height;
            final boolean singleMoreKeyWithPreview = parentKeyboardView.isKeyPreviewPopupEnabled()
                    && !parentKey.noKeyPreview() && parentKey.mMoreKeys.length == 1;
            if (singleMoreKeyWithPreview) {
                // Use pre-computed width and height if this more keys keyboard has only one key to
                // mitigate visual flicker between key preview and more keys keyboard.
                // Caveats for the visual assets: To achieve this effect, both the key preview
                // backgrounds and the more keys keyboard panel background have the exact same
                // left/right/top paddings. The bottom paddings of both backgrounds don't need to
                // be considered because the vertical positions of both backgrounds were already
                // adjusted with their bottom paddings deducted.
                width = parentKeyboardView.mKeyPreviewDrawParams.mPreviewVisibleWidth;
                height = parentKeyboardView.mKeyPreviewDrawParams.mPreviewVisibleHeight
                        + mParams.mVerticalGap;
            } else {
                width = getMaxKeyWidth(parentKeyboardView, parentKey, mParams.mDefaultKeyWidth);
                height = parentKeyboard.mMostCommonKeyHeight;
            }
            final int dividerWidth;
            if (parentKey.needsDividersInMoreKeys()) {
                mDivider = mResources.getDrawable(R.drawable.more_keys_divider);
                dividerWidth = (int)(width * DIVIDER_RATIO);
            } else {
                mDivider = null;
                dividerWidth = 0;
            }
            mParams.setParameters(parentKey.mMoreKeys.length, parentKey.getMoreKeysColumn(),
                    width, height, parentKey.mX + parentKey.mWidth / 2,
                    parentKeyboardView.getMeasuredWidth(), parentKey.isFixedColumnOrderMoreKeys(),
                    dividerWidth);
        }

        private static int getMaxKeyWidth(KeyboardView view, Key parentKey, int minKeyWidth) {
            final int padding = (int)(view.getResources()
                    .getDimension(R.dimen.more_keys_keyboard_key_horizontal_padding)
                    + (parentKey.hasLabelsInMoreKeys() ? minKeyWidth * LABEL_PADDING_RATIO : 0));
            final Paint paint = view.newDefaultLabelPaint();
            paint.setTextSize(parentKey.hasLabelsInMoreKeys()
                    ? view.mKeyDrawParams.mKeyLabelSize
                    : view.mKeyDrawParams.mKeyLetterSize);
            int maxWidth = minKeyWidth;
            for (final MoreKeySpec spec : parentKey.mMoreKeys) {
                final String label = spec.mLabel;
                // If the label is single letter, minKeyWidth is enough to hold the label.
                if (label != null && StringUtils.codePointCount(label) > 1) {
                    final int width = (int)view.getLabelWidth(label, paint) + padding;
                    if (maxWidth < width) {
                        maxWidth = width;
                    }
                }
            }
            return maxWidth;
        }

        private static class MoreKeyDivider extends Key.Spacer {
            private final Drawable mIcon;

            public MoreKeyDivider(MoreKeysKeyboardParams params, Drawable icon, int x, int y) {
                super(params, x, y, params.mDividerWidth, params.mDefaultRowHeight);
                mIcon = icon;
            }

            @Override
            public Drawable getIcon(KeyboardIconsSet iconSet, int alpha) {
                // KeyboardIconsSet and alpha are unused. Use the icon that has been passed to the
                // constructor.
                // TODO: Drawable itself should have an alpha value.
                mIcon.setAlpha(128);
                return mIcon;
            }
        }

        @Override
        public MoreKeysKeyboard build() {
            final MoreKeysKeyboardParams params = mParams;
            final int moreKeyFlags = mParentKey.getMoreKeyLabelFlags();
            final MoreKeySpec[] moreKeys = mParentKey.mMoreKeys;
            for (int n = 0; n < moreKeys.length; n++) {
                final MoreKeySpec moreKeySpec = moreKeys[n];
                final int row = n / params.mNumColumns;
                final int x = params.getX(n, row);
                final int y = params.getY(row);
                final Key key = new Key(params, moreKeySpec, x, y,
                        params.mDefaultKeyWidth, params.mDefaultRowHeight, moreKeyFlags);
                params.markAsEdgeKey(key, row);
                params.onAddKey(key);

                final int pos = params.getColumnPos(n);
                // The "pos" value represents the offset from the default position. Negative means
                // left of the default position.
                if (params.mDividerWidth > 0 && pos != 0) {
                    final int dividerX = (pos > 0) ? x - params.mDividerWidth
                            : x + params.mDefaultKeyWidth;
                    final Key divider = new MoreKeyDivider(params, mDivider, dividerX, y);
                    params.onAddKey(divider);
                }
            }
            return new MoreKeysKeyboard(params);
        }
    }
}
