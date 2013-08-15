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

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.internal.KeyPreviewDrawParams;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.keyboard.internal.MoreKeySpec;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.StringUtils;
import com.android.inputmethod.latin.utils.TypefaceUtils;

public final class MoreKeysKeyboard extends Keyboard {
    private final int mDefaultKeyCoordX;

    MoreKeysKeyboard(final MoreKeysKeyboardParams params) {
        super(params);
        mDefaultKeyCoordX = params.getDefaultKeyCoordX() + params.mDefaultKeyWidth / 2;
    }

    public int getDefaultCoordX() {
        return mDefaultKeyCoordX;
    }

    @UsedForTesting
    static class MoreKeysKeyboardParams extends KeyboardParams {
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
        public void setParameters(final int numKeys, final int maxColumns, final int keyWidth,
                final int rowHeight, final int coordXInParent, final int parentKeyboardWidth,
                final boolean isFixedColumnOrder, final int dividerWidth) {
            mIsFixedOrder = isFixedColumnOrder;
            if (parentKeyboardWidth / keyWidth < Math.min(numKeys, maxColumns)) {
                throw new IllegalArgumentException("Keyboard is too small to hold more keys: "
                        + parentKeyboardWidth + " " + keyWidth + " " + numKeys + " " + maxColumns);
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
        /* package */int getColumnPos(final int n) {
            return mIsFixedOrder ? getFixedOrderColumnPos(n) : getAutomaticColumnPos(n);
        }

        private int getFixedOrderColumnPos(final int n) {
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

        private int getAutomaticColumnPos(final int n) {
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

        private static int getTopRowEmptySlots(final int numKeys, final int numColumns) {
            final int remainings = numKeys % numColumns;
            return remainings == 0 ? 0 : numColumns - remainings;
        }

        private int getOptimizedColumns(final int numKeys, final int maxColumns) {
            int numColumns = Math.min(numKeys, maxColumns);
            while (getTopRowEmptySlots(numKeys, numColumns) >= mNumRows) {
                numColumns--;
            }
            return numColumns;
        }

        public int getDefaultKeyCoordX() {
            return mLeftKeys * mColumnWidth;
        }

        public int getX(final int n, final int row) {
            final int x = getColumnPos(n) * mColumnWidth + getDefaultKeyCoordX();
            if (isTopRow(row)) {
                return x + mTopRowAdjustment * (mColumnWidth / 2);
            }
            return x;
        }

        public int getY(final int row) {
            return (mNumRows - 1 - row) * mDefaultRowHeight + mTopPadding;
        }

        public void markAsEdgeKey(final Key key, final int row) {
            if (row == 0)
                key.markAsTopEdge(this);
            if (isTopRow(row))
                key.markAsBottomEdge(this);
        }

        private boolean isTopRow(final int rowCount) {
            return mNumRows > 1 && rowCount == mNumRows - 1;
        }
    }

    public static class Builder extends KeyboardBuilder<MoreKeysKeyboardParams> {
        private final Key mParentKey;
        private final Drawable mDivider;

        private static final float LABEL_PADDING_RATIO = 0.2f;
        private static final float DIVIDER_RATIO = 0.2f;

        /**
         * The builder of MoreKeysKeyboard.
         * @param context the context of {@link MoreKeysKeyboardView}.
         * @param parentKey the {@link Key} that invokes more keys keyboard.
         * @param parentKeyboardView the {@link KeyboardView} that contains the parentKey.
         * @param keyPreviewDrawParams the parameter to place key preview.
         */
        public Builder(final Context context, final Key parentKey,
                final MainKeyboardView parentKeyboardView,
                final KeyPreviewDrawParams keyPreviewDrawParams) {
            super(context, new MoreKeysKeyboardParams());
            final Keyboard parentKeyboard = parentKeyboardView.getKeyboard();
            load(parentKeyboard.mMoreKeysTemplate, parentKeyboard.mId);

            // TODO: More keys keyboard's vertical gap is currently calculated heuristically.
            // Should revise the algorithm.
            mParams.mVerticalGap = parentKeyboard.mVerticalGap / 2;
            mParentKey = parentKey;

            final MoreKeySpec[] moreKeys = parentKey.getMoreKeys();
            final int width, height;
            // {@link KeyPreviewDrawParams#mPreviewVisibleWidth} should have been set at
            // {@link MainKeyboardView#showKeyPreview(PointerTracker}, though there may be
            // some chances that the value is zero. <code>width == 0</code> will cause
            // zero-division error at
            // {@link MoreKeysKeyboardParams#setParameters(int,int,int,int,int,int,boolean,int)}.
            final boolean singleMoreKeyWithPreview = parentKeyboardView.isKeyPreviewPopupEnabled()
                    && !parentKey.noKeyPreview() && moreKeys.length == 1
                    && keyPreviewDrawParams.mPreviewVisibleWidth > 0;
            if (singleMoreKeyWithPreview) {
                // Use pre-computed width and height if this more keys keyboard has only one key to
                // mitigate visual flicker between key preview and more keys keyboard.
                // Caveats for the visual assets: To achieve this effect, both the key preview
                // backgrounds and the more keys keyboard panel background have the exact same
                // left/right/top paddings. The bottom paddings of both backgrounds don't need to
                // be considered because the vertical positions of both backgrounds were already
                // adjusted with their bottom paddings deducted.
                width = keyPreviewDrawParams.mPreviewVisibleWidth;
                height = keyPreviewDrawParams.mPreviewVisibleHeight + mParams.mVerticalGap;
            } else {
                final float padding = context.getResources().getDimension(
                        R.dimen.more_keys_keyboard_key_horizontal_padding)
                        + (parentKey.hasLabelsInMoreKeys()
                                ? mParams.mDefaultKeyWidth * LABEL_PADDING_RATIO : 0.0f);
                width = getMaxKeyWidth(parentKey, mParams.mDefaultKeyWidth, padding,
                        parentKeyboardView.newLabelPaint(parentKey));
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
            mParams.setParameters(moreKeys.length, parentKey.getMoreKeysColumn(),
                    width, height, parentKey.getX() + parentKey.getWidth() / 2,
                    parentKeyboard.mId.mWidth, parentKey.isFixedColumnOrderMoreKeys(),
                    dividerWidth);
        }

        private static int getMaxKeyWidth(final Key parentKey, final int minKeyWidth,
                final float padding, final Paint paint) {
            int maxWidth = minKeyWidth;
            for (final MoreKeySpec spec : parentKey.getMoreKeys()) {
                final String label = spec.mLabel;
                // If the label is single letter, minKeyWidth is enough to hold the label.
                if (label != null && StringUtils.codePointCount(label) > 1) {
                    maxWidth = Math.max(maxWidth,
                            (int)(TypefaceUtils.getLabelWidth(label, paint) + padding));
                }
            }
            return maxWidth;
        }

        @Override
        public MoreKeysKeyboard build() {
            final MoreKeysKeyboardParams params = mParams;
            final int moreKeyFlags = mParentKey.getMoreKeyLabelFlags();
            final MoreKeySpec[] moreKeys = mParentKey.getMoreKeys();
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

    private static class MoreKeyDivider extends Key.Spacer {
        private final Drawable mIcon;

        public MoreKeyDivider(final MoreKeysKeyboardParams params, final Drawable icon,
                final int x, final int y) {
            super(params, x, y, params.mDividerWidth, params.mDefaultRowHeight);
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
