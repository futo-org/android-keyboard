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

import com.android.inputmethod.keyboard.layout.expected.ExpectedKey.ExpectedAdditionalMoreKey;

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
}
