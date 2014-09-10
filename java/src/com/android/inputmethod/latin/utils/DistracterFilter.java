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

package com.android.inputmethod.latin.utils;

import java.util.List;
import java.util.Locale;

import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.PrevWordsInfo;

public interface DistracterFilter {
    /**
     * Determine whether a word is a distracter to words in dictionaries.
     *
     * @param prevWordsInfo the information of previous words.
     * @param testedWord the word that will be tested to see whether it is a distracter to words
     *                   in dictionaries.
     * @param locale the locale of word.
     * @return true if testedWord is a distracter, otherwise false.
     */
    public boolean isDistracterToWordsInDictionaries(final PrevWordsInfo prevWordsInfo,
            final String testedWord, final Locale locale);

    public int getWordHandlingType(final PrevWordsInfo prevWordsInfo, final String testedWord,
            final Locale locale);

    public void updateEnabledSubtypes(final List<InputMethodSubtype> enabledSubtypes);

    public void close();

    public static final class HandlingType {
        private final static int REQUIRE_NO_SPECIAL_HANDLINGS = 0x0;
        private final static int SHOULD_BE_LOWER_CASED = 0x1;
        private final static int SHOULD_BE_HANDLED_AS_OOV = 0x2;

        public static int getHandlingType(final boolean shouldBeLowerCased, final boolean isOov) {
            int wordHandlingType = HandlingType.REQUIRE_NO_SPECIAL_HANDLINGS;
            if (shouldBeLowerCased) {
                wordHandlingType |= HandlingType.SHOULD_BE_LOWER_CASED;
            }
            if (isOov) {
                wordHandlingType |= HandlingType.SHOULD_BE_HANDLED_AS_OOV;
            }
            return wordHandlingType;
        }

        public static boolean shouldBeLowerCased(final int handlingType) {
            return (handlingType & SHOULD_BE_LOWER_CASED) != 0;
        }

        public static boolean shouldBeHandledAsOov(final int handlingType) {
            return (handlingType & SHOULD_BE_HANDLED_AS_OOV) != 0;
        }
    };

    public static final DistracterFilter EMPTY_DISTRACTER_FILTER = new DistracterFilter() {
        @Override
        public boolean isDistracterToWordsInDictionaries(PrevWordsInfo prevWordsInfo,
                String testedWord, Locale locale) {
            return false;
        }

        @Override
        public int getWordHandlingType(final PrevWordsInfo prevWordsInfo,
                final String testedWord, final Locale locale) {
            return HandlingType.REQUIRE_NO_SPECIAL_HANDLINGS;
        }

        @Override
        public void close() {
        }

        @Override
        public void updateEnabledSubtypes(List<InputMethodSubtype> enabledSubtypes) {
        }
    };
}
