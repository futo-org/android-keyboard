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
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;

/**
 * SparseTable is an extensible map from integer to integer.
 * This holds one value for every mBlockSize keys, so it uses 1/mBlockSize'th of the full index
 * memory.
 */
@UsedForTesting
public class SparseTable {

    /**
     * mLookupTable is indexed by terminal ID, containing exactly one entry for every mBlockSize
     * terminals.
     * It contains at index i = j / mBlockSize the index in each ArrayList in mContentsTables where
     * the values for terminals with IDs j to j + mBlockSize - 1 are stored as an mBlockSize-sized
     * integer array.
     */
    private final ArrayList<Integer> mLookupTable;
    private final ArrayList<ArrayList<Integer>> mContentTables;

    private final int mBlockSize;
    private final int mContentTableCount;
    public static final int NOT_EXIST = -1;
    public static final int SIZE_OF_INT_IN_BYTES = 4;

    @UsedForTesting
    public SparseTable(final int initialCapacity, final int blockSize,
            final int contentTableCount) {
        mBlockSize = blockSize;
        final int lookupTableSize = initialCapacity / mBlockSize
                + (initialCapacity % mBlockSize > 0 ? 1 : 0);
        mLookupTable = new ArrayList<Integer>(Collections.nCopies(lookupTableSize, NOT_EXIST));
        mContentTableCount = contentTableCount;
        mContentTables = CollectionUtils.newArrayList();
        for (int i = 0; i < mContentTableCount; ++i) {
            mContentTables.add(new ArrayList<Integer>());
        }
    }

    @UsedForTesting
    public SparseTable(final ArrayList<Integer> lookupTable,
            final ArrayList<ArrayList<Integer>> contentTables, final int blockSize) {
        mBlockSize = blockSize;
        mContentTableCount = contentTables.size();
        mLookupTable = lookupTable;
        mContentTables = contentTables;
    }

    /**
     * Converts an byte array to an int array considering each set of 4 bytes is an int stored in
     * big-endian.
     * The length of byteArray must be a multiple of four.
     * Otherwise, IndexOutOfBoundsException will be raised.
     */
    @UsedForTesting
    private static ArrayList<Integer> convertByteArrayToIntegerArray(final byte[] byteArray) {
        final ArrayList<Integer> integerArray = new ArrayList<Integer>(byteArray.length / 4);
        for (int i = 0; i < byteArray.length; i += 4) {
            int value = 0;
            for (int j = i; j < i + 4; ++j) {
                value <<= 8;
                value |= byteArray[j] & 0xFF;
             }
            integerArray.add(value);
        }
        return integerArray;
    }

    @UsedForTesting
    public int get(final int contentTableIndex, final int index) {
        if (!contains(index)) {
            return NOT_EXIST;
        }
        return mContentTables.get(contentTableIndex).get(
                mLookupTable.get(index / mBlockSize) + (index % mBlockSize));
    }

    @UsedForTesting
    public ArrayList<Integer> getAll(final int index) {
        final ArrayList<Integer> ret = CollectionUtils.newArrayList();
        for (int i = 0; i < mContentTableCount; ++i) {
            ret.add(get(i, index));
        }
        return ret;
    }

    @UsedForTesting
    public void set(final int contentTableIndex, final int index, final int value) {
        if (mLookupTable.get(index / mBlockSize) == NOT_EXIST) {
            mLookupTable.set(index / mBlockSize, mContentTables.get(contentTableIndex).size());
            for (int i = 0; i < mContentTableCount; ++i) {
                for (int j = 0; j < mBlockSize; ++j) {
                    mContentTables.get(i).add(NOT_EXIST);
                }
            }
        }
        mContentTables.get(contentTableIndex).set(
                mLookupTable.get(index / mBlockSize) + (index % mBlockSize), value);
    }

    public void remove(final int indexOfContent, final int index) {
        set(indexOfContent, index, NOT_EXIST);
    }

    @UsedForTesting
    public int size() {
        return mLookupTable.size() * mBlockSize;
    }

    @UsedForTesting
    /* package */ int getContentTableSize() {
        // This class always has at least one content table.
        return mContentTables.get(0).size();
    }

    @UsedForTesting
    /* package */ int getLookupTableSize() {
        return mLookupTable.size();
    }

    public boolean contains(final int index) {
        if (index < 0 || index / mBlockSize >= mLookupTable.size()
                || mLookupTable.get(index / mBlockSize) == NOT_EXIST) {
            return false;
        }
        return true;
    }

    @UsedForTesting
    public void write(final OutputStream lookupOutStream, final OutputStream[] contentOutStreams)
            throws IOException {
         if (contentOutStreams.length != mContentTableCount) {
             throw new RuntimeException(contentOutStreams.length + " streams are given, but the"
                     + " table has " + mContentTableCount + " content tables.");
         }
        for (final int index : mLookupTable) {
          BinaryDictEncoderUtils.writeUIntToStream(lookupOutStream, index, SIZE_OF_INT_IN_BYTES);
        }

        for (int i = 0; i < contentOutStreams.length; ++i) {
            for (final int data : mContentTables.get(i)) {
                BinaryDictEncoderUtils.writeUIntToStream(contentOutStreams[i], data,
                        SIZE_OF_INT_IN_BYTES);
            }
        }
    }

    @UsedForTesting
    public void writeToFiles(final File lookupTableFile, final File[] contentFiles)
            throws IOException {
        FileOutputStream lookupTableOutStream = null;
        final FileOutputStream[] contentTableOutStreams = new FileOutputStream[mContentTableCount];
        try {
            lookupTableOutStream = new FileOutputStream(lookupTableFile);
            for (int i = 0; i < contentFiles.length; ++i) {
                contentTableOutStreams[i] = new FileOutputStream(contentFiles[i]);
            }
            write(lookupTableOutStream, contentTableOutStreams);
        } finally {
            if (lookupTableOutStream != null) {
                lookupTableOutStream.close();
            }
            for (int i = 0; i < contentTableOutStreams.length; ++i) {
                if (contentTableOutStreams[i] != null) {
                    contentTableOutStreams[i].close();
                }
            }
        }
    }

    private static byte[] readFileToByteArray(final File file) throws IOException {
        final byte[] contents = new byte[(int) file.length()];
        FileInputStream inStream = null;
        try {
            inStream = new FileInputStream(file);
            inStream.read(contents);
        } finally {
            if (inStream != null) {
                inStream.close();
            }
        }
        return contents;
    }

    @UsedForTesting
    public static SparseTable readFromFiles(final File lookupTableFile, final File[] contentFiles,
            final int blockSize) throws IOException {
        final ArrayList<ArrayList<Integer>> contentTables =
                new ArrayList<ArrayList<Integer>>(contentFiles.length);
        for (int i = 0; i < contentFiles.length; ++i) {
            contentTables.add(convertByteArrayToIntegerArray(readFileToByteArray(contentFiles[i])));
        }
        return new SparseTable(convertByteArrayToIntegerArray(readFileToByteArray(lookupTableFile)),
                contentTables, blockSize);
    }
}
