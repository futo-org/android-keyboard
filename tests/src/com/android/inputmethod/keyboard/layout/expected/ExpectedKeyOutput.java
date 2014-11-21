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
import com.android.inputmethod.keyboard.internal.MoreKeySpec;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.Locale;

/**
 * This class represents an expected output of a key.
 *
 * There are two types of expected output, an integer code point and a string output text.
 */
abstract class ExpectedKeyOutput {
    static ExpectedKeyOutput newInstance(final int code) {
        return new Code(code);
    }

    static ExpectedKeyOutput newInstance(final String outputText) {
        // If the <code>outputText</code> is one code point string, use {@link CodePoint} object.
        if (StringUtils.codePointCount(outputText) == 1) {
            return new Code(outputText.codePointAt(0));
        }
        return new Text(outputText);
    }

    abstract ExpectedKeyOutput toUpperCase(final Locale locale);
    abstract ExpectedKeyOutput preserveCase();
    abstract boolean equalsTo(final String text);
    abstract boolean equalsTo(final Key key);
    abstract boolean equalsTo(final MoreKeySpec moreKeySpec);
    abstract boolean equalsTo(final ExpectedKeyOutput output);

    /**
     * This class represents an integer code point.
     */
    private static class Code extends ExpectedKeyOutput {
        // UNICODE code point or a special negative value defined in {@link Constants}.
        private final int mCode;

        Code(final int code) { mCode = code; }

        @Override
        ExpectedKeyOutput toUpperCase(final Locale locale) {
            if (Constants.isLetterCode(mCode)) {
                final String codeString = StringUtils.newSingleCodePointString(mCode);
                // A letter may have an upper case counterpart that consists of multiple code
                // points, for instance the upper case of "ß" is "SS".
                return newInstance(StringUtils.toUpperCaseOfStringForLocale(
                        codeString, true /* needsToUpperCase */, locale));
            }
            // A special negative value has no upper case.
            return this;
        }

        @Override
        ExpectedKeyOutput preserveCase() {
            return new CasePreservedCode(mCode);
        }

        @Override
        boolean equalsTo(final String text) {
            return StringUtils.codePointCount(text) == 1 && text.codePointAt(0) == mCode;
        }

        @Override
        boolean equalsTo(final Key key) {
            return mCode == key.getCode();
        }

        @Override
        boolean equalsTo(final MoreKeySpec moreKeySpec) {
            return mCode == moreKeySpec.mCode;
        }

        @Override
        boolean equalsTo(final ExpectedKeyOutput output) {
            return (output instanceof Code) && mCode == ((Code)output).mCode;
        }

        @Override
        public String toString() {
            return Constants.isLetterCode(mCode) ? StringUtils.newSingleCodePointString(mCode)
                    : Constants.printableCode(mCode);
        }

        private static class CasePreservedCode extends Code {
            CasePreservedCode(final int code) { super(code); }

            @Override
            ExpectedKeyOutput toUpperCase(final Locale locale) { return this; }

            @Override
            ExpectedKeyOutput preserveCase() { return this; }
        }
    }

    /**
     * This class represents a string output text.
     */
    private static class Text extends ExpectedKeyOutput {
        private final String mText;

        Text(final String text) { mText = text; }

        @Override
        ExpectedKeyOutput toUpperCase(final Locale locale) {
            return newInstance(mText.toUpperCase(locale));
        }

        @Override
        ExpectedKeyOutput preserveCase() {
            return new CasePreservedText(mText);
        }

        @Override
        boolean equalsTo(final String text) {
            return text.equals(text);
        }

        @Override
        boolean equalsTo(final Key key) {
            return key.getCode() == Constants.CODE_OUTPUT_TEXT
                    && mText.equals(key.getOutputText());
        }

        @Override
        boolean equalsTo(final MoreKeySpec moreKeySpec) {
            return moreKeySpec.mCode == Constants.CODE_OUTPUT_TEXT
                    && mText.equals(moreKeySpec.mOutputText);
        }

        @Override
        boolean equalsTo(final ExpectedKeyOutput output) {
            return (output instanceof Text) && mText == ((Text)output).mText;
        }

        @Override
        public String toString() {
            return mText;
        }

        private static class CasePreservedText extends Text {
            CasePreservedText(final String text) { super(text); }

            @Override
            ExpectedKeyOutput toUpperCase(final Locale locale) { return this; }

            @Override
            ExpectedKeyOutput preserveCase() { return this; }
        }
    }
}
