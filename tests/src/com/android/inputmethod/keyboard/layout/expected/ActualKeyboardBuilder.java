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

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.MoreKeySpec;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * This class builds an actual keyboard for unit test.
 */
public final class ActualKeyboardBuilder extends AbstractKeyboardBuilder<Key> {
    // Comparator to sort {@link Key}s from top-left to bottom-right order.
    private static final Comparator<Key> ROW_COLUMN_COMPARATOR = new Comparator<Key>() {
        @Override
        public int compare(final Key lhs, final Key rhs) {
            if (lhs.getY() < rhs.getY()) return -1;
            if (lhs.getY() > rhs.getY()) return 1;
            if (lhs.getX() < rhs.getX()) return -1;
            if (lhs.getX() > rhs.getX()) return 1;
            return 0;
        }
    };

    private static ArrayList<Key> filterOutSpacerAndSortKeys(final Key[] keys) {
        final ArrayList<Key> filteredKeys = CollectionUtils.newArrayList();
        for (final Key key : keys) {
            if (key.isSpacer()) {
                continue;
            }
            filteredKeys.add(key);
        }
        Collections.sort(filteredKeys, ROW_COLUMN_COMPARATOR);
        return filteredKeys;
    }

    /**
     * Create the keyboard that consists of the array of rows of the actual keyboard's keys.
     * @param keys the array of keys of the actual keyboard.
     * @return the actual keyboard grouped with rows.
     */
    public static Key[][] buildKeyboard(final Key[] keys) {
        // Filter out spacer and sort keys from top-left to bottom-right order to prepare to
        // create rows.
        final ArrayList<Key> sortedKeys = filterOutSpacerAndSortKeys(keys);

        // Grouping keys into rows.
        final ArrayList<ArrayList<Key>> rows = CollectionUtils.newArrayList();
        ArrayList<Key> elements = CollectionUtils.newArrayList();
        int lastY = sortedKeys.get(0).getY();
        for (final Key key : sortedKeys) {
            if (lastY != key.getY()) {
                // A new row is starting.
                lastY = key.getY();
                rows.add(elements);
                elements = CollectionUtils.newArrayList();
            }
            elements.add(key);
        }
        rows.add(elements); // Add the last row.

        // Calculate each dimension of rows and create a builder.
        final int[] dimensions = new int[rows.size()];
        for (int rowIndex = 0; rowIndex < dimensions.length; rowIndex++) {
            dimensions[rowIndex] = rows.get(rowIndex).size();
        }
        final ActualKeyboardBuilder builder = new ActualKeyboardBuilder(dimensions);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            final int row = rowIndex + 1;
            final ArrayList<Key> rowKeys = rows.get(rowIndex);
            builder.setRowAt(row, rowKeys.toArray(new Key[rowKeys.size()]));
        }
        return builder.build();
    }

    private ActualKeyboardBuilder(final int ... dimensions) {
        super(dimensions);
    }

    @Override
    Key defaultElement() { return null; }

    @Override
    Key[] newArray(final int size) { return new Key[size]; }

    @Override
    Key[][] newArrayOfArray(final int size) { return new Key[size][]; }

    // Helper class to create concise representation from the key specification.
    static class MoreKeySpecStringizer extends StringUtils.Stringizer<MoreKeySpec> {
        static final MoreKeySpecStringizer STRINGIZER = new MoreKeySpecStringizer();

        @Override
        public String stringize(final MoreKeySpec spec) {
            return toString(spec.mLabel, spec.mIconId, spec.mOutputText, spec.mCode);
        }

        static String toString(final String label, final int iconId, final String outputText,
                final int code) {
            final String visual = (iconId != KeyboardIconsSet.ICON_UNDEFINED)
                    ? KeyboardIconsSet.getIconName(iconId) : label;
            final String output;
            if (code == Constants.CODE_OUTPUT_TEXT) {
                output = outputText;
            } else if (code < Constants.CODE_SPACE) {
                output = Constants.printableCode(code);
            } else {
                output = StringUtils.newSingleCodePointString(code);
            }
            if (visual.equals(output)) {
                return visual;
            }
            return visual + "|" + output;
        }
    }

    // Helper class to create concise representation from the key.
    static class KeyStringizer extends StringUtils.Stringizer<Key> {
        static final KeyStringizer STRINGIZER = new KeyStringizer();

        @Override
        public String stringize(final Key key) {
            if (key == null) {
                return "NULL";
            }
            if (key.isSpacer()) {
                return "SPACER";
            }
            final StringBuilder sb = new StringBuilder();
            sb.append(MoreKeySpecStringizer.toString(
                    key.getLabel(), key.getIconId(), key.getOutputText(), key.getCode()));
            final MoreKeySpec[] moreKeys = key.getMoreKeys();
            if (moreKeys == null) {
                return sb.toString();
            }
            sb.append("^");
            sb.append(MoreKeySpecStringizer.STRINGIZER.join(moreKeys));
            return sb.toString();
        }
    }

    /**
     * Convert the key to human readable string.
     * @param key the key to be converted to string.
     * @return the human readable representation of <code>key</code>.
     */
    public static String toString(final Key key) {
        return KeyStringizer.STRINGIZER.stringize(key);
    }

    /**
     * Convert the keyboard row to human readable string.
     * @param keys the keyboard row to be converted to string.
     * @return the human readable representation of <code>keys</code>.
     */
    public static String toString(final Key[] keys) {
        return KeyStringizer.STRINGIZER.join(keys);
    }

    // Helper class to create concise representation from the array of the key.
    static class KeyArrayStringizer extends StringUtils.Stringizer<Key[]> {
        static final KeyArrayStringizer STRINGIZER = new KeyArrayStringizer();

        @Override
        public String stringize(final Key[] keyArray) {
            return KeyStringizer.STRINGIZER.join(keyArray);
        }
    }

    /**
     * Convert the keyboard to human readable string.
     * @param rows the keyboard to be converted to string.
     * @return the human readable representation of <code>rows</code>.
     */
    public static String toString(final Key[][] rows) {
        return KeyArrayStringizer.STRINGIZER.join(rows, "\n" /* delimiter */);
    }
}
