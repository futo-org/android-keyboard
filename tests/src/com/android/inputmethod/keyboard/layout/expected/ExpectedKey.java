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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * This class represents an expected key.
 */
public class ExpectedKey {
    static ExpectedKey EMPTY_KEY = newInstance("");

    // A key that has a string label and may have "more keys".
    static ExpectedKey newInstance(final String label, final ExpectedKey... moreKeys) {
        return newInstance(label, label, moreKeys);
    }

    // A key that has a string label and a different output text and may have "more keys".
    static ExpectedKey newInstance(final String label, final String outputText,
            final ExpectedKey... moreKeys) {
        return newInstance(ExpectedKeyVisual.newInstance(label),
                ExpectedKeyOutput.newInstance(outputText), moreKeys);
    }

    // A key that has a string label and a code point output and may have "more keys".
    static ExpectedKey newInstance(final String label, final int code,
            final ExpectedKey... moreKeys) {
        return newInstance(ExpectedKeyVisual.newInstance(label),
                ExpectedKeyOutput.newInstance(code), moreKeys);
    }

    // A key that has an icon and an output text and may have "more keys".
    static ExpectedKey newInstance(final int iconId, final String outputText,
            final ExpectedKey... moreKeys) {
        return newInstance(ExpectedKeyVisual.newInstance(iconId),
                ExpectedKeyOutput.newInstance(outputText), moreKeys);
    }

    // A key that has an icon and a code point output and may have "more keys".
    static ExpectedKey newInstance(final int iconId, final int code,
            final ExpectedKey... moreKeys) {
        return newInstance(ExpectedKeyVisual.newInstance(iconId),
                ExpectedKeyOutput.newInstance(code), moreKeys);
    }

    static ExpectedKey newInstance(final ExpectedKeyVisual visual, final ExpectedKeyOutput output,
            final ExpectedKey... moreKeys) {
        if (moreKeys.length == 0) {
            return new ExpectedKey(visual, output);
        }
        // The more keys are the extra keys that the main keyboard key may have in its long press
        // popup keyboard.
        // The additional more keys can be defined independently from other more keys.
        // The position of the additional more keys in the long press popup keyboard can be
        // controlled by specifying special marker "%" in the usual more keys definitions.
        final ArrayList<ExpectedKey> moreKeysList = new ArrayList<>();
        final ArrayList<ExpectedAdditionalMoreKey> additionalMoreKeys = new ArrayList<>();
        int firstAdditionalMoreKeyIndex = -1;
        for (int index = 0; index < moreKeys.length; index++) {
            final ExpectedKey moreKey = moreKeys[index];
            if (moreKey instanceof ExpectedAdditionalMoreKey) {
                additionalMoreKeys.add((ExpectedAdditionalMoreKey) moreKey);
                if (firstAdditionalMoreKeyIndex < 0) {
                    firstAdditionalMoreKeyIndex = index;
                }
            } else {
                moreKeysList.add(moreKey);
            }
        }
        if (additionalMoreKeys.isEmpty()) {
            return new ExpectedKeyWithMoreKeys(visual, output, moreKeys);
        }
        final ExpectedKey[] moreKeysArray = moreKeysList.toArray(
                new ExpectedKey[moreKeysList.size()]);
        final ExpectedAdditionalMoreKey[] additionalMoreKeysArray = additionalMoreKeys.toArray(
                new ExpectedAdditionalMoreKey[additionalMoreKeys.size()]);
        return new ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(
                visual, output, moreKeysArray, firstAdditionalMoreKeyIndex,
                additionalMoreKeysArray);
    }

    private static final ExpectedKey[] EMPTY_KEYS = new ExpectedKey[0];

    // The expected visual outlook of this key.
    private final ExpectedKeyVisual mVisual;
    // The expected output of this key.
    private final ExpectedKeyOutput mOutput;

    protected final ExpectedKeyVisual getVisual() {
        return mVisual;
    }

    protected final ExpectedKeyOutput getOutput() {
        return mOutput;
    }

    public ExpectedKey[] getMoreKeys() {
        // This key has no "more keys".
        return EMPTY_KEYS;
    }

    public ExpectedKey setMoreKeys(final ExpectedKey... moreKeys) {
        return newInstance(mVisual, mOutput, moreKeys);
    }

    public ExpectedKey setAdditionalMoreKeys(
            final ExpectedAdditionalMoreKey... additionalMoreKeys) {
        if (additionalMoreKeys.length == 0) {
            return this;
        }
        return new ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(
                mVisual, mOutput, EMPTY_KEYS, 0 /* additionalMoreKeysIndex */, additionalMoreKeys);
    }

    public ExpectedKey setAdditionalMoreKeysIndex(final int additionalMoreKeysIndex) {
        if (additionalMoreKeysIndex == 0) {
            return this;
        }
        return new ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(
                mVisual, mOutput, EMPTY_KEYS, additionalMoreKeysIndex);
    }

    protected ExpectedKey(final ExpectedKeyVisual visual, final ExpectedKeyOutput output) {
        mVisual = visual;
        mOutput = output;
    }

    public ExpectedKey toUpperCase(Locale locale) {
        return newInstance(mVisual.toUpperCase(locale), mOutput.toUpperCase(locale));
    }

    public ExpectedKey preserveCase() {
        final ExpectedKey[] moreKeys = getMoreKeys();
        final ExpectedKey[] casePreservedMoreKeys = new ExpectedKey[moreKeys.length];
        for (int index = 0; index < moreKeys.length; index++) {
            final ExpectedKey moreKey = moreKeys[index];
            casePreservedMoreKeys[index] = newInstance(
                    moreKey.getVisual().preserveCase(), moreKey.getOutput().preserveCase());
        }
        return newInstance(
                getVisual().preserveCase(), getOutput().preserveCase(), casePreservedMoreKeys);
    }

    public boolean equalsTo(final Key key) {
        // This key has no "more keys".
        return mVisual.hasSameKeyVisual(key) && mOutput.hasSameKeyOutput(key)
                && key.getMoreKeys() == null;
    }

    public boolean equalsTo(final MoreKeySpec moreKeySpec) {
        return mVisual.hasSameKeyVisual(moreKeySpec) && mOutput.hasSameKeyOutput(moreKeySpec);
    }

    @Override
    public boolean equals(final Object object) {
        if (object instanceof ExpectedKey) {
            final ExpectedKey key = (ExpectedKey) object;
            return mVisual.hasSameKeyVisual(key.mVisual) && mOutput.hasSameKeyOutput(key.mOutput)
                    && Arrays.equals(getMoreKeys(), key.getMoreKeys());
        }
        return false;
    }

    private static int hashCode(final Object... objects) {
        return Arrays.hashCode(objects);
    }

    @Override
    public int hashCode() {
        return hashCode(mVisual, mOutput, getMoreKeys());
    }

    @Override
    public String toString() {
        if (mVisual.hasSameKeyVisual(mOutput)) {
            return mVisual.toString();
        }
        return mVisual + "|" + mOutput;
    }

    /**
     * This class represents an expected "additional more key".
     *
     * The additional more keys can be defined independently from other more keys. The position of
     * the additional more keys in the long press popup keyboard can be controlled by specifying
     * special marker "%" in the usual more keys definitions.
     */
    public static class ExpectedAdditionalMoreKey extends ExpectedKey {
        public static ExpectedAdditionalMoreKey newInstance(final String label) {
            return new ExpectedAdditionalMoreKey(ExpectedKeyVisual.newInstance(label),
                    ExpectedKeyOutput.newInstance(label));
        }

        public static ExpectedAdditionalMoreKey newInstance(final ExpectedKey key) {
            return new ExpectedAdditionalMoreKey(key.getVisual(), key.getOutput());
        }

        ExpectedAdditionalMoreKey(final ExpectedKeyVisual visual, final ExpectedKeyOutput output) {
            super(visual, output);
        }

        @Override
        public ExpectedAdditionalMoreKey toUpperCase(final Locale locale) {
            final ExpectedKey upperCaseKey = super.toUpperCase(locale);
            return new ExpectedAdditionalMoreKey(
                    upperCaseKey.getVisual(), upperCaseKey.getOutput());
        }
    }

    /**
     * This class represents an expected key that has "more keys".
     */
    private static class ExpectedKeyWithMoreKeys extends ExpectedKey {
        private final ExpectedKey[] mMoreKeys;

