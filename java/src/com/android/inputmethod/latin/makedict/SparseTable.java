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
     * It contains at index i = j / mBlockSize the index in mContentsTable where the values for
     * terminals with IDs j to j + mBlockSize - 1 are stored as an mBlockSize-sized integer array.
     */
    private final ArrayList<Integer> mLookupTable;
    private final ArrayList<Integer> mContentTable;

    private final int mBlockSize;
    public static final int NOT_EXIST = -1;

    @UsedForTesting
    public SparseTable(final int initialCapacity, final int blockSize) {
        mBlockSize = blockSize;
        final int lookupTableSize = initialCapacity / mBlockSize
                + (initialCapacity % mBlockSize > 0 ? 1 : 0);
        mLookupTable = new ArrayList<Integer>(Collections.nCopies(lookupTableSize, NOT_EXIST));
        mContentTable = new ArrayList<Integer>();
    }

    @UsedForTesting
    public SparseTable(final int[] lookupTable, final int[] contentTable, final int blockSize) {
        mBlockSize = blockSize;
        mLookupTable = new ArrayList<Integer>(lookupTable.length);
        for (int i = 0; i < lookupTable.length; ++i) {
            mLookupTable.add(lookupTable[i]);
        }
        mContentTable = new ArrayList<Integer>(contentTable.length);
        for (int i = 0; i < contentTable.length; ++i) {
            mContentTable.add(contentTable[i]);
        }
    }

    /**
     * Converts an byte array to an int array considering each set of 4 bytes is an int stored in
     * big-endian.
     * The length of byteArray must be a multiple of four.
     * Otherwise, IndexOutOfBoundsException will be raised.
     */
    @UsedForTesting
    private static void convertByteArrayToIntegerArray(final byte[] byteArray,
            final ArrayList<Integer> integerArray) {
        for (int i = 0; i < byteArray.length; i += 4) {
            int value = 0;
            for (int j = i; j < i + 4; ++j) {
                value <<= 8;
                value |= byteArray[j] & 0xFF;
             }
            integerArray.add(value);
        }
    }

    @UsedForTesting
    public SparseTable(final byte[] lookupTable, final byte[] contentTable, final int blockSize) {
        mBlockSize = blockSize;
        mLookupTable = new ArrayList<Integer>(lookupTable.length / 4);
        mContentTable = new ArrayList<Integer>(contentTable.length / 4);
        convertByteArrayToIntegerArray(lookupTable, mLookupTable);
        convertByteArrayToIntegerArray(contentTable, mContentTable);
    }

    @UsedForTesting
    public int get(final int index) {
        if (index < 0 || index / mBlockSize >= mLookupTable.size()
                || mLookupTable.get(index / mBlockSize) == NOT_EXIST) {
            return NOT_EXIST;
        }
        return mContentTable.get(mLookupTable.get(index / mBlockSize) + (index % mBlockSize));
    }

    @UsedForTesting
    public void set(final int index, final int value) {
        if (mLookupTable.get(index / mBlockSize) == NOT_EXIST) {
            mLookupTable.set(index / mBlockSize, mContentTable.size());
            for (int i = 0; i < mBlockSize; ++i) {
                mContentTable.add(NOT_EXIST);
            }
        }
        mContentTable.set(mLookupTable.get(index / mBlockSize) + (index % mBlockSize), value);
    }

    public void remove(final int index) {
        set(index, NOT_EXIST);
    }

    @UsedForTesting
    public int size() {
        return mLookupTable.size() * mBlockSize;
    }

    @UsedForTesting
    /* package */ int getContentTableSize() {
        return mContentTable.size();
    }

    @UsedForTesting
    /* package */ int getLookupTableSize() {
        return mLookupTable.size();
    }

    public boolean contains(final int index) {
        return get(index) != NOT_EXIST;
    }

    @UsedForTesting
    public void write(final OutputStream lookupOutStream, final OutputStream contentOutStream)
            throws IOException {
        for (final int index : mLookupTable) {
          BinaryDictEncoderUtils.writeUIntToStream(lookupOutStream, index, 4);
        }

        for (final int index : mContentTable) {
            BinaryDictEncoderUtils.writeUIntToStream(contentOutStream, index, 4);
        }
    }

    @UsedForTesting
    public void writeToFiles(final File lookupTableFile, final File contentFile)
            throws IOException {
      FileOutputStream lookupTableOutStream = null;
      FileOutputStream contentOutStream = null;
        try {
            lookupTableOutStream = new FileOutputStream(lookupTableFile);
            contentOutStream = new FileOutputStream(contentFile);
            write(lookupTableOutStream, contentOutStream);
        } finally {
            if (lookupTableOutStream != null) {
                lookupTableOutStream.close();
            }
            if (contentOutStream != null) {
                contentOutStream.close();
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
    public static SparseTable readFromFiles(final File lookupTableFile, final File contentFile,
            final int blockSize) throws IOException {
        final byte[] lookupTable = readFileToByteArray(lookupTableFile);
        final byte[] content = readFileToByteArray(contentFile);
        return new SparseTable(lookupTable, content, blockSize);
    }
}
