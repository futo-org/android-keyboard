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
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.NgramContext;
import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * An implementation of DictEncoder for version 4 binary dictionary.
 */
@UsedForTesting
public class Ver4DictEncoder implements DictEncoder {
    private final File mDictPlacedDir;

    @UsedForTesting
    public Ver4DictEncoder(final File dictPlacedDir) {
        mDictPlacedDir = dictPlacedDir;
    }

    // TODO: This builds a FusionDictionary first and iterates it to add words to the binary
    // dictionary. However, it is possible to just add words directly to the binary dictionary
    // instead.
    // In the long run, when we stop supporting version 2, FusionDictionary will become deprecated
    // and we can remove it. Then we'll be able to just call BinaryDictionary directly.
    @Override
    public void writeDictionary(FusionDictionary dict, FormatOptions formatOptions)
            throws IOException, UnsupportedFormatException {
        if (formatOptions.mVersion != FormatSpec.VERSION4) {
            throw new UnsupportedFormatException("File header has a wrong version number : "
                    + formatOptions.mVersion);
        }
        if (!mDictPlacedDir.isDirectory()) {
            throw new UnsupportedFormatException("Given path is not a directory.");
        }
        if (!BinaryDictionaryUtils.createEmptyDictFile(mDictPlacedDir.getAbsolutePath(),
                FormatSpec.VERSION4, LocaleUtils.constructLocaleFromString(
                dict.mOptions.mAttributes.get(DictionaryHeader.DICTIONARY_LOCALE_KEY)),
                dict.mOptions.mAttributes)) {
            throw new IOException("Cannot create dictionary file : "
                + mDictPlacedDir.getAbsolutePath());
        }
        final BinaryDictionary binaryDict = new BinaryDictionary(mDictPlacedDir.getAbsolutePath(),
                0l, mDictPlacedDir.length(), true /* useFullEditDistance */,
                LocaleUtils.constructLocaleFromString(dict.mOptions.mAttributes.get(
                        DictionaryHeader.DICTIONARY_LOCALE_KEY)),
                Dictionary.TYPE_USER /* Dictionary type. Does not matter for us */,
                true /* isUpdatable */);
        if (!binaryDict.isValidDictionary()) {
            // Somehow createEmptyDictFile returned true, but the file was not created correctly
            throw new IOException("Cannot create dictionary file");
        }
        for (final WordProperty wordProperty : dict) {
            if (!binaryDict.addUnigramEntry(wordProperty.mWord, wordProperty.getProbability(),
                    wordProperty.mIsBeginningOfSentence, wordProperty.mIsNotAWord,
                    wordProperty.mIsPossiblyOffensive, 0 /* timestamp */)) {
                MakedictLog.e("Cannot add unigram entry for " + wordProperty.mWord);
            }
            if (binaryDict.needsToRunGC(true /* mindsBlockByGC */)) {
                if (!binaryDict.flushWithGC()) {
                    MakedictLog.e("Cannot flush dict with GC.");
                    return;
                }
            }
        }
        for (final WordProperty word0Property : dict) {
            if (!word0Property.mHasNgrams) continue;
            // TODO: Support ngram.
            for (final WeightedString word1 : word0Property.getBigrams()) {
                final NgramContext ngramContext =
                        new NgramContext(new NgramContext.WordInfo(word0Property.mWord));
                if (!binaryDict.addNgramEntry(ngramContext, word1.mWord,
                        word1.getProbability(), 0 /* timestamp */)) {
                    MakedictLog.e("Cannot add n-gram entry for "
                            + ngramContext + " -> " + word1.mWord);
                    return;
                }
                if (binaryDict.needsToRunGC(true /* mindsBlockByGC */)) {
                    if (!binaryDict.flushWithGC()) {
                        MakedictLog.e("Cannot flush dict with GC.");
                        return;
                    }
                }
            }
        }
        if (!binaryDict.flushWithGC()) {
            MakedictLog.e("Cannot flush dict with GC.");
            return;
        }
        binaryDict.close();
    }

    @Override
    public void setPosition(int position) {
    }

    @Override
    public int getPosition() {
        return 0;
    }

    @Override
    public void writePtNodeCount(int ptNodeCount) {
    }

    @Override
    public void writePtNode(PtNode ptNode, FusionDictionary dict,
            HashMap<Integer, Integer> codePointToOneByteCodeMap) {
    }
}
