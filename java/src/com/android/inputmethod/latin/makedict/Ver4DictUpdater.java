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
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * An implementation of DictUpdater for version 4 binary dictionary.
 */
@UsedForTesting
public class Ver4DictUpdater extends Ver4DictDecoder implements DictUpdater {

    @UsedForTesting
    public Ver4DictUpdater(final File dictDirectory, final int factoryType) {
        // DictUpdater must have an updatable DictBuffer.
        super(dictDirectory, ((factoryType & MASK_DICTBUFFER) == USE_BYTEARRAY)
                ? USE_BYTEARRAY : USE_WRITABLE_BYTEBUFFER);
    }

    @Override
    public void deleteWord(final String word) throws IOException, UnsupportedFormatException {
        if (mDictBuffer == null) openDictBuffer();
        readHeader();
        final int wordPos = getTerminalPosition(word);
        if (wordPos != FormatSpec.NOT_VALID_WORD) {
            mDictBuffer.position(wordPos);
            final int flags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
            mDictBuffer.position(wordPos);
            mDictBuffer.put((byte) DynamicBinaryDictIOUtils.markAsDeleted(flags));
        }
    }

    @Override
    public void insertWord(final String word, final int frequency,
        final ArrayList<WeightedString> bigramStrings, final ArrayList<WeightedString> shortcuts,
        final boolean isNotAWord, final boolean isBlackListEntry)
                throws IOException, UnsupportedFormatException {
        // TODO: Implement this method.
    }
}
