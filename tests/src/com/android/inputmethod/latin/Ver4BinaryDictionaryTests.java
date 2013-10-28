/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.Ver4DictEncoder;
import com.android.inputmethod.latin.makedict.FusionDictionary.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

@LargeTest
public class Ver4BinaryDictionaryTests extends AndroidTestCase {
    private static final String TAG = Ver4BinaryDictionaryTests.class.getSimpleName();
    private static final String TEST_LOCALE = "test";
    private static final FormatSpec.FormatOptions FORMAT_OPTIONS =
            new FormatSpec.FormatOptions(4, true /* supportsDynamicUpdate */);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    // TODO: remove after native code support dictionary creation.
    private DictionaryOptions getDictionaryOptions(final String id, final String version) {
        final DictionaryOptions options = new DictionaryOptions(new HashMap<String, String>(),
                false /* germanUmlautProcessing */, false /* frenchLigatureProcessing */);
        options.mAttributes.put("version", version);
        options.mAttributes.put("dictionary", id);
        return options;
    }

    // TODO: remove after native code support dictionary creation.
    private File getTrieFile(final String id, final String version) {
        return new File(getContext().getCacheDir() + "/" + id + "." + version, 
                TEST_LOCALE + "." + version + FormatSpec.TRIE_FILE_EXTENSION);
    }

    public void testIsValidDictionary() {
        final String dictVersion = Long.toString(System.currentTimeMillis());
        final File trieFile = getTrieFile(TEST_LOCALE, dictVersion);

        BinaryDictionary binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertFalse(binaryDictionary.isValidDictionary());

        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(),
                getDictionaryOptions(TEST_LOCALE, dictVersion));
        DictEncoder encoder = new Ver4DictEncoder(getContext().getCacheDir());
        try {
            encoder.writeDictionary(dict, FORMAT_OPTIONS);
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing dictionary", e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format", e);
        }

        binaryDictionary = new BinaryDictionary(trieFile.getAbsolutePath(),
                0 /* offset */, trieFile.length(), true /* useFullEditDistance */,
                Locale.getDefault(), TEST_LOCALE, true /* isUpdatable */);
        assertTrue(binaryDictionary.isValidDictionary());
    }
}
