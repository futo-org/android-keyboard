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
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.Locale;

/**
 * This class represents an expected visual outlook of a key.
 *
 * There are two types of expected visual, an integer icon id and a string label.
 */
abstract class ExpectedKeyVisual {
    static ExpectedKeyVisual newInstance(final String label) {
        return new Label(label);
    }

    static ExpectedKeyVisual newInstance(final int iconId) {
        return new Icon(iconId);
    }

    abstract ExpectedKeyVisual toUpperCase(final Locale locale);
    abstract ExpectedKeyVisual preserveCase();
    abstract boolean equalsTo(final String text);
    abstract boolean equalsTo(final Key key);
    abstract boolean equalsTo(final MoreKeySpec moreKeySpec);
    abstract boolean equalsTo(final ExpectedKeyOutput output);
    abstract boolean equalsTo(final ExpectedKeyVisual visual);

    /**
     * This class represents an integer icon id.
     */
    private static class Icon extends ExpectedKeyVisual {
        private final int mIconId;

        Icon(final int iconId) {
            mIconId = iconId;
        }

        @Override
        ExpectedKeyVisual toUpperCase(final Locale locale) {
            return this;
        }

        @Override
        ExpectedKeyVisual preserveCase() {
            return this;
        }

        @Override
        boolean equalsTo(final String text) {
            return false;
        }

        @Override
        boolean equalsTo(final Key key) {
            return mIconId == key.getIconId();
        }

        @Override
        boolean equalsTo(final MoreKeySpec moreKeySpec) {
            return mIconId == moreKeySpec.mIconId;
        }

        @Override
        boolean equalsTo(final ExpectedKeyOutput output) {
            return false;
        }

        @Override
        boolean equalsTo(final ExpectedKeyVisual visual) {
            return (visual instanceof Icon) && mIconId == ((Icon)visual).mIconId;
        }

        @Override
        public String toString() {
            return KeyboardIconsSet.getIconName(mIconId);
        }
    }

    /**
     * This class represents a string label.
     */
    private static class Label extends ExpectedKeyVisual {
        private final String mLabel;

        Label(final String label) { mLabel = label; }

        @Override
        ExpectedKeyVisual toUpperCase(final Locale locale) {
            return new Label(StringUtils.toUpperCaseOfStringForLocale(
                    mLabel, true /* needsToUpperCase */, locale));
        }

        @Override
        ExpectedKeyVisual preserveCase() {
            return new CasePreservedLabel(mLabel);
        }

        @Override
        boolean equalsTo(final String text) {
            return mLabel.equals(text);
        }

        @Override
        boolean equalsTo(final Key key) {
            return mLabel.equals(key.getLabel());
        }

        @Override
        boolean equalsTo(final MoreKeySpec moreKeySpec) {
            return mLabel.equals(moreKeySpec.mLabel);
        }

        @Override
        boolean equalsTo(final ExpectedKeyOutput output) {
            return output.equalsTo(mLabel);
        }

        @Override
        boolean equalsTo(final ExpectedKeyVisual visual) {
            return (visual instanceof Label) && mLabel.equals(((Label)visual).mLabel);
        }

        @Override
        public String toString() {
            return mLabel;
        }

        private static class CasePreservedLabel extends Label {
            CasePreservedLabel(final String label) { super(label); }

            @Override
            ExpectedKeyVisual toUpperCase(final Locale locale) { return this; }

            @Override
            ExpectedKeyVisual preserveCase() { return this; }
        }
    }
}
