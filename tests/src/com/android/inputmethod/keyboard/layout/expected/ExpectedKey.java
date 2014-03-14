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

import java.util.Arrays;
import java.util.Locale;

/**
 * This class represents an expected key.
 */
public class ExpectedKey {
    static ExpectedKey EMPTY_KEY = newInstance("");

    // A key that has a string label and may have "more keys".
    static ExpectedKey newInstance(final String label, final ExpectedKey ... moreKeys) {
        return newInstance(label, label, moreKeys);
    }

    // A key that has a string label and a different output text and may have "more keys".
    static ExpectedKey newInstance(final String label, final String outputText,
            final ExpectedKey ... moreKeys) {
        return newInstance(ExpectedKeyVisual.newInstance(label),
                ExpectedKeyOutput.newInstance(outputText), moreKeys);
    }

    // A key that has a string label and a code point output and may have "more keys".
    static ExpectedKey newInstance(final String label, final int code,
            final ExpectedKey ... moreKeys) {
        return newInstance(ExpectedKeyVisual.newInstance(label),
                ExpectedKeyOutput.newInstance(code), moreKeys);
    }

    // A key that has an icon and an output text and may have "more keys".
    static ExpectedKey newInstance(final int iconId, final String outputText,
            final ExpectedKey ... moreKeys) {
        return newInstance(ExpectedKeyVisual.newInstance(iconId),
                ExpectedKeyOutput.newInstance(outputText), moreKeys);
    }

    // A key that has an icon and a code point output and may have "more keys".
    static ExpectedKey newInstance(final int iconId, final int code,
            final ExpectedKey ... moreKeys) {
        return newInstance(ExpectedKeyVisual.newInstance(iconId),
                ExpectedKeyOutput.newInstance(code), moreKeys);
    }

    static ExpectedKey newInstance(final ExpectedKeyVisual visual, final ExpectedKeyOutput output,
            final ExpectedKey ... moreKeys) {
        if (moreKeys.length == 0) {
            return new ExpectedKey(visual, output);
        }
        return new ExpectedKeyWithMoreKeys(visual, output, moreKeys);
    }

    private static final ExpectedKey[] EMPTY_KEYS = new ExpectedKey[0];

    // The expected visual outlook of this key.
    private final ExpectedKeyVisual mVisual;
    // The expected output of this key.
    private final ExpectedKeyOutput mOutput;

    public final ExpectedKeyVisual getVisual() {
        return mVisual;
    }

    public final ExpectedKeyOutput getOutput() {
        return mOutput;
    }

    public ExpectedKey[] getMoreKeys() {
        // This key has no "more keys".
        return EMPTY_KEYS;
    }

    protected ExpectedKey(final ExpectedKeyVisual visual, final ExpectedKeyOutput output) {
        mVisual = visual;
        mOutput = output;
    }

    public ExpectedKey toUpperCase(Locale locale) {
        return newInstance(mVisual.toUpperCase(locale), mOutput.toUpperCase(locale));
    }

    public boolean equalsTo(final Key key) {
        // This key has no "more keys".
        return mVisual.equalsTo(key) && mOutput.equalsTo(key) && key.getMoreKeys() == null;
    }

    public boolean equalsTo(final MoreKeySpec moreKeySpec) {
        return mVisual.equalsTo(moreKeySpec) && mOutput.equalsTo(moreKeySpec);
    }

    @Override
    public boolean equals(final Object object) {
        if (object instanceof ExpectedKey) {
            final ExpectedKey key = (ExpectedKey)object;
            return mVisual.equalsTo(key.mVisual) && mOutput.equalsTo(key.mOutput)
                    && Arrays.equals(getMoreKeys(), key.getMoreKeys());
        }
        return false;
    }

    private static int hashCode(final Object ... objects) {
        return Arrays.hashCode(objects);
    }

    @Override
    public int hashCode() {
        return hashCode(mVisual, mOutput, getMoreKeys());
    }

    @Override
    public String toString() {
        if (mVisual.equalsTo(mOutput)) {
            return mVisual.toString();
        }
        return mVisual + "|" + mOutput;
    }

    /**
     * This class represents an expected key that has "more keys".
     */
    private static final class ExpectedKeyWithMoreKeys extends ExpectedKey {
        private final ExpectedKey[] mMoreKeys;

        ExpectedKeyWithMoreKeys(final ExpectedKeyVisual visual,
                final ExpectedKeyOutput output, final ExpectedKey ... moreKeys) {
            super(visual, output);
            mMoreKeys = moreKeys;
        }

        @Override
        public ExpectedKey toUpperCase(final Locale locale) {
            final ExpectedKey[] upperCaseMoreKeys = new ExpectedKey[mMoreKeys.length];
            for (int i = 0; i < mMoreKeys.length; i++) {
                upperCaseMoreKeys[i] = mMoreKeys[i].toUpperCase(locale);
            }
            return newInstance(getVisual().toUpperCase(locale), getOutput().toUpperCase(locale),
                    upperCaseMoreKeys);
        }

        @Override
        public ExpectedKey[] getMoreKeys() {
            return mMoreKeys;
        }

        @Override
        public boolean equalsTo(final Key key) {
            if (getVisual().equalsTo(key) && getOutput().equalsTo(key)) {
                final MoreKeySpec[] moreKeys = key.getMoreKeys();
                // This key should have at least one "more key".
                if (moreKeys == null || moreKeys.length != mMoreKeys.length) {
                    return false;
                }
                for (int index = 0; index < moreKeys.length; index++) {
                    if (!mMoreKeys[index].equalsTo(moreKeys[index])) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean equalsTo(final MoreKeySpec moreKeySpec) {
            // MoreKeySpec has no "more keys".
            return false;
        }

        @Override
        public String toString() {
            return super.toString() + "^" + Arrays.toString(mMoreKeys);
        }
    }
}
