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

package com.android.inputmethod.keyboard.layout;

import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.layout.expected.AbstractLayoutBase;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.Constants;

import java.util.Locale;

/**
 * The base class of keyboard layout.
 */
public abstract class LayoutBase extends AbstractLayoutBase {
    /**
     * This class is used to customize common keyboard layout to language specific layout.
     */
    public static class LayoutCustomizer {
        private final Locale mLocale;

        // Empty keys definition to remove keys by adding this.
        protected static final ExpectedKey[] EMPTY_KEYS = joinKeys();

        public LayoutCustomizer(final Locale locale) {
            mLocale = locale;
        }

        public final Locale getLocale() {
            return mLocale;
        }

        public int getNumberOfRows() {
            return 4;
        }

        /**
         * Set accented letters to common layout.
         * @param builder the {@link ExpectedKeyboardBuilder} object that contains common keyboard
         *        layout.
         * @return the {@link ExpectedKeyboardBuilder} object that contains accented letters as
         *        "more keys".
         */
        public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
            return builder;
        }

        /**
         * Get the function key to switch to alphabet layout.
         * @return the {@link ExpectedKey} of the alphabet key.
         */
        public ExpectedKey getAlphabetKey() { return ALPHABET_KEY; }

        /**
         * Get the function key to switch to symbols layout.
         * @return the {@link ExpectedKey} of the symbols key.
         */
        public ExpectedKey getSymbolsKey() { return SYMBOLS_KEY; }

        /**
         * Get the function key to switch to symbols shift layout.
         * @param isPhone true if requesting phone's key.
         * @return the {@link ExpectedKey} of the symbols shift key.
         */
        public ExpectedKey getSymbolsShiftKey(boolean isPhone) {
            return isPhone ? SYMBOLS_SHIFT_KEY : TABLET_SYMBOLS_SHIFT_KEY;
        }

        /**
         * Get the function key to switch from symbols shift to symbols layout.
         * @return the {@link ExpectedKey} of the back to symbols key.
         */
        public ExpectedKey getBackToSymbolsKey() { return BACK_TO_SYMBOLS_KEY; }

        /**
         * Get the currency key.
         * @return the {@link ExpectedKey} of the currency key.
         */
        public ExpectedKey getCurrencyKey() { return Symbols.CURRENCY_DOLLAR; }

