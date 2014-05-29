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

package com.android.inputmethod.latin.personalization;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.ExpandableBinaryDictionary;
import com.android.inputmethod.latin.ExpandableBinaryDictionary.AddMultipleDictionaryEntriesCallback;
import com.android.inputmethod.latin.makedict.CodePointUtils;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

/**
 * Unit tests for personalization dictionary
 */
@LargeTest
public class PersonalizationDictionaryTests extends AndroidTestCase {
    private static final String TAG = PersonalizationDictionaryTests.class.getSimpleName();

    private static final Locale LOCALE_EN_US = new Locale("en", "US");
    private static final String DUMMY_PACKAGE_NAME = "test.package.name";
    private static final long TIMEOUT_TO_WAIT_DICTIONARY_OPERATIONS_IN_SECONDS = 120;

    private DictionaryFacilitator getDictionaryFacilitator() {
        final ArrayList<String> dictTypes = new ArrayList<>();
        dictTypes.add(Dictionary.TYPE_MAIN);
        dictTypes.add(Dictionary.TYPE_PERSONALIZATION);
        final DictionaryFacilitator dictionaryFacilitator = new DictionaryFacilitator();
        dictionaryFacilitator.resetDictionariesForTesting(getContext(), LOCALE_EN_US, dictTypes,
                new HashMap<String, File>(), new HashMap<String, Map<String, String>>());
        return dictionaryFacilitator;
    }

    public void testAddManyTokens() {
        final DictionaryFacilitator dictionaryFacilitator = getDictionaryFacilitator();
        dictionaryFacilitator.clearPersonalizationDictionary();
        final int dataChunkCount = 20;
        final int wordCountInOneChunk = 2000;
        final Random random = new Random(System.currentTimeMillis());
        final int[] codePointSet = CodePointUtils.LATIN_ALPHABETS_LOWER;

        final SpacingAndPunctuations spacingAndPunctuations =
                new SpacingAndPunctuations(getContext().getResources());

        final int timeStampInSeconds = (int)TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis());

        for (int i = 0; i < dataChunkCount; i++) {
            final ArrayList<String> tokens = new ArrayList<>();
            for (int j = 0; j < wordCountInOneChunk; j++) {
                tokens.add(CodePointUtils.generateWord(random, codePointSet));
            }
            final PersonalizationDataChunk personalizationDataChunk = new PersonalizationDataChunk(
                    true /* inputByUser */, tokens, timeStampInSeconds, DUMMY_PACKAGE_NAME);
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final AddMultipleDictionaryEntriesCallback callback =
                    new AddMultipleDictionaryEntriesCallback() {
                        @Override
                        public void onFinished() {
                            countDownLatch.countDown();
                        }
                    };
            dictionaryFacilitator.addEntriesToPersonalizationDictionary(personalizationDataChunk,
                    spacingAndPunctuations, callback);
            try {
                countDownLatch.await(TIMEOUT_TO_WAIT_DICTIONARY_OPERATIONS_IN_SECONDS,
                        TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for finishing dictionary operations.", e);
            }
        }
        dictionaryFacilitator.flushPersonalizationDictionary();
        try {
            dictionaryFacilitator.waitForLoadingDictionariesForTesting(
                    TIMEOUT_TO_WAIT_DICTIONARY_OPERATIONS_IN_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for finishing dictionary operations.", e);
        }
        final String dictName = ExpandableBinaryDictionary.getDictName(
                PersonalizationDictionary.NAME, LOCALE_EN_US, null /* dictFile */);
        final File dictFile = ExpandableBinaryDictionary.getDictFile(
                getContext(), dictName, null /* dictFile */);

        final BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile.getAbsolutePath(),
                0 /* offset */, 0 /* size */,
                true /* useFullEditDistance */, LOCALE_EN_US, Dictionary.TYPE_PERSONALIZATION,
                true /* isUpdatable */);
        assertTrue(binaryDictionary.isValidDictionary());
    }
}
