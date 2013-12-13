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
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.utils.LocaleUtils;

import java.io.File;
import java.io.IOException;

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
        if (!BinaryDictionary.createEmptyDictFile(mDictPlacedDir.getAbsolutePath(),
                FormatSpec.VERSION4, dict.mOptions.mAttributes)) {
            throw new IOException("Cannot create dictionary file");
        }
        final BinaryDictionary binaryDict = new BinaryDictionary(mDictPlacedDir.getAbsolutePath(),
                0l, mDictPlacedDir.length(), true /* useFullEditDistance */,
                LocaleUtils.constructLocaleFromString(dict.mOptions.mAttributes.get(
                        FormatSpec.FileHeader.DICTIONARY_LOCALE_ATTRIBUTE)),
                Dictionary.TYPE_USER /* Dictionary type. Does not matter for us */,
                true /* isUpdatable */);
        if (!binaryDict.isValidDictionary()) {
            // Somehow createEmptyDictFile returned true, but the file was not created correctly
            throw new IOException("Cannot create dictionary file");
        }
        for (final Word word : dict) {
            // TODO: switch to addMultipleDictionaryEntries when they support shortcuts
            if (null == word.mShortcutTargets || word.mShortcutTargets.isEmpty()) {
                binaryDict.addUnigramWord(word.mWord, word.mFrequency,
                        null /* shortcutTarget */, 0 /* shortcutProbability */,
                        word.mIsNotAWord, word.mIsBlacklistEntry, 0 /* timestamp */);
            } else {
                for (final WeightedString shortcutTarget : word.mShortcutTargets) {
                    binaryDict.addUnigramWord(word.mWord, word.mFrequency,
                            shortcutTarget.mWord, shortcutTarget.mFrequency,
                            word.mIsNotAWord, word.mIsBlacklistEntry, 0 /* timestamp */);
                }
            }
            if (binaryDict.needsToRunGC(true /* mindsBlockByGC */)) {
                binaryDict.flushWithGC();
            }
        }
        for (final Word word0 : dict) {
            if (null == word0.mBigrams) continue;
            for (final WeightedString word1 : word0.mBigrams) {
                binaryDict.addBigramWords(word0.mWord, word1.mWord, word1.mFrequency,
                        0 /* timestamp */);
            }
            if (binaryDict.needsToRunGC(true /* mindsBlockByGC */)) {
                binaryDict.flushWithGC();
            }
        }
        binaryDict.flushWithGC();
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
    public void writeForwardLinkAddress(int forwardLinkAddress) {
    }

    @Override
    public void writePtNode(
            PtNode ptNode, int parentPosition, FormatOptions formatOptions, FusionDictionary dict) {
    }
}
