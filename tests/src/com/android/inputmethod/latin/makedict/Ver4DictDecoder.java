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
import com.android.inputmethod.latin.common.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

/**
 * An implementation of binary dictionary decoder for version 4 binary dictionary.
 */
@UsedForTesting
public class Ver4DictDecoder extends AbstractDictDecoder {
    final File mDictDirectory;

    @UsedForTesting
    /* package */ Ver4DictDecoder(final File dictDirectory) {
        mDictDirectory = dictDirectory;

    }

    @Override
    public DictionaryHeader readHeader() throws IOException, UnsupportedFormatException {
        // dictType is not being used in dicttool. Passing an empty string.
        final BinaryDictionary binaryDictionary= new BinaryDictionary(
              mDictDirectory.getAbsolutePath(), 0 /* offset */, 0 /* length */,
              true /* useFullEditDistance */, null /* locale */,
              "" /* dictType */, true /* isUpdatable */);
        final DictionaryHeader header = binaryDictionary.getHeader();
        binaryDictionary.close();
        if (header == null) {
            throw new IOException("Cannot read the dictionary header.");
        }
        return header;
    }

    @Override
    public FusionDictionary readDictionaryBinary(final boolean deleteDictIfBroken)
            throws FileNotFoundException, IOException, UnsupportedFormatException {
        // dictType is not being used in dicttool. Passing an empty string.
        final BinaryDictionary binaryDictionary = new BinaryDictionary(
              mDictDirectory.getAbsolutePath(), 0 /* offset */, 0 /* length */,
              true /* useFullEditDistance */, null /* locale */,
              "" /* dictType */, true /* isUpdatable */);
        final DictionaryHeader header = readHeader();
        final FusionDictionary fusionDict =
                new FusionDictionary(new FusionDictionary.PtNodeArray(), header.mDictionaryOptions);
        int token = 0;
        final ArrayList<WordProperty> wordProperties = new ArrayList<>();
        do {
            final BinaryDictionary.GetNextWordPropertyResult result =
                    binaryDictionary.getNextWordProperty(token);
            final WordProperty wordProperty = result.mWordProperty;
            if (wordProperty == null) {
                binaryDictionary.close();
                if (deleteDictIfBroken) {
                    FileUtils.deleteRecursively(mDictDirectory);
                }
                return null;
            }
            wordProperties.add(wordProperty);
            token = result.mNextToken;
        } while (token != 0);

        // Insert unigrams into the fusion dictionary.
        for (final WordProperty wordProperty : wordProperties) {
            fusionDict.add(wordProperty.mWord, wordProperty.mProbabilityInfo,
                    wordProperty.mIsNotAWord,
                    wordProperty.mIsPossiblyOffensive);
        }
        // Insert bigrams into the fusion dictionary.
        // TODO: Support ngrams.
        for (final WordProperty wordProperty : wordProperties) {
            if (!wordProperty.mHasNgrams) {
                continue;
            }
            final String word0 = wordProperty.mWord;
            for (final WeightedString bigram : wordProperty.getBigrams()) {
                fusionDict.setBigram(word0, bigram.mWord, bigram.mProbabilityInfo);
            }
        }
        binaryDictionary.close();
        return fusionDict;
    }
}
