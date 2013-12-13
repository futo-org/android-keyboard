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

import com.android.inputmethod.latin.makedict.DictDecoder.DictionaryBufferFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An auxiliary class for updating data associated with SparseTable.
 */
public class SparseTableContentUpdater extends SparseTableContentReader {
    protected OutputStream mLookupTableOutStream;
    protected OutputStream[] mAddressTableOutStreams;
    protected OutputStream[] mContentOutStreams;

    public SparseTableContentUpdater(final String name, final int blockSize,
            final File baseDir, final String[] contentFilenames, final String[] contentIds,
            final DictionaryBufferFactory factory) {
        super(name, blockSize, baseDir, contentFilenames, contentIds, factory);
        mAddressTableOutStreams = new OutputStream[mContentCount];
        mContentOutStreams = new OutputStream[mContentCount];
    }

    protected void openStreamsAndBuffers() throws IOException {
        openBuffers();
        mLookupTableOutStream = new FileOutputStream(mLookupTableFile, true /* append */);
        for (int i = 0; i < mContentCount; ++i) {
            mAddressTableOutStreams[i] = new FileOutputStream(mAddressTableFiles[i],
                    true /* append */);
            mContentOutStreams[i] = new FileOutputStream(mContentFiles[i], true /* append */);
        }
    }

    /**
     * Set the contentIndex-th elements of contentId-th table.
     *
     * @param contentId the id of the content table.
     * @param contentIndex the index where to set the valie.
     * @param value the value to set.
     */
    protected void setContentValue(final int contentId, final int contentIndex, final int value)
            throws IOException {
        if ((contentIndex / mBlockSize) * SparseTable.SIZE_OF_INT_IN_BYTES
                >= mLookupTableBuffer.limit()) {
            // Need to extend the lookup table
            final int currentSize = mLookupTableBuffer.limit()
                    / SparseTable.SIZE_OF_INT_IN_BYTES;
            final int target = contentIndex / mBlockSize + 1;
            for (int i = currentSize; i < target; ++i) {
                BinaryDictEncoderUtils.writeUIntToStream(mLookupTableOutStream,
                        SparseTable.NOT_EXIST, SparseTable.SIZE_OF_INT_IN_BYTES);
            }
            // We need to reopen the byte buffer of the lookup table because a MappedByteBuffer in
            // Java isn't expanded automatically when the underlying file is expanded.
            reopenLookupTable();
        }

        mLookupTableBuffer.position((contentIndex / mBlockSize) * SparseTable.SIZE_OF_INT_IN_BYTES);
        int posInAddressTable = mLookupTableBuffer.readInt();
        if (posInAddressTable == SparseTable.NOT_EXIST) {
            // Need to extend the address table
            mLookupTableBuffer.position(mLookupTableBuffer.position()
                    - SparseTable.SIZE_OF_INT_IN_BYTES);
            posInAddressTable = mAddressTableBuffers[0].limit() / mBlockSize;
            BinaryDictEncoderUtils.writeUIntToDictBuffer(mLookupTableBuffer,
                    posInAddressTable, SparseTable.SIZE_OF_INT_IN_BYTES);
            for (int i = 0; i < mContentCount; ++i) {
                for (int j = 0; j < mBlockSize; ++j) {
                    BinaryDictEncoderUtils.writeUIntToStream(mAddressTableOutStreams[i],
                            SparseTable.NOT_EXIST, SparseTable.SIZE_OF_INT_IN_BYTES);
                }
            }
            // We need to reopen the byte buffers of the address tables because a MappedByteBuffer
            // in Java isn't expanded automatically when the underlying file is expanded.
            reopenAddressTables();
        }
        posInAddressTable += (contentIndex % mBlockSize) * SparseTable.SIZE_OF_INT_IN_BYTES;

        mAddressTableBuffers[contentId].position(posInAddressTable);
        BinaryDictEncoderUtils.writeUIntToDictBuffer(mAddressTableBuffers[contentId],
                value, SparseTable.SIZE_OF_INT_IN_BYTES);
    }

    private void reopenLookupTable() throws IOException {
        mLookupTableOutStream.flush();
        mLookupTableBuffer = mFactory.getDictionaryBuffer(mLookupTableFile);
    }

    private void reopenAddressTables() throws IOException {
        for (int i = 0; i < mContentCount; ++i) {
            mAddressTableOutStreams[i].flush();
            mAddressTableBuffers[i] = mFactory.getDictionaryBuffer(mAddressTableFiles[i]);
        }
    }

    protected void close() throws IOException {
        mLookupTableOutStream.close();
        for (final OutputStream stream : mAddressTableOutStreams) {
            stream.close();
        }
        for (final OutputStream stream : mContentOutStreams) {
            stream.close();
        }
    }
}
