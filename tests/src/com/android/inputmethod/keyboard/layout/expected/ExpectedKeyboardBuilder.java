/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.keyboard.layout.expected;

import java.util.Arrays;
import java.util.Locale;

/**
 * This class builds an expected keyboard for unit test.
 */
public final class ExpectedKeyboardBuilder extends AbstractKeyboardBuilder<ExpectedKey> {
    public ExpectedKeyboardBuilder(final int ... dimensions) {
        super(dimensions);
    }

    public ExpectedKeyboardBuilder(final ExpectedKey[][] rows) {
        super(rows);
    }

    @Override
    protected ExpectedKey defaultElement() {
        return ExpectedKey.EMPTY_KEY;
    }

    @Override
    ExpectedKey[] newArray(final int size) {
        return new ExpectedKey[size];
    }

    @Override
    ExpectedKey[][] newArrayOfArray(final int size) {
        return new ExpectedKey[size][];
    }

    @Override
    public ExpectedKey[][] build() {
        return super.build();
    }

    // A replacement job to be performed.
    private interface ReplaceJob {
        // Returns a {@link ExpectedKey} objects to replace.
        ExpectedKey[] replacingKeys(final ExpectedKey oldKey);
        // Return true if replacing should be stopped at first occurrence.
        boolean stopAtFirstOccurrence();
    }

    private static ExpectedKey[] replaceKeyAt(final ExpectedKey[] keys, final int columnIndex,
            final ExpectedKey[] replacingKeys) {
        // Optimization for replacing a key with another key.
        if (replacingKeys.length == 1) {
            keys[columnIndex] = replacingKeys[0];
            return keys;
        }
        final int newLength = keys.length - 1 + replacingKeys.length;
        // Remove the key at columnIndex.
        final ExpectedKey[] newKeys = Arrays.copyOf(keys, newLength);
        System.arraycopy(keys, columnIndex + 1, newKeys, columnIndex + replacingKeys.length,
                keys.length - 1 - columnIndex);
        // Insert replacing keys at columnIndex.
        System.arraycopy(replacingKeys, 0, newKeys, columnIndex, replacingKeys.length);
        return newKeys;

    }

    // Replace key(s) that has the specified visual.
    private void replaceKeyOf(final ExpectedKeyVisual visual, final ReplaceJob job) {
        int replacedCount = 0;
        final int rowCount = getRowCount();
        for (int row = 1; row <= rowCount; row++) {
            ExpectedKey[] keys = getRowAt(row);
            for (int columnIndex = 0; columnIndex < keys.length; /* nothing */) {
                final ExpectedKey currentKey = keys[columnIndex];
                if (!currentKey.getVisual().equalsTo(visual)) {
                    columnIndex++;
                    continue;
                }
                final ExpectedKey[] replacingKeys = job.replacingKeys(currentKey);
                keys = replaceKeyAt(keys, columnIndex, replacingKeys);
                columnIndex += replacingKeys.length;
                setRowAt(row, keys);
                replacedCount++;
                if (job.stopAtFirstOccurrence()) {
                    return;
                }
            }
        }
        if (replacedCount == 0) {
            throw new RuntimeException(
                    "Can't find key that has visual: " + visual + " in\n" + this);
        }
    }

    /**
     * Set the row with specified keys that have specified labels.
     * @param row the row number to set keys.
     * @param labels the label texts of the keys.
     * @return this builder.
     */
    public ExpectedKeyboardBuilder setLabelsOfRow(final int row, final String ... labels) {
        final ExpectedKey[] keys = new ExpectedKey[labels.length];
        for (int columnIndex = 0; columnIndex < labels.length; columnIndex++) {
            keys[columnIndex] = ExpectedKey.newInstance(labels[columnIndex]);
        }
        setRowAt(row, keys);
        return this;
    }

    /**
     * Set the "more keys" of the key that has the specified label.
     * @param label the label of the key to set the "more keys".
     * @param moreKeys the array of labels of the "more keys" to be set.
     * @return this builder.
     */
    public ExpectedKeyboardBuilder setMoreKeysOf(final String label, final String ... moreKeys) {
        final ExpectedKey[] expectedMoreKeys = new ExpectedKey[moreKeys.length];
        for (int index = 0; index < moreKeys.length; index++) {
            expectedMoreKeys[index] = ExpectedKey.newInstance(moreKeys[index]);
        }
        setMoreKeysOf(label, expectedMoreKeys);
        return this;
    }

    /**
     * Set the "more keys" of the key that has the specified label.
     * @param label the label of the key to set the "more keys".
     * @param moreKeys the array of "more key" to be set.
     * @return this builder.
     */
    public ExpectedKeyboardBuilder setMoreKeysOf(final String label,
            final ExpectedKey ... moreKeys) {
        setMoreKeysOf(ExpectedKeyVisual.newInstance(label), moreKeys);
        return this;
    }

    /**
     * Set the "more keys" of the key that has the specified icon.
     * @param iconId the icon id of the key to set the "more keys".
     * @param moreKeys the array of "more key" to be set.
     * @return this builder.
     */
    public ExpectedKeyboardBuilder setMoreKeysOf(final int iconId, final ExpectedKey ... moreKeys) {
        setMoreKeysOf(ExpectedKeyVisual.newInstance(iconId), moreKeys);
        return this;
    }

