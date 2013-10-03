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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Random;

/**
 * Unit tests for SparseTable.
 */
@LargeTest
public class SparseTableTests extends AndroidTestCase {
    private static final String TAG = SparseTableTests.class.getSimpleName();

    private static final int[] SMALL_INDEX = { SparseTable.NOT_EXIST, 0 };
    private static final int[] BIG_INDEX = { SparseTable.NOT_EXIST, 1, 2, 3, 4, 5, 6, 7};

    private final Random mRandom;
    private final ArrayList<Integer> mRandomIndex;

    private static final int DEFAULT_SIZE = 10000;
    private static final int BLOCK_SIZE = 8;

    public SparseTableTests() {
        this(System.currentTimeMillis(), DEFAULT_SIZE);
    }

    public SparseTableTests(final long seed, final int tableSize) {
        super();
        Log.d(TAG, "Seed for test is " + seed + ", size is " + tableSize);
        mRandom = new Random(seed);
        mRandomIndex = new ArrayList<Integer>(tableSize);
        for (int i = 0; i < tableSize; ++i) {
            mRandomIndex.add(SparseTable.NOT_EXIST);
        }
    }

    public void testInitializeWithArray() {
        final SparseTable table = new SparseTable(SMALL_INDEX, BIG_INDEX, BLOCK_SIZE);
        for (int i = 0; i < 8; ++i) {
            assertEquals(SparseTable.NOT_EXIST, table.get(i));
        }
        assertEquals(SparseTable.NOT_EXIST, table.get(8));
        for (int i = 9; i < 16; ++i) {
            assertEquals(i - 8, table.get(i));
        }
    }

    public void testSet() {
        final SparseTable table = new SparseTable(16, BLOCK_SIZE);
        table.set(3, 6);
        table.set(8, 16);
        for (int i = 0; i < 16; ++i) {
            if (i == 3 || i == 8) {
                assertEquals(i * 2, table.get(i));
            } else {
                assertEquals(SparseTable.NOT_EXIST, table.get(i));
            }
        }
    }

    private void generateRandomIndex(final int size, final int prop) {
        for (int i = 0; i < DEFAULT_SIZE; ++i) {
            if (mRandom.nextInt(100) < prop) {
                mRandomIndex.set(i, mRandom.nextInt());
            } else {
                mRandomIndex.set(i, SparseTable.NOT_EXIST);
            }
        }
    }

    private void runTestRandomSet() {
        final SparseTable table = new SparseTable(DEFAULT_SIZE, BLOCK_SIZE);
        int elementCount = 0;
        for (int i = 0; i < DEFAULT_SIZE; ++i) {
            if (mRandomIndex.get(i) != SparseTable.NOT_EXIST) {
                table.set(i, mRandomIndex.get(i));
                elementCount++;
            }
        }

        Log.d(TAG, "table size = " + table.getLookupTableSize() + " + "
              + table.getContentTableSize());
        Log.d(TAG, "the table has " + elementCount + " elements");
        for (int i = 0; i < DEFAULT_SIZE; ++i) {
            assertEquals(table.get(i), (int)mRandomIndex.get(i));
        }

        // flush and reload
        OutputStream lookupOutStream = null;
        OutputStream contentOutStream = null;
        InputStream lookupInStream = null;
        InputStream contentInStream = null;
        try {
            final File lookupIndexFile = File.createTempFile("testRandomSet", ".small");
            final File contentFile = File.createTempFile("testRandomSet", ".big");
            lookupOutStream = new FileOutputStream(lookupIndexFile);
            contentOutStream = new FileOutputStream(contentFile);
            table.write(lookupOutStream, contentOutStream);
            lookupInStream = new FileInputStream(lookupIndexFile);
            contentInStream = new FileInputStream(contentFile);
            final byte[] lookupArray = new byte[(int) lookupIndexFile.length()];
            final byte[] contentArray = new byte[(int) contentFile.length()];
            lookupInStream.read(lookupArray);
            contentInStream.read(contentArray);
            final SparseTable newTable = new SparseTable(lookupArray, contentArray, BLOCK_SIZE);
            for (int i = 0; i < DEFAULT_SIZE; ++i) {
                assertEquals(table.get(i), newTable.get(i));
            }
        } catch (IOException e) {
            Log.d(TAG, "IOException while flushing and realoding", e);
        } finally {
            if (lookupOutStream != null) {
                try {
                    lookupOutStream.close();
                } catch (IOException e) {
                    Log.d(TAG, "IOException while closing the stream", e);
                }
            }
            if (contentOutStream != null) {
                try {
                    contentOutStream.close();
                } catch (IOException e) {
                    Log.d(TAG, "IOException while closing contentStream.", e);
                }
            }
        }
    }

    public void testRandomSet() {
        for (int i = 0; i <= 100; i += 10) {
            generateRandomIndex(DEFAULT_SIZE, i);
            runTestRandomSet();
        }
    }
}
