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
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey.ExpectedAdditionalMoreKey;
import com.android.inputmethod.latin.common.Constants;

/**
 * Base class to create an expected keyboard for unit test.
 */
public abstract class AbstractLayoutBase {
    // Those helper methods have a lower case name to be readable when defining expected keyboard
    // layouts.

    // Helper method to create an {@link ExpectedKey} object that has the label.
    public static ExpectedKey key(final String label, final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(label, moreKeys);
    }

    // Helper method to create an {@link ExpectedKey} object that has the label and the output text.
    public static ExpectedKey key(final String label, final String outputText,
            final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(label, outputText, moreKeys);
    }

    // Helper method to create an {@link ExpectedKey} object that has the label and the output code.
    public static ExpectedKey key(final String label, final int code,
            final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(label, code, moreKeys);
    }

    // Helper method to create an {@link ExpectedKey} object that has the icon and the output text.
    public static ExpectedKey key(final int iconId, final String outputText,
            final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(iconId, outputText, moreKeys);
    }

    // Helper method to create an {@link ExpectedKey} object that has the icon and the output code.
    public static ExpectedKey key(final int iconId, final int code,
            final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(iconId, code, moreKeys);
    }

    // Helper method to create an {@link ExpectedKey} object that has new "more keys".
    public static ExpectedKey key(final ExpectedKey key, final ExpectedKey ... moreKeys) {
        return ExpectedKey.newInstance(key.getVisual(), key.getOutput(), moreKeys);
    }

    // Helper method to create an {@link ExpectedAdditionalMoreKey} object for an
    // "additional more key" that has the label.
    // The additional more keys can be defined independently from other more keys. The position of
    // the additional more keys in the long press popup keyboard can be controlled by specifying
    // special marker "%" in the usual more keys definitions.
    public static ExpectedAdditionalMoreKey additionalMoreKey(final String label) {
        return ExpectedAdditionalMoreKey.newInstance(label);
    }

    // Helper method to create an {@link ExpectedKey} object for a "more key" that has the label.
    public static ExpectedKey moreKey(final String label) {
        return ExpectedKey.newInstance(label);
    }

    // Helper method to create an {@link ExpectedKey} object for a "more key" that has the label
    // and the output text.
    public static ExpectedKey moreKey(final String label, final String outputText) {
        return ExpectedKey.newInstance(label, outputText);
    }

    // Helper method to create an {@link ExpectedKey} object for a "more key" that has the label
    // and the output code.
    public static ExpectedKey moreKey(final String label, final int code) {
        return ExpectedKey.newInstance(label, code);
    }

    // Helper method to create an {@link ExpectedKey} object for a "more key" that has the icon
    // and the output text.
    public static ExpectedKey moreKey(final int iconId, final String outputText) {
        return ExpectedKey.newInstance(iconId, outputText);
    }

    // Helper method to create {@link ExpectedKey} array by joining {@link ExpectedKey},
    // {@link ExpectedKey} array, and {@link String}.
    public static ExpectedKey[] joinMoreKeys(final Object ... moreKeys) {
        return joinKeys(moreKeys);
    }

    // Helper method to create {@link ExpectedKey} array by joining {@link ExpectedKey},
    // {@link ExpectedKey} array, and {@link String}.
    public static ExpectedKey[] joinKeys(final Object ... keys) {
        return ExpectedKeyboardBuilder.joinKeys(keys);
    }

