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

package com.android.inputmethod.keyboard.layout.customizer;

import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.SymbolsShifted;
import com.android.inputmethod.keyboard.layout.expected.AbstractLayoutBase;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * This class is used to customize common keyboard layout to language specific layout.
 */
public class LayoutCustomizer extends AbstractLayoutBase {
    private final Locale mLocale;

    // Empty keys definition to remove keys by adding this.
    protected static final ExpectedKey[] EMPTY_KEYS = joinKeys();

    public LayoutCustomizer(final Locale locale) {  mLocale = locale; }

    public final Locale getLocale() { return mLocale; }

    public int getNumberOfRows() { return 4; }

    /**
     * Set accented letters to a specific keyboard element.
     * @param builder the {@link ExpectedKeyboardBuilder} object that contains common keyboard
     *        layout.
     * @param elementId the element id of keyboard
     * @return the {@link ExpectedKeyboardBuilder} object that contains accented letters as
     *        "more keys".
     */
    public ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder,
            final int elementId) {
        // This method can be overridden by an extended class to provide customized expected
        // accented letters depending on the shift state of keyboard.
        // This is a default behavior to call a shift-state-independent
        // {@link #setAccentedLetters(ExpectedKeyboardBuilder)} implementation, so that
        // <code>elementId</code> is ignored here.
        return setAccentedLetters(builder);
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