        ExpectedKeyWithMoreKeys(final ExpectedKeyVisual visual, final ExpectedKeyOutput output,
                final ExpectedKey... moreKeys) {
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
        public ExpectedKey setAdditionalMoreKeys(
                final ExpectedAdditionalMoreKey... additionalMoreKeys) {
            if (additionalMoreKeys.length == 0) {
                return this;
            }
            return new ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(
                    getVisual(), getOutput(), mMoreKeys, 0 /* additionalMoreKeysIndex */,
                    additionalMoreKeys);
        }

        @Override
        public ExpectedKey setAdditionalMoreKeysIndex(final int additionalMoreKeysIndex) {
            if (additionalMoreKeysIndex == 0) {
                return this;
            }
            return new ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(
                    getVisual(), getOutput(), mMoreKeys, additionalMoreKeysIndex);
        }

        @Override
        public boolean equalsTo(final Key key) {
            if (getVisual().hasSameKeyVisual(key) && getOutput().hasSameKeyOutput(key)) {
                final MoreKeySpec[] moreKeySpecs = key.getMoreKeys();
                final ExpectedKey[] moreKeys = getMoreKeys();
                // This key should have at least one "more key".
                if (moreKeySpecs == null || moreKeySpecs.length != moreKeys.length) {
                    return false;
                }
                for (int index = 0; index < moreKeySpecs.length; index++) {
                    if (!moreKeys[index].equalsTo(moreKeySpecs[index])) {
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
            return super.toString() + "^" + Arrays.toString(getMoreKeys());
        }
    }

    /**
     * This class represents an expected key that has "more keys" and "additional more keys".
     */
    private static final class ExpectedKeyWithMoreKeysAndAdditionalMoreKeys
            extends ExpectedKeyWithMoreKeys {
        private final ExpectedAdditionalMoreKey[] mAdditionalMoreKeys;
        private final int mAdditionalMoreKeysIndex;

        ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(final ExpectedKeyVisual visual,
                final ExpectedKeyOutput output, final ExpectedKey[] moreKeys,
                final int additionalMoreKeysIndex,
                final ExpectedAdditionalMoreKey... additionalMoreKeys) {
            super(visual, output, moreKeys);
            mAdditionalMoreKeysIndex = additionalMoreKeysIndex;
            mAdditionalMoreKeys = additionalMoreKeys;
        }

        @Override
        public ExpectedKey setMoreKeys(final ExpectedKey... moreKeys) {
            return new ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(
                    getVisual(), getOutput(), moreKeys, mAdditionalMoreKeysIndex,
                    mAdditionalMoreKeys);
        }

        @Override
        public ExpectedKey setAdditionalMoreKeys(
                final ExpectedAdditionalMoreKey... additionalMoreKeys) {
            return new ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(
                    getVisual(), getOutput(), super.getMoreKeys(), mAdditionalMoreKeysIndex,
                    additionalMoreKeys);
        }

        @Override
        public ExpectedKey setAdditionalMoreKeysIndex(final int additionalMoreKeysIndex) {
            return new ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(
                    getVisual(), getOutput(), super.getMoreKeys(), additionalMoreKeysIndex,
                    mAdditionalMoreKeys);
        }

        @Override
        public ExpectedKey toUpperCase(final Locale locale) {
            final ExpectedKey[] moreKeys = super.getMoreKeys();
            final ExpectedKey[] upperCaseMoreKeys = new ExpectedKey[moreKeys.length];
            for (int i = 0; i < moreKeys.length; i++) {
                upperCaseMoreKeys[i] = moreKeys[i].toUpperCase(locale);
            }
            final ExpectedAdditionalMoreKey[] upperCaseAdditionalMoreKeys =
                    new ExpectedAdditionalMoreKey[mAdditionalMoreKeys.length];
            for (int i = 0; i < mAdditionalMoreKeys.length; i++) {
                upperCaseAdditionalMoreKeys[i] = mAdditionalMoreKeys[i].toUpperCase(locale);
            }
            return new ExpectedKeyWithMoreKeysAndAdditionalMoreKeys(
                    getVisual().toUpperCase(locale), getOutput().toUpperCase(locale),
                    upperCaseMoreKeys, mAdditionalMoreKeysIndex, upperCaseAdditionalMoreKeys);
        }

        @Override
        public ExpectedKey[] getMoreKeys() {
            final ExpectedKey[] moreKeys = super.getMoreKeys();
            final ExpectedKey[] edittedMoreKeys = Arrays.copyOf(
                    moreKeys, moreKeys.length + mAdditionalMoreKeys.length);
            System.arraycopy(edittedMoreKeys, mAdditionalMoreKeysIndex,
                    edittedMoreKeys, mAdditionalMoreKeysIndex + mAdditionalMoreKeys.length,
                    moreKeys.length - mAdditionalMoreKeysIndex);
            System.arraycopy(mAdditionalMoreKeys, 0, edittedMoreKeys, mAdditionalMoreKeysIndex,
                    mAdditionalMoreKeys.length);
            return edittedMoreKeys;
        }
    }
}
