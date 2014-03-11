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

import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Base class to create an expected keyboard for unit test.
 */
public class LayoutBase {
    // Those helper methods have a lower case name to be readable when defining expected keyboard
    // layouts.

    // Helper method to create {@link ExpectedKey} object that has the label.
    public static ExpectedKey key(final String label, final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(label, moreKeys);
    }

    // Helper method to create {@link ExpectedKey} object that has the label and the output text.
    public static ExpectedKey key(final String label, final String outputText,
            final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(label, outputText, moreKeys);
    }

    // Helper method to create {@link ExpectedKey} object that has the label and the output code.
    public static ExpectedKey key(final String label, final int code,
            final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(label, code, moreKeys);
    }

    // Helper method to create {@link ExpectedKey} object that has the icon and the output code.
    public static ExpectedKey key(final int iconId, final int code,
            final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(iconId, code, moreKeys);
    }

    // Helper method to create {@link ExpectedKey} object that has new "more keys".
    public static ExpectedKey key(final ExpectedKey key, final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(key.getVisual(), key.getOutput(), moreKeys);
    }

    // Helper method to create {@link ExpectedKey} object for "more key" that has the label.
    public static ExpectedKey moreKey(final String label) {
        return ExpectedKey.newInstance(label);
    }

    // Helper method to create {@link ExpectedKey} object for "more key" that has the label and the
    // output text.
    public static ExpectedKey moreKey(final String label, final String outputText) {
        return ExpectedKey.newInstance(label, outputText);
    }

    // Helper method to create {@link ExpectedKey} object for "more key" that has the label and the
    // output code.
    public static ExpectedKey moreKey(final String label, final int code) {
        return ExpectedKey.newInstance(label, code);
    }

    // Helper method to create {@link ExpectedKey} array by joining {@link ExpectedKey},
    // {@link ExpectedKey} array, and {@link String}.
    public static ExpectedKey[] join(final Object ... keys) {
        final ArrayList<ExpectedKey> list = CollectionUtils.newArrayList();
        for (final Object key : keys) {
            if (key instanceof ExpectedKey) {
                list.add((ExpectedKey)key);
            } else if (key instanceof ExpectedKey[]) {
                list.addAll(Arrays.asList((ExpectedKey[])key));
            } else if (key instanceof String) {
                list.add(key((String)key));
            } else {
                throw new RuntimeException("Unknown expected key type: " + key);
            }
        }
        return list.toArray(new ExpectedKey[list.size()]);
    }

    // Icon ids.
    private static final int ICON_SHIFT = KeyboardIconsSet.getIconId("shift_key");
    private static final int ICON_DELETE = KeyboardIconsSet.getIconId("delete_key");
    private static final int ICON_SETTINGS = KeyboardIconsSet.getIconId("settings_key");
    private static final int ICON_ENTER = KeyboardIconsSet.getIconId("enter_key");
    private static final int ICON_EMOJI = KeyboardIconsSet.getIconId("emoji_key");

    // Functional keys.
    public static final ExpectedKey CAPSLOCK_MORE_KEY = key(" ", Constants.CODE_CAPSLOCK);
    public static final ExpectedKey SHIFT_KEY = key(ICON_SHIFT, Constants.CODE_SHIFT);
    public static final ExpectedKey DELETE_KEY = key(ICON_DELETE, Constants.CODE_DELETE);
    public static final ExpectedKey SYMBOLS_KEY = key("?123", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    public static final ExpectedKey SETTINGS_KEY = key(ICON_SETTINGS, Constants.CODE_SETTINGS);
    public static final ExpectedKey ENTER_KEY = key(ICON_ENTER, Constants.CODE_ENTER);
    public static final ExpectedKey EMOJI_KEY = key(ICON_EMOJI, Constants.CODE_EMOJI);

    // Punctuation more keys for phone form factor.
    public static final String[] PHONE_PUNCTUATION_MORE_KEYS = {
            ";", "/", "(", ")", "#", "!", ",", "?",
            "&", "%", "+", "\"", "-", ":", "'", "@"
    };

    // Punctuation more keys for tablet form factor.
    public static final String[] TABLET_PUNCTUATION_MORE_KEYS = {
            ";", "/", "(", ")", "#", "'", ",",
            "&", "%", "+", "\"", "-", ":", "@"
    };

    // Helper method to create alphabet layout for phone by adding special function keys except
    // shift key.
    private static ExpectedKeyboardBuilder convertToPhoneAlphabetKeyboardBuilder(
            final ExpectedKey[][] commonLayout) {
        return new ExpectedKeyboardBuilder(commonLayout)
                .addKeysOnTheRightOfRow(3, DELETE_KEY)
                .setLabelsOfRow(4, ",", " ", ".")
                .setMoreKeysOf(",", SETTINGS_KEY)
                .setMoreKeysOf(".", PHONE_PUNCTUATION_MORE_KEYS)
                .addKeysOnTheLeftOfRow(4, SYMBOLS_KEY)
                .addKeysOnTheRightOfRow(4, key(ENTER_KEY, EMOJI_KEY));
    }

    // Helper method to create alphabet layout for tablet by adding special function keys except
    // shift key.
    private static ExpectedKeyboardBuilder convertToTabletAlphabetKeyboardBuilder(
            final ExpectedKey[][] commonLayout) {
        return new ExpectedKeyboardBuilder(commonLayout)
                // U+00BF: "¿" INVERTED QUESTION MARK
                // U+00A1: "¡" INVERTED EXCLAMATION MARK
                .addKeysOnTheRightOfRow(3,
                        key("!", moreKey("\u00A1")), key("?", moreKey("\u00BF")))
                .addKeysOnTheRightOfRow(1, DELETE_KEY)
                .addKeysOnTheRightOfRow(2, ENTER_KEY)
                .setLabelsOfRow(4, "/", " ", ",", ".")
                .setMoreKeysOf(".", TABLET_PUNCTUATION_MORE_KEYS)
                .addKeysOnTheLeftOfRow(4, SYMBOLS_KEY, SETTINGS_KEY)
                .addKeysOnTheRightOfRow(4, EMOJI_KEY);
    }

    // Helper method to create alphabet layout by adding special function keys.
    public static ExpectedKey[][] getAlphabetLayoutWithoutShiftKeys(
            final ExpectedKey[][] commonLayout, final boolean isPhone) {
        return isPhone ? convertToPhoneAlphabetKeyboardBuilder(commonLayout).build()
                : convertToTabletAlphabetKeyboardBuilder(commonLayout).build();
    }

    // Helper method to create alphabet layout by adding special function keys.
    public static ExpectedKey[][] getDefaultAlphabetLayout(final ExpectedKey[][] commonLayout,
            final boolean isPhone) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(
                getAlphabetLayoutWithoutShiftKeys(commonLayout, isPhone));
        if (isPhone) {
            builder.addKeysOnTheLeftOfRow(3, key(SHIFT_KEY, CAPSLOCK_MORE_KEY));
        } else {
            builder.addKeysOnTheLeftOfRow(3, key(SHIFT_KEY, CAPSLOCK_MORE_KEY))
                    .addKeysOnTheRightOfRow(3, key(SHIFT_KEY, CAPSLOCK_MORE_KEY));
        }
        return builder.build();
    }
}
