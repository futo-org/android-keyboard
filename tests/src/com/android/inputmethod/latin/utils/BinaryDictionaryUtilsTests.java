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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.FormatSpec;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@LargeTest
public class BinaryDictionaryUtilsTests extends AndroidTestCase {
    private static final String TEST_DICT_FILE_EXTENSION = ".testDict";
    private static final String TEST_LOCALE = "test";

    private File createEmptyDictionaryAndGetFile(final String dictId,
            final int formatVersion) throws IOException {
        if (formatVersion == FormatSpec.VERSION4) {
            return createEmptyVer4DictionaryAndGetFile(dictId);
        } else {
            throw new IOException("Dictionary format version " + formatVersion
                    + " is not supported.");
        }
    }

    private File createEmptyVer4DictionaryAndGetFile(final String dictId) throws IOException {
        final File file = getDictFile(dictId);
        FileUtils.deleteRecursively(file);
        Map<String, String> attributeMap = new HashMap<>();
        attributeMap.put(DictionaryHeader.DICTIONARY_ID_KEY, dictId);
        attributeMap.put(DictionaryHeader.DICTIONARY_VERSION_KEY,
                String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
        attributeMap.put(DictionaryHeader.USES_FORGETTING_CURVE_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(DictionaryHeader.HAS_HISTORICAL_INFO_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        if (BinaryDictionaryUtils.createEmptyDictFile(file.getAbsolutePath(), FormatSpec.VERSION4,
                LocaleUtils.constructLocaleFromString(TEST_LOCALE), attributeMap)) {
            return file;
        } else {
            throw new IOException("Empty dictionary " + file.getAbsolutePath()
                    + " cannot be created.");
        }
    }

    private File getDictFile(final String dictId) {
        return new File(getContext().getCacheDir(), dictId + TEST_DICT_FILE_EXTENSION);
    }

    public void testRenameDictionary() {
        final int formatVersion = FormatSpec.VERSION4;
        File dictFile0 = null;
        try {
            dictFile0 = createEmptyDictionaryAndGetFile("MoveFromDictionary", formatVersion);
        } catch (IOException e) {
            fail("IOException while writing an initial dictionary : " + e);
        }
        final File dictFile1 = getDictFile("MoveToDictionary");
        FileUtils.deleteRecursively(dictFile1);
        assertTrue(BinaryDictionaryUtils.renameDict(dictFile0, dictFile1));
        assertFalse(dictFile0.exists());
        assertTrue(dictFile1.exists());
        BinaryDictionary binaryDictionary = new BinaryDictionary(dictFile1.getAbsolutePath(),
                0 /* offset */, dictFile1.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertTrue(binaryDictionary.isValidDictionary());
        assertTrue(binaryDictionary.getFormatVersion() == formatVersion);
        binaryDictionary.close();
    }
}