    private void setMoreKeysOf(final ExpectedKeyVisual visual, final ExpectedKey[] moreKeys) {
        replaceKeyOf(visual, new ReplaceJob() {
            @Override
            public ExpectedKey[] replacingKeys(final ExpectedKey oldKey) {
                return new ExpectedKey[] {
                    ExpectedKey.newInstance(oldKey.getVisual(), oldKey.getOutput(), moreKeys)
                };
            }
            @Override
            public boolean stopAtFirstOccurrence() {
                return true;
            }
        });
    }

    /**
     * Insert the keys at specified position.
     * @param row the row number to insert the <code>keys</code>.
     * @param column the column number to insert the <code>keys</code>.
     * @param keys the array of keys to insert at <code>row,column</code>.
     * @return this builder.
     * @throws {@link RuntimeException} if <code>row</code> or <code>column</code> is illegal.
     */
    public ExpectedKeyboardBuilder insertKeysAtRow(final int row, final int column,
            final ExpectedKey ... keys) {
        for (int index = 0; index < keys.length; index++) {
            setElementAt(row, column + index, keys[index], true /* insert */);
        }
        return this;
    }

    /**
     * Add the keys on the left most of the row.
     * @param row the row number to add the <code>keys</code>.
     * @param keys the array of keys to add on the left most of the row.
     * @return this builder.
     * @throws {@link RuntimeException} if <code>row</code> is illegal.
     */
    public ExpectedKeyboardBuilder addKeysOnTheLeftOfRow(final int row,
            final ExpectedKey ... keys) {
        // Keys should be inserted from the last to preserve the order.
        for (int index = keys.length - 1; index >= 0; index--) {
            setElementAt(row, 1, keys[index], true /* insert */);
        }
        return this;
    }

    /**
     * Add the keys on the right most of the row.
     * @param row the row number to add the <code>keys</code>.
     * @param keys the array of keys to add on the right most of the row.
     * @return this builder.
     * @throws {@link RuntimeException} if <code>row</code> is illegal.
     */
    public ExpectedKeyboardBuilder addKeysOnTheRightOfRow(final int row,
            final ExpectedKey ... keys) {
        final int rightEnd = getRowAt(row).length + 1;
        insertKeysAtRow(row, rightEnd, keys);
        return this;
    }

    /**
     * Replace the most top-left key that has the specified label with the new keys.
     * @param label the label of the key to set <code>newKeys</code>.
     * @param newKeys the keys to be set.
     * @return this builder.
     */
    public ExpectedKeyboardBuilder replaceKeyOfLabel(final String label,
            final ExpectedKey ... newKeys) {
        final ExpectedKeyVisual visual = ExpectedKeyVisual.newInstance(label);
        replaceKeyOf(visual, new ReplaceJob() {
            @Override
            public ExpectedKey[] replacingKeys(final ExpectedKey oldKey) {
                return newKeys;
            }
            @Override
            public boolean stopAtFirstOccurrence() {
                return true;
            }
        });
        return this;
    }

    /**
     * Replace the all specified keys with the new keys.
     * @param key the key to be replaced by <code>newKeys</code>.
     * @param newKeys the keys to be set.
     * @return this builder.
     */
    public ExpectedKeyboardBuilder replaceKeysOfAll(final ExpectedKey key,
            final ExpectedKey ... newKeys) {
        replaceKeyOf(key.getVisual(), new ReplaceJob() {
            @Override
            public ExpectedKey[] replacingKeys(final ExpectedKey oldKey) {
                return newKeys;
            }
            @Override
            public boolean stopAtFirstOccurrence() {
                return false;
            }
        });
        return this;
    }

    /**
     * Convert all keys of this keyboard builder to upper case keys.
     * @param locale the locale used to convert cases.
     * @return this builder
     */
    public ExpectedKeyboardBuilder toUpperCase(final Locale locale) {
        final int rowCount = getRowCount();
        for (int row = 1; row <= rowCount; row++) {
            final ExpectedKey[] lowerCaseKeys = getRowAt(row);
            final ExpectedKey[] upperCaseKeys = new ExpectedKey[lowerCaseKeys.length];
            for (int columnIndex = 0; columnIndex < lowerCaseKeys.length; columnIndex++) {
                upperCaseKeys[columnIndex] = lowerCaseKeys[columnIndex].toUpperCase(locale);
            }
            setRowAt(row, upperCaseKeys);
        }
        return this;
    }

    @Override
    public String toString() {
        return toString(build());
    }

    /**
     * Convert the keyboard to human readable string.
     * @param rows the keyboard to be converted to string.
     * @return the human readable representation of <code>rows</code>.
     */
    public static String toString(final ExpectedKey[][] rows) {
        final StringBuilder sb = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            if (rowIndex > 0) {
                sb.append("\n");
            }
            sb.append(Arrays.toString(rows[rowIndex]));
        }
        return sb.toString();
    }
}
