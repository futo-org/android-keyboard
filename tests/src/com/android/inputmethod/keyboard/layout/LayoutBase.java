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
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.AbstractLayoutBase;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * The base class of keyboard layout.
 */
public abstract class LayoutBase extends AbstractLayoutBase {
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
        getCustomizer().setAccentedLetters(builder, elementId);
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
            getCustomizer().setAccentedLetters(builder, elementId);
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
