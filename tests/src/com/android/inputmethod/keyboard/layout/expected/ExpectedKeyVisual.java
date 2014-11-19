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
import com.android.inputmethod.latin.common.StringUtils;

import java.util.Locale;

/**
 * This class represents an expected visual outlook of a key.
 *
 * There are two types of expected visual, an integer icon id and a string label.
 */
public abstract class ExpectedKeyVisual {
    public static ExpectedKeyVisual newInstance(final String label) {
        return new Label(label);
    }

    public static ExpectedKeyVisual newInstance(final int iconId) {
        return new Icon(iconId);
    }

    public abstract int getIconId();
    public abstract String getLabel();
    abstract ExpectedKeyVisual toUpperCase(final Locale locale);
    abstract ExpectedKeyVisual preserveCase();
    abstract boolean hasSameKeyVisual(final String text);
    abstract boolean hasSameKeyVisual(final Key key);
    abstract boolean hasSameKeyVisual(final MoreKeySpec moreKeySpec);
    abstract boolean hasSameKeyVisual(final ExpectedKeyOutput output);
    abstract boolean hasSameKeyVisual(final ExpectedKeyVisual visual);

    /**
     * This class represents an integer icon id.
     */
    private static class Icon extends ExpectedKeyVisual {
        private final int mIconId;

        Icon(final int iconId) {
            mIconId = iconId;
        }

        @Override
        public int getIconId() {
            return mIconId;
        }

        @Override
        public String getLabel() {
            return null;
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
        boolean hasSameKeyVisual(final String text) {
            return false;
        }

        @Override
        boolean hasSameKeyVisual(final Key key) {
            // If the actual key has an icon as its visual, a label has to be null.
            // See {@link KeyboardView#onDrawKeyTopVisuals(Key,Canvas,Paint,KeyDrawParams).
            return mIconId == key.getIconId() && key.getLabel() == null;
        }

        @Override
        boolean hasSameKeyVisual(final MoreKeySpec moreKeySpec) {
            // If the actual more key has an icon as its visual, a label has to be null.
            // See {@link KeySpecParser#getIconId(String)} and
            // {@link KeySpecParser#getLabel(String)}.
            return mIconId == moreKeySpec.mIconId && moreKeySpec.mLabel == null;
        }

        @Override
        boolean hasSameKeyVisual(final ExpectedKeyOutput output) {
            return false;
        }

        @Override
        boolean hasSameKeyVisual(final ExpectedKeyVisual visual) {
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

        Label(final String label) {
            mLabel = label;
        }

        @Override
        public int getIconId() {
            return KeyboardIconsSet.ICON_UNDEFINED;
        }

        @Override
        public String getLabel() {
            return mLabel;
        }

        @Override
        ExpectedKeyVisual toUpperCase(final Locale locale) {
            return new Label(StringUtils.toTitleCaseOfKeyLabel(mLabel, locale));
        }

        @Override
        ExpectedKeyVisual preserveCase() {
            return new CasePreservedLabel(mLabel);
        }

        @Override
        boolean hasSameKeyVisual(final String text) {
            return mLabel.equals(text);
        }

        @Override
        boolean hasSameKeyVisual(final Key key) {
            // If the actual key has a label as its visual, an icon has to be undefined.
            // See {@link KeyboardView#onDrawKeyTopVisuals(Key,Canvas,Paint,KeyDrawParams).
            return mLabel.equals(key.getLabel())
                    && key.getIconId() == KeyboardIconsSet.ICON_UNDEFINED;
        }

        @Override
        boolean hasSameKeyVisual(final MoreKeySpec moreKeySpec) {
            // If the actual more key has a label as its visual, an icon has to be undefined.
            // See {@link KeySpecParser#getIconId(String)} and
            // {@link KeySpecParser#getLabel(String)}.
            return mLabel.equals(moreKeySpec.mLabel)
                    && moreKeySpec.mIconId == KeyboardIconsSet.ICON_UNDEFINED;
        }

        @Override
        boolean hasSameKeyVisual(final ExpectedKeyOutput output) {
            return output.hasSameKeyOutput(mLabel);
        }

        @Override
        boolean hasSameKeyVisual(final ExpectedKeyVisual visual) {
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
