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
     * @param contentIds the ids of contents. These ids are used for a suffix of a name of
     * address files and content files.
     * @param factory the DictionaryBufferFactory which is used for opening the files.
     */
    public SparseTableContentReader(final String name, final int blockSize, final File baseDir,
            final String[] contentFilenames, final String[] contentIds,
            final DictionaryBufferFactory factory) {
        if (contentFilenames.length != contentIds.length) {
            throw new RuntimeException("The length of contentFilenames and the length of"
                    + " contentIds are different " + contentFilenames.length + ", "
                    + contentIds.length);
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
                    name + FormatSpec.CONTENT_TABLE_FILE_SUFFIX + contentIds[i]);
            mContentFiles[i] = new File(mBaseDir, contentFilenames[i] + contentIds[i]);
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

    protected void read(final int contentIndex, final int index,
            final SparseTableContentReaderInterface reader) {
        if (index < 0 || (index / mBlockSize) * SparseTable.SIZE_OF_INT_IN_BYTES
                >= mLookupTableBuffer.limit()) {
            return;
        }

        mLookupTableBuffer.position((index / mBlockSize) * SparseTable.SIZE_OF_INT_IN_BYTES);
        final int posInAddressTable = mLookupTableBuffer.readInt();
        if (posInAddressTable == SparseTable.NOT_EXIST) {
            return;
        }

        mAddressTableBuffers[contentIndex].position(
                (posInAddressTable + index % mBlockSize) * SparseTable.SIZE_OF_INT_IN_BYTES);
        final int address = mAddressTableBuffers[contentIndex].readInt();
        if (address == SparseTable.NOT_EXIST) {
            return;
        }

        mContentBuffers[contentIndex].position(address);
        reader.read(mContentBuffers[contentIndex]);
    }
}