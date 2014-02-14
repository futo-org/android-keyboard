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

package com.android.inputmethod.latin.makedict;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

/**
 * An implementation of binary dictionary decoder for version 4 binary dictionary.
 */
@UsedForTesting
public class Ver4DictDecoder extends AbstractDictDecoder {
    private static final String TAG = Ver4DictDecoder.class.getSimpleName();

    final File mDictDirectory;
    final BinaryDictionary mBinaryDictionary;

    @UsedForTesting
    /* package */ Ver4DictDecoder(final File dictDirectory, final int factoryFlag) {
        this(dictDirectory, null /* factory */);
    }

    @UsedForTesting
    /* package */ Ver4DictDecoder(final File dictDirectory, final DictionaryBufferFactory factory) {
        mDictDirectory = dictDirectory;
        mBinaryDictionary = new BinaryDictionary(dictDirectory.getAbsolutePath(),
                0 /* offset */, 0 /* length */, true /* useFullEditDistance */, null /* locale */,
                "" /* dictType */, true /* isUpdatable */);
    }

    @Override
    public DictionaryHeader readHeader() throws IOException, UnsupportedFormatException {
        return mBinaryDictionary.getHeader();
    }

    @Override
    public FusionDictionary readDictionaryBinary(final boolean deleteDictIfBroken)
            throws FileNotFoundException, IOException, UnsupportedFormatException {
        final DictionaryHeader header = readHeader();
        final FusionDictionary fusionDict =
                new FusionDictionary(new FusionDictionary.PtNodeArray(), header.mDictionaryOptions);
        int token = 0;
        final ArrayList<WordProperty> wordProperties = CollectionUtils.newArrayList();
        do {
            final BinaryDictionary.GetNextWordPropertyResult result =
                    mBinaryDictionary.getNextWordProperty(token);
            final WordProperty wordProperty = result.mWordProperty;
            if (wordProperty == null) {
                if (deleteDictIfBroken) {
                    mBinaryDictionary.close();
                    FileUtils.deleteRecursively(mDictDirectory);
                }
                return null;
            }
            wordProperties.add(wordProperty);
            token = result.mNextToken;
        } while (token != 0);

        // Insert unigrams to the fusion dictionary.
        for (final WordProperty wordProperty : wordProperties) {
            if (wordProperty.mIsBlacklistEntry) {
                fusionDict.addBlacklistEntry(wordProperty.mWord, wordProperty.mShortcutTargets,
                        wordProperty.mIsNotAWord);
            } else {
                fusionDict.add(wordProperty.mWord, wordProperty.mProbabilityInfo,
                        wordProperty.mShortcutTargets, wordProperty.mIsNotAWord);
            }
        }
        // Insert bigrams to the fusion dictionary.
        for (final WordProperty wordProperty : wordProperties) {
            if (wordProperty.mBigrams == null) {
                continue;
            }
            final String word0 = wordProperty.mWord;
            for (final WeightedString bigram : wordProperty.mBigrams) {
                fusionDict.setBigram(word0, bigram.mWord, bigram.mProbabilityInfo);
            }
        }
        return fusionDict;
    }
}
