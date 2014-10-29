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
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.StringUtils;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class builds an actual keyboard for unit test.
 *
 * An actual keyboard is an array of rows, and a row consists of an array of {@link Key}s.
 * Each row may have different number of {@link Key}s.
 */
public final class ActualKeyboardBuilder extends AbstractKeyboardBuilder<Key> {
    private static ArrayList<Key> filterOutSpacer(final List<Key> keys) {
        final ArrayList<Key> filteredKeys = new ArrayList<>();
        for (final Key key : keys) {
            if (key.isSpacer()) {
                continue;
            }
            filteredKeys.add(key);
        }
        return filteredKeys;
    }

    /**
     * Create the keyboard that consists of the array of rows of the actual keyboard's keys.
     * @param sortedKeys keys list of the actual keyboard that is sorted from top-left to
     * bottom-right.
     * @return the actual keyboard grouped with rows.
     */
    public static Key[][] buildKeyboard(final List<Key> sortedKeys) {
        // Filter out spacer to prepare to create rows.
        final ArrayList<Key> filteredSortedKeys = filterOutSpacer(sortedKeys);

        // Grouping keys into rows.
        final ArrayList<ArrayList<Key>> rows = new ArrayList<>();
        ArrayList<Key> elements = new ArrayList<>();
        int lastY = filteredSortedKeys.get(0).getY();
        for (final Key key : filteredSortedKeys) {
            if (lastY != key.getY()) {
                // A new row is starting.
                lastY = key.getY();
                rows.add(elements);
                elements = new ArrayList<>();
            }
            elements.add(key);
        }
        rows.add(elements); // Add the last row.

        // Calculate each dimension of rows and create a builder.
        final int[] dimensions = new int[rows.size()];
        for (int rowIndex = 0; rowIndex < dimensions.length; rowIndex++) {
            dimensions[rowIndex] = rows.get(rowIndex).size();
        }
        final ActualKeyboardBuilder builder = new ActualKeyboardBuilder();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            final int row = rowIndex + 1;
            final ArrayList<Key> rowKeys = rows.get(rowIndex);
            builder.setRowAt(row, rowKeys.toArray(new Key[rowKeys.size()]));
        }
        return builder.build();
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
            if (spec == null) {
                return "null";
            }
            return toString(spec.mLabel, spec.mIconId, spec.mOutputText, spec.mCode);
        }

        @Nonnull
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
        public String stringize(@Nullable final Key key) {
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
    @Nonnull
    public static String toString(@Nullable final Key key) {
        return KeyStringizer.STRINGIZER.stringize(key);
    }

    /**
     * Convert the keyboard row to human readable string.
     * @param keys the keyboard row to be converted to string.
     * @return the human readable representation of <code>keys</code>.
     */
    @Nonnull
    public static String toString(@Nullable final Key[] keys) {
        return KeyStringizer.STRINGIZER.join(keys);
    }

    // Helper class to create concise representation from the array of the key.
    static class KeyArrayStringizer extends StringUtils.Stringizer<Key[]> {
        static final KeyArrayStringizer STRINGIZER = new KeyArrayStringizer();

        @Override
        public String stringize(@Nullable final Key[] keyArray) {
            return KeyStringizer.STRINGIZER.join(keyArray);
        }
    }

    /**
     * Convert the keyboard to human readable string.
     * @param rows the keyboard to be converted to string.
     * @return the human readable representation of <code>rows</code>.
     */
    @Nonnull
    public static String toString(@Nullable final Key[][] rows) {
        return KeyArrayStringizer.STRINGIZER.join(rows, "\n" /* delimiter */);
    }
}
