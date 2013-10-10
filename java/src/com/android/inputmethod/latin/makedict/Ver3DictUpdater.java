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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * An implementation of DictUpdater for version 3 binary dictionary.
 */
@UsedForTesting
public class Ver3DictUpdater extends Ver3DictDecoder implements DictUpdater {
    private OutputStream mOutStream;

    @UsedForTesting
    public Ver3DictUpdater(final File dictFile, final int factoryType) {
        // DictUpdater must have an updatable DictBuffer.
        super(dictFile, ((factoryType & MASK_DICTBUFFER) == USE_BYTEARRAY)
                ? USE_BYTEARRAY : USE_WRITABLE_BYTEBUFFER);
        mOutStream = null;
    }

    private void openStreamAndBuffer() throws FileNotFoundException, IOException {
        super.openDictBuffer();
        mOutStream = new FileOutputStream(mDictionaryBinaryFile, true /* append */);
    }

    private void close() throws IOException {
        if (mOutStream != null) {
            mOutStream.close();
            mOutStream = null;
        }
    }

    @Override @UsedForTesting
    public void deleteWord(final String word) throws IOException, UnsupportedFormatException {
        if (mOutStream == null) openStreamAndBuffer();
        mDictBuffer.position(0);
        super.readHeader();
        final int wordPos = getTerminalPosition(word);
        if (wordPos != FormatSpec.NOT_VALID_WORD) {
            mDictBuffer.position(wordPos);
            final int flags = mDictBuffer.readUnsignedByte();
            mDictBuffer.position(wordPos);
            mDictBuffer.put((byte) DynamicBinaryDictIOUtils.markAsDeleted(flags));
        }
        close();
    }

    @Override @UsedForTesting
    public void insertWord(final String word, final int frequency,
            final ArrayList<WeightedString> bigramStrings,
            final ArrayList<WeightedString> shortcuts,
            final boolean isNotAWord, final boolean isBlackListEntry)
                    throws IOException, UnsupportedFormatException {
        if (mOutStream == null) openStreamAndBuffer();
        DynamicBinaryDictIOUtils.insertWord(this, mOutStream, word, frequency, bigramStrings,
                shortcuts, isNotAWord, isBlackListEntry);
        close();
    }
}