        /**
         * Get other currencies keys.
         * @return the array of {@link ExpectedKey} that represents other currency keys.
         */
        public ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_THAN_DOLLAR;
        }

        /**
         * Get "more keys" of double quotation mark.
         * @return the array of {@link ExpectedKey} of more double quotation marks in natural order.
         */
        public ExpectedKey[] getDoubleQuoteMoreKeys() { return Symbols.DOUBLE_QUOTES_9LR; }

        /**
         * Get "more keys" of single quotation mark.
         * @return the array of {@link ExpectedKey} of more single quotation marks in natural order.
         */
        public ExpectedKey[] getSingleQuoteMoreKeys() { return Symbols.SINGLE_QUOTES_9LR; }

        /**
         * Get double angle quotation marks in natural order.
         * @return the array of {@link ExpectedKey} of double angle quotation marks in natural
         *         order.
         */
        public ExpectedKey[] getDoubleAngleQuoteKeys() { return Symbols.DOUBLE_ANGLE_QUOTES_LR; }

        /**
         * Get single angle quotation marks in natural order.
         * @return the array of {@link ExpectedKey} of single angle quotation marks in natural
         *         order.
         */
        public ExpectedKey[] getSingleAngleQuoteKeys() { return Symbols.SINGLE_ANGLE_QUOTES_LR; }

        /**
         * Get the left shift keys.
         * @param isPhone true if requesting phone's keys.
         * @return the array of {@link ExpectedKey} that should be placed at left edge of the
         *         keyboard.
         */
        public ExpectedKey[] getLeftShiftKeys(final boolean isPhone) {
            return joinKeys(SHIFT_KEY);
        }

        /**
         * Get the right shift keys.
         * @param isPhone true if requesting phone's keys.
         * @return the array of {@link ExpectedKey} that should be placed at right edge of the
         *         keyboard.
         */
        public ExpectedKey[] getRightShiftKeys(final boolean isPhone) {
            return isPhone ? EMPTY_KEYS : joinKeys(EXCLAMATION_AND_QUESTION_MARKS, SHIFT_KEY);
        }

        /**
         * Get the enter key.
         * @param isPhone true if requesting phone's key.
         * @return the array of {@link ExpectedKey} that should be placed as an enter key.
         */
        public ExpectedKey getEnterKey(final boolean isPhone) {
            return isPhone ? key(ENTER_KEY, EMOJI_ACTION_KEY) : ENTER_KEY;
        }

        /**
         * Get the emoji key.
         * @param isPhone true if requesting phone's key.
         * @return the array of {@link ExpectedKey} that should be placed as an emoji key.
         */
        public ExpectedKey getEmojiKey(final boolean isPhone) {
            return EMOJI_NORMAL_KEY;
        }

        /**
         * Get the space keys.
         * @param isPhone true if requesting phone's keys.
         * @return the array of {@link ExpectedKey} that should be placed at the center of the
         *         keyboard.
         */
        public ExpectedKey[] getSpaceKeys(final boolean isPhone) {
            return joinKeys(LANGUAGE_SWITCH_KEY, SPACE_KEY);
        }

        /**
         * Get the keys left to the spacebar.
         * @param isPhone true if requesting phone's keys.
         * @return the array of {@link ExpectedKey} that should be placed at left of the spacebar.
         */
        public ExpectedKey[] getKeysLeftToSpacebar(final boolean isPhone) {
            // U+002C: "," COMMA
            return joinKeys(key("\u002C", SETTINGS_KEY));
        }

        /**
         * Get the keys right to the spacebar.
         * @param isPhone true if requesting phone's keys.
         * @return the array of {@link ExpectedKey} that should be placed at right of the spacebar.
         */
        public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
            final ExpectedKey periodKey = key(".", getPunctuationMoreKeys(isPhone));
            return joinKeys(periodKey);
        }

        /**
         * Get "more keys" for the punctuation key (usually the period key).
         * @param isPhone true if requesting phone's keys.
         * @return the array of {@link ExpectedKey} that are "more keys" of the punctuation key.
         */
        public ExpectedKey[] getPunctuationMoreKeys(final boolean isPhone) {
            return isPhone ? PHONE_PUNCTUATION_MORE_KEYS : TABLET_PUNCTUATION_MORE_KEYS;
        }
    }

    /**
     * The layout customize class for countries that use Euro.
     */
    public static class EuroCustomizer extends LayoutCustomizer {
        public EuroCustomizer(final Locale locale) {
            super(locale);
        }

        @Override
        public final ExpectedKey getCurrencyKey() { return Symbols.CURRENCY_EURO; }

        @Override
        public final ExpectedKey[] getOtherCurrencyKeys() {
            return SymbolsShifted.CURRENCIES_OTHER_THAN_EURO;
        }
    }

    private final LayoutCustomizer mCustomizer;
    private final Symbols mSymbols;
    private final SymbolsShifted mSymbolsShifted;

    LayoutBase(final LayoutCustomizer customizer, final Class<? extends Symbols> symbolsClass,
            final Class<? extends SymbolsShifted> symbolsShiftedClass) {
        mCustomizer = customizer;
        try {
            mSymbols = symbolsClass.getDeclaredConstructor(LayoutCustomizer.class)
                    .newInstance(customizer);
            mSymbolsShifted = symbolsShiftedClass.getDeclaredConstructor(LayoutCustomizer.class)
                    .newInstance(customizer);
        } catch (final Exception e) {
            throw new RuntimeException("Unknown Symbols/SymbolsShifted class", e);
        }
    }

    /**
     * The layout name.
     * @return the name of this layout.
     */
    public abstract String getName();

    /**
     * The locale of this layout.
     * @return the locale of this layout.
     */
    public final Locale getLocale() { return mCustomizer.getLocale(); }

    /**
     * The layout customizer for this layout.
     * @return the layout customizer;
     */
    public final LayoutCustomizer getCustomizer() { return mCustomizer; }

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
    public static final ExpectedKey DELETE_KEY = key(ICON_DELETE, Constants.CODE_DELETE);
    public static final ExpectedKey TAB_KEY = key(ICON_TAB, Constants.CODE_TAB);
    public static final ExpectedKey SHORTCUT_KEY = key(ICON_SHORTCUT, Constants.CODE_SHORTCUT);
    public static final ExpectedKey SETTINGS_KEY = key(ICON_SETTINGS, Constants.CODE_SETTINGS);
    public static final ExpectedKey LANGUAGE_SWITCH_KEY = key(
            ICON_LANGUAGE_SWITCH, Constants.CODE_LANGUAGE_SWITCH);
    public static final ExpectedKey ENTER_KEY = key(ICON_ENTER, Constants.CODE_ENTER);
    public static final ExpectedKey EMOJI_ACTION_KEY = key(ICON_EMOJI_ACTION, Constants.CODE_EMOJI);
    public static final ExpectedKey EMOJI_NORMAL_KEY = key(ICON_EMOJI_NORMAL, Constants.CODE_EMOJI);
    public static final ExpectedKey SPACE_KEY = key(ICON_SPACE, Constants.CODE_SPACE);
    static final ExpectedKey CAPSLOCK_MORE_KEY = key(" ", Constants.CODE_CAPSLOCK);
    public static final ExpectedKey SHIFT_KEY = key(ICON_SHIFT,
            Constants.CODE_SHIFT, CAPSLOCK_MORE_KEY);
    public static final ExpectedKey SHIFTED_SHIFT_KEY = key(ICON_SHIFTED_SHIFT,
            Constants.CODE_SHIFT, CAPSLOCK_MORE_KEY);
    static final ExpectedKey ALPHABET_KEY = key("ABC", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    static final ExpectedKey SYMBOLS_KEY = key("?123", Constants.CODE_SWITCH_ALPHA_SYMBOL);
    static final ExpectedKey BACK_TO_SYMBOLS_KEY = key("?123", Constants.CODE_SHIFT);
    static final ExpectedKey SYMBOLS_SHIFT_KEY = key("= \\ <", Constants.CODE_SHIFT);
    static final ExpectedKey TABLET_SYMBOLS_SHIFT_KEY = key("~ [ <", Constants.CODE_SHIFT);

    // U+00A1: "¡" INVERTED EXCLAMATION MARK
    // U+00BF: "¿" INVERTED QUESTION MARK
    static final ExpectedKey[] EXCLAMATION_AND_QUESTION_MARKS = joinKeys(
            key("!", moreKey("\u00A1")), key("?", moreKey("\u00BF")));
    // U+200C: ZERO WIDTH NON-JOINER
    // U+200D: ZERO WIDTH JOINER
    static final ExpectedKey ZWNJ_KEY = key(ICON_ZWNJ, "\u200C");
    static final ExpectedKey ZWJ_KEY = key(ICON_ZWJ, "\u200D");
    // Domain key
    public static final ExpectedKey DOMAIN_KEY =
            key(".com", joinMoreKeys(".net", ".org", ".gov", ".edu")).preserveCase();

    // Punctuation more keys for phone form factor.
    public static final ExpectedKey[] PHONE_PUNCTUATION_MORE_KEYS = joinKeys(
            ",", "?", "!", "#", ")", "(", "/", ";",
            "'", "@", ":", "-", "\"", "+", "%", "&");
    // Punctuation more keys for tablet form factor.
    public static final ExpectedKey[] TABLET_PUNCTUATION_MORE_KEYS = joinKeys(
            ",", "'", "#", ")", "(", "/", ";",
            "@", ":", "-", "\"", "+", "%", "&");

    /**
     * Helper method to create alphabet layout adding special function keys.
     * @param builder the {@link ExpectedKeyboardBuilder} object that contains common keyboard
     *     layout
     * @param isPhone true if requesting phone's layout.
     * @return the {@link ExpectedKeyboardBuilder} object that is customized and have special keys.
     */
    ExpectedKeyboardBuilder convertCommonLayoutToKeyboard(final ExpectedKeyboardBuilder builder,
            final boolean isPhone) {
        final LayoutCustomizer customizer = getCustomizer();
        final int numberOfRows = customizer.getNumberOfRows();
        builder.setKeysOfRow(numberOfRows, (Object[])customizer.getSpaceKeys(isPhone));
        builder.addKeysOnTheLeftOfRow(
                numberOfRows, (Object[])customizer.getKeysLeftToSpacebar(isPhone));
        builder.addKeysOnTheRightOfRow(
                numberOfRows, (Object[])customizer.getKeysRightToSpacebar(isPhone));
        if (isPhone) {
            builder.addKeysOnTheRightOfRow(numberOfRows - 1, DELETE_KEY)
                    .addKeysOnTheLeftOfRow(numberOfRows, customizer.getSymbolsKey())
                    .addKeysOnTheRightOfRow(numberOfRows, customizer.getEnterKey(isPhone));
        } else {
            builder.addKeysOnTheRightOfRow(1, DELETE_KEY)
                    .addKeysOnTheRightOfRow(numberOfRows - 2, customizer.getEnterKey(isPhone))
                    .addKeysOnTheLeftOfRow(numberOfRows, customizer.getSymbolsKey())
                    .addKeysOnTheRightOfRow(numberOfRows, customizer.getEmojiKey(isPhone));
        }
        builder.addKeysOnTheLeftOfRow(
                numberOfRows - 1, (Object[])customizer.getLeftShiftKeys(isPhone));
        builder.addKeysOnTheRightOfRow(
                numberOfRows - 1, (Object[])customizer.getRightShiftKeys(isPhone));
        return builder;
    }

    /**
     * Get common alphabet layout. This layout doesn't contain any special keys.
     *
     * A keyboard layout is an array of rows, and a row consists of an array of
     * {@link ExpectedKey}s. Each row may have different number of {@link ExpectedKey}s.
     *
     * @param isPhone true if requesting phone's layout.
     * @return the common alphabet keyboard layout.
     */
    abstract ExpectedKey[][] getCommonAlphabetLayout(boolean isPhone);

    /**
     * Get common alphabet shifted layout. This layout doesn't contain any special keys.
     *
     * A keyboard layout is an array of rows, and a row consists of an array of
     * {@link ExpectedKey}s. Each row may have different number of {@link ExpectedKey}s.
     *
     * @param isPhone true if requesting phone's layout.
     * @param elementId the element id of the requesting shifted mode.
     * @return the common alphabet shifted keyboard layout.
     */
    ExpectedKey[][] getCommonAlphabetShiftLayout(final boolean isPhone, final int elementId) {
        final ExpectedKeyboardBuilder builder = new ExpectedKeyboardBuilder(
                getCommonAlphabetLayout(isPhone));
        getCustomizer().setAccentedLetters(builder);
        builder.toUpperCase(getLocale());
        return builder.build();
    }

    /**
     * Get the complete expected keyboard layout.
     *
     * A keyboard layout is an array of rows, and a row consists of an array of
     * {@link ExpectedKey}s. Each row may have different number of {@link ExpectedKey}s.
     *
     * @param isPhone true if requesting phone's layout.
     * @param elementId the element id of the requesting keyboard mode.
     * @return the keyboard layout of the <code>elementId</code>.
     */
    public ExpectedKey[][] getLayout(final boolean isPhone, final int elementId) {
        if (elementId == KeyboardId.ELEMENT_SYMBOLS) {
            return mSymbols.getLayout(isPhone);
        }
        if (elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            return mSymbolsShifted.getLayout(isPhone);
        }
        final ExpectedKeyboardBuilder builder;
        if (elementId == KeyboardId.ELEMENT_ALPHABET) {
            builder = new ExpectedKeyboardBuilder(getCommonAlphabetLayout(isPhone));
            getCustomizer().setAccentedLetters(builder);
        } else {
            final ExpectedKey[][] commonLayout = getCommonAlphabetShiftLayout(isPhone, elementId);
            if (commonLayout == null) {
                return null;
            }
            builder = new ExpectedKeyboardBuilder(commonLayout);
        }
        convertCommonLayoutToKeyboard(builder, isPhone);
        if (elementId != KeyboardId.ELEMENT_ALPHABET) {
            builder.replaceKeysOfAll(SHIFT_KEY, SHIFTED_SHIFT_KEY);
        }
        return builder.build();
    }
}
