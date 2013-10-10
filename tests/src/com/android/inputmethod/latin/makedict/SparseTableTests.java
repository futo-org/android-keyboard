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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Random;

/**
 * Unit tests for SparseTable.
 */
@LargeTest
public class SparseTableTests extends AndroidTestCase {
    private static final String TAG = SparseTableTests.class.getSimpleName();

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

    public void testSet() {
        final SparseTable table = new SparseTable(16, BLOCK_SIZE, 1);
        table.set(0, 3, 6);
        table.set(0, 8, 16);
        for (int i = 0; i < 16; ++i) {
            if (i == 3 || i == 8) {
                assertEquals(i * 2, table.get(0, i));
            } else {
                assertEquals(SparseTable.NOT_EXIST, table.get(0, i));
            }
        }
    }

    private void generateRandomIndex(final int size, final int prop) {
        for (int i = 0; i < size; ++i) {
            if (mRandom.nextInt(100) < prop) {
                mRandomIndex.set(i, mRandom.nextInt());
            } else {
                mRandomIndex.set(i, SparseTable.NOT_EXIST);
            }
        }
    }

    private void runTestRandomSet() {
        final SparseTable table = new SparseTable(DEFAULT_SIZE, BLOCK_SIZE, 1);
        int elementCount = 0;
        for (int i = 0; i < DEFAULT_SIZE; ++i) {
            if (mRandomIndex.get(i) != SparseTable.NOT_EXIST) {
                table.set(0, i, mRandomIndex.get(i));
                elementCount++;
            }
        }

        Log.d(TAG, "table size = " + table.getLookupTableSize() + " + "
              + table.getContentTableSize());
        Log.d(TAG, "the table has " + elementCount + " elements");
        for (int i = 0; i < DEFAULT_SIZE; ++i) {
            assertEquals(table.get(0, i), (int)mRandomIndex.get(i));
        }

        // flush and reload
        OutputStream lookupOutStream = null;
        OutputStream contentOutStream = null;
        try {
            final File lookupIndexFile = File.createTempFile("testRandomSet", ".small");
            final File contentFile = File.createTempFile("testRandomSet", ".big");
            lookupOutStream = new FileOutputStream(lookupIndexFile);
            contentOutStream = new FileOutputStream(contentFile);
            table.write(lookupOutStream, new OutputStream[] { contentOutStream });
            lookupOutStream.flush();
            contentOutStream.flush();
            final SparseTable newTable = SparseTable.readFromFiles(lookupIndexFile,
                    new File[] { contentFile }, BLOCK_SIZE);
            for (int i = 0; i < DEFAULT_SIZE; ++i) {
                assertEquals(table.get(0, i), newTable.get(0, i));
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

    public void testMultipleContents() {
        final int numOfContents = 5;
        generateRandomIndex(DEFAULT_SIZE, 20);
        final SparseTable table = new SparseTable(DEFAULT_SIZE, BLOCK_SIZE, numOfContents);
        for (int i = 0; i < mRandomIndex.size(); ++i) {
            if (mRandomIndex.get(i) != SparseTable.NOT_EXIST) {
                for (int j = 0; j < numOfContents; ++j) {
                    table.set(j, i, mRandomIndex.get(i));
                }
            }
        }

        OutputStream lookupOutStream = null;
        OutputStream[] contentsOutStream = new OutputStream[numOfContents];
        try {
            final File lookupIndexFile = File.createTempFile("testMultipleContents", "small");
            lookupOutStream = new FileOutputStream(lookupIndexFile);
            final File[] contentFiles = new File[numOfContents];
            for (int i = 0; i < numOfContents; ++i) {
                contentFiles[i] = File.createTempFile("testMultipleContents", "big" + i);
                contentsOutStream[i] = new FileOutputStream(contentFiles[i]);
            }
            table.write(lookupOutStream, contentsOutStream);
            lookupOutStream.flush();
            for (int i = 0; i < numOfContents; ++i) {
                contentsOutStream[i].flush();
            }
            final SparseTable newTable = SparseTable.readFromFiles(lookupIndexFile, contentFiles,
                    BLOCK_SIZE);
            for (int i = 0; i < numOfContents; ++i) {
                for (int j = 0; j < DEFAULT_SIZE; ++j) {
                    assertEquals(table.get(i, j), newTable.get(i, j));
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "IOException while flushing and reloading", e);
        } finally {
            if (lookupOutStream != null) {
                try {
                    lookupOutStream.close();
                } catch (IOException e) {
                    Log.d(TAG, "IOException while closing the stream", e);
                }
            }
            for (int i = 0; i < numOfContents; ++i) {
                if (contentsOutStream[i] != null) {
                    try {
                        contentsOutStream[i].close();
                    } catch (IOException e) {
                        Log.d(TAG, "IOException while closing the stream.", e);
                    }
                }
            }
        }
    }
}