    // Icon ids.
    private static final int ICON_DELETE = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_DELETE_KEY);
    private static final int ICON_SPACE = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_SPACE_KEY);
    private static final int ICON_TAB = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_TAB_KEY);
    private static final int ICON_SHORTCUT = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_SHORTCUT_KEY);
    private static final int ICON_SETTINGS = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_SETTINGS_KEY);
    private static final int ICON_LANGUAGE_SWITCH = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY);
    private static final int ICON_ENTER = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_ENTER_KEY);
    private static final int ICON_EMOJI_ACTION = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_EMOJI_ACTION_KEY);
    private static final int ICON_EMOJI_NORMAL = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY);
    private static final int ICON_SHIFT = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_SHIFT_KEY);
    private static final int ICON_SHIFTED_SHIFT = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_SHIFT_KEY_SHIFTED);
    private static final int ICON_ZWNJ = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_ZWNJ_KEY);
    private static final int ICON_ZWJ = KeyboardIconsSet.getIconId(
            KeyboardIconsSet.NAME_ZWJ_KEY);

    // Functional keys.
    protected static final ExpectedKey DELETE_KEY = key(ICON_DELETE, Constants.CODE_DELETE);
    protected static final ExpectedKey TAB_KEY = key(ICON_TAB, Constants.CODE_TAB);
    protected static final ExpectedKey SHORTCUT_KEY = key(ICON_SHORTCUT, Constants.CODE_SHORTCUT);
    protected static final ExpectedKey SETTINGS_KEY = key(ICON_SETTINGS, Constants.CODE_SETTINGS);
    protected static final ExpectedKey LANGUAGE_SWITCH_KEY = key(
            ICON_LANGUAGE_SWITCH, Constants.CODE_LANGUAGE_SWITCH);
    protected static final ExpectedKey ENTER_KEY = key(ICON_ENTER, Constants.CODE_ENTER);
    protected static final ExpectedKey EMOJI_ACTION_KEY = key(ICON_EMOJI_ACTION, Constants.CODE_EMOJI);
    protected static final ExpectedKey EMOJI_NORMAL_KEY = key(ICON_EMOJI_NORMAL, Constants.CODE_EMOJI);
    protected static final ExpectedKey SPACE_KEY = key(ICON_SPACE, Constants.CODE_SPACE);
    protected static final ExpectedKey CAPSLOCK_MORE_KEY = key(" ", Constants.CODE_CAPSLOCK);
    protected static final ExpectedKey SHIFT_KEY = key(ICON_SHIFT,
            Constants.CODE_SHIFT, CAPSLOCK_MORE_KEY);
    protected static final ExpectedKey SHIFTED_SHIFT_KEY = key(ICON_SHIFTED_SHIFT,
            Constants.CODE_SHIFT, CAPSLOCK_MORE_KEY);
    protected static final ExpectedKey ALPHABET_KEY = key("ABC", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    protected static final ExpectedKey SYMBOLS_KEY = key("?123", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    protected static final ExpectedKey BACK_TO_SYMBOLS_KEY = key("?123", Constants.CODE_SHIFT);
    protected static final ExpectedKey SYMBOLS_SHIFT_KEY = key("= \\ <", Constants.CODE_SHIFT);
    protected static final ExpectedKey TABLET_SYMBOLS_SHIFT_KEY = key("~ [ <", Constants.CODE_SHIFT);

    // U+00A1: "¡" INVERTED EXCLAMATION MARK
    // U+00BF: "¿" INVERTED QUESTION MARK
    protected static final ExpectedKey[] EXCLAMATION_AND_QUESTION_MARKS = joinKeys(
            key("!", moreKey("\u00A1")), key("?", moreKey("\u00BF")));
    // U+200C: ZERO WIDTH NON-JOINER
    // U+200D: ZERO WIDTH JOINER
    protected static final ExpectedKey ZWNJ_KEY = key(ICON_ZWNJ, "\u200C");
    protected static final ExpectedKey ZWJ_KEY = key(ICON_ZWJ, "\u200D");
    // Domain key
    protected static final ExpectedKey DOMAIN_KEY =
            key(".com", joinMoreKeys(".net", ".org", ".gov", ".edu")).preserveCase();

    // Punctuation more keys for phone form factor.
    protected static final ExpectedKey[] PHONE_PUNCTUATION_MORE_KEYS = joinKeys(
            ",", "?", "!", "#", ")", "(", "/", ";",
            "'", "@", ":", "-", "\"", "+", "%", "&");
    // Punctuation more keys for tablet form factor.
    protected static final ExpectedKey[] TABLET_PUNCTUATION_MORE_KEYS = joinKeys(
            ",", "'", "#", ")", "(", "/", ";",
            "@", ":", "-", "\"", "+", "%", "&");
}
