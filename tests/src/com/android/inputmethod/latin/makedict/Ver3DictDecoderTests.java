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
import com.android.inputmethod.latin.makedict.DictDecoder.DictionaryBufferFromByteArrayFactory;
import com.android.inputmethod.latin.makedict.DictDecoder.
        DictionaryBufferFromReadOnlyByteBufferFactory;
import com.android.inputmethod.latin.makedict.DictDecoder.
        DictionaryBufferFromWritableByteBufferFactory;

import android.test.AndroidTestCase;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Unit tests for Ver3DictDecoder
 */
public class Ver3DictDecoderTests extends AndroidTestCase {
    private static final String TAG = Ver3DictDecoderTests.class.getSimpleName();

    private final byte[] data = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };

    // Utilities for testing
    public void writeDataToFile(final File file) {
        FileOutputStream outStream = null;
        try {
            outStream = new FileOutputStream(file);
            outStream.write(data);
        } catch (IOException e) {
            fail ("Can't write data to the test file");
        } finally {
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close the output stream", e);
                }
            }
        }
    }

    @SuppressWarnings("null")
    public void runTestOpenBuffer(final String testName, final DictionaryBufferFactory factory) {
        File testFile = null;
        try {
            testFile = File.createTempFile(testName, ".tmp", getContext().getCacheDir());
        } catch (IOException e) {
            Log.e(TAG, "IOException while the creating temporary file", e);
        }

        assertNotNull(testFile);
        final Ver3DictDecoder dictDecoder = new Ver3DictDecoder(testFile, factory);
        try {
            dictDecoder.openDictBuffer();
        } catch (Exception e) {
            Log.e(TAG, "Failed to open the buffer", e);
        }

        writeDataToFile(testFile);

        try {
            dictDecoder.openDictBuffer();
        } catch (Exception e) {
            Log.e(TAG, "Raised the exception while opening buffer", e);
        }

        assertEquals(testFile.length(), dictDecoder.getDictBuffer().capacity());
    }

    public void testOpenBufferWithByteBuffer() {
        runTestOpenBuffer("testOpenBufferWithByteBuffer",
                new DictionaryBufferFromReadOnlyByteBufferFactory());
    }

    public void testOpenBufferWithByteArray() {
        runTestOpenBuffer("testOpenBufferWithByteArray",
                new DictionaryBufferFromByteArrayFactory());
    }

    public void testOpenBufferWithWritableByteBuffer() {
        runTestOpenBuffer("testOpenBufferWithWritableByteBuffer",
                new DictionaryBufferFromWritableByteBufferFactory());
    }

    @SuppressWarnings("null")
    public void runTestGetBuffer(final String testName, final DictionaryBufferFactory factory) {
        File testFile = null;
        try {
            testFile = File.createTempFile(testName, ".tmp", getContext().getCacheDir());
        } catch (IOException e) {
            Log.e(TAG, "IOException while the creating temporary file", e);
        }

        final Ver3DictDecoder dictDecoder = new Ver3DictDecoder(testFile, factory);

        // the default return value of getBuffer() must be null.
        assertNull("the default return value of getBuffer() is not null",
                dictDecoder.getDictBuffer());

        writeDataToFile(testFile);
        assertTrue(testFile.exists());
        Log.d(TAG, "file length = " + testFile.length());

        DictBuffer dictBuffer = null;
        try {
            dictBuffer = dictDecoder.openAndGetDictBuffer();
        } catch (IOException e) {
            Log.e(TAG, "Failed to open and get the buffer", e);
        }
        assertNotNull("the buffer must not be null", dictBuffer);

        for (int i = 0; i < data.length; ++i) {
            assertEquals(data[i], dictBuffer.readUnsignedByte());
        }
    }

    public void testGetBufferWithByteBuffer() {
        runTestGetBuffer("testGetBufferWithByteBuffer",
                new DictionaryBufferFromReadOnlyByteBufferFactory());
    }

    public void testGetBufferWithByteArray() {
        runTestGetBuffer("testGetBufferWithByteArray",
                new DictionaryBufferFromByteArrayFactory());
    }

    public void testGetBufferWithWritableByteBuffer() {
        runTestGetBuffer("testGetBufferWithWritableByteBuffer",
                new DictionaryBufferFromWritableByteBufferFactory());
    }
}
