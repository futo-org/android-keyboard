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

import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;
import com.android.inputmethod.latin.makedict.DictDecoder.DictionaryBufferFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * An auxiliary class for reading SparseTable and data written by SparseTableContentWriter.
 */
public class SparseTableContentReader {

    /**
     * An interface of a function which is passed to SparseTableContentReader.read.
     */
    public interface SparseTableContentReaderInterface {
        /**
         * Reads data.
         *
         * @param buffer the DictBuffer. The position of the buffer is set to the head of data.
         */
        public void read(final DictBuffer buffer);
    }

    protected final int mContentCount;
    protected final int mBlockSize;
    protected final File mBaseDir;
    protected final File mLookupTableFile;
    protected final File[] mAddressTableFiles;
    protected final File[] mContentFiles;
    protected DictBuffer mLookupTableBuffer;
    protected final DictBuffer[] mAddressTableBuffers;
    private final DictBuffer[] mContentBuffers;
    protected final DictionaryBufferFactory mFactory;

    /**
     * Sole constructor of SparseTableContentReader.
     *
     * @param name the name of SparseTable.
     * @param blockSize the block size of the content table.
     * @param baseDir the directory which contains the files of the content table.
     * @param contentFilenames the file names of content files.
     * @param contentSuffixes the ids of contents. These ids are used for a suffix of a name of
     * address files and content files.
     * @param factory the DictionaryBufferFactory which is used for opening the files.
     */
    public SparseTableContentReader(final String name, final int blockSize, final File baseDir,
            final String[] contentFilenames, final String[] contentSuffixes,
            final DictionaryBufferFactory factory) {
        if (contentFilenames.length != contentSuffixes.length) {
            throw new RuntimeException("The length of contentFilenames and the length of"
                    + " contentSuffixes are different " + contentFilenames.length + ", "
                    + contentSuffixes.length);
        }
        mBlockSize = blockSize;
        mBaseDir = baseDir;
        mFactory = factory;
        mContentCount = contentFilenames.length;
        mLookupTableFile = new File(baseDir, name + FormatSpec.LOOKUP_TABLE_FILE_SUFFIX);
        mAddressTableFiles = new File[mContentCount];
        mContentFiles = new File[mContentCount];
        for (int i = 0; i < mContentCount; ++i) {
            mAddressTableFiles[i] = new File(mBaseDir,
                    name + FormatSpec.CONTENT_TABLE_FILE_SUFFIX + contentSuffixes[i]);
            mContentFiles[i] = new File(mBaseDir, contentFilenames[i] + contentSuffixes[i]);
        }
        mAddressTableBuffers = new DictBuffer[mContentCount];
        mContentBuffers = new DictBuffer[mContentCount];
    }

    public void openBuffers() throws FileNotFoundException, IOException {
        mLookupTableBuffer = mFactory.getDictionaryBuffer(mLookupTableFile);
        for (int i = 0; i < mContentCount; ++i) {
            mAddressTableBuffers[i] = mFactory.getDictionaryBuffer(mAddressTableFiles[i]);
            mContentBuffers[i] = mFactory.getDictionaryBuffer(mContentFiles[i]);
        }
    }

    /**
     * Calls the read() callback of the reader with the appropriate buffer appropriately positioned.
     * @param contentNumber the index in the original contentFilenames[] array.
     * @param terminalId the terminal ID to read.
     * @param reader the reader on which to call the callback.
     */
    protected void read(final int contentNumber, final int terminalId,
            final SparseTableContentReaderInterface reader) {
        if (terminalId < 0 || (terminalId / mBlockSize) * SparseTable.SIZE_OF_INT_IN_BYTES
                >= mLookupTableBuffer.limit()) {
            return;
        }

        mLookupTableBuffer.position((terminalId / mBlockSize) * SparseTable.SIZE_OF_INT_IN_BYTES);
        final int indexInAddressTable = mLookupTableBuffer.readInt();
        if (indexInAddressTable == SparseTable.NOT_EXIST) {
            return;
        }

        mAddressTableBuffers[contentNumber].position(SparseTable.SIZE_OF_INT_IN_BYTES
                * ((indexInAddressTable * mBlockSize) + (terminalId % mBlockSize)));
        final int address = mAddressTableBuffers[contentNumber].readInt();
        if (address == SparseTable.NOT_EXIST) {
            return;
        }

        mContentBuffers[contentNumber].position(address);
        reader.read(mContentBuffers[contentNumber]);
    }
}