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
import com.android.inputmethod.latin.makedict.BinaryDictDecoder.FusionDictionaryBufferInterface;
import com.android.inputmethod.latin.utils.ByteArrayWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class BinaryDictReader {

    public interface FusionDictionaryBufferFactory {
        public FusionDictionaryBufferInterface getFusionDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException;
    }

    /**
     * Creates FusionDictionaryBuffer from a ByteBuffer
     */
    public static final class FusionDictionaryBufferFromByteBufferFactory
            implements FusionDictionaryBufferFactory {
        @Override
        public FusionDictionaryBufferInterface getFusionDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException {
            FileInputStream inStream = null;
            ByteBuffer buffer = null;
            try {
                inStream = new FileInputStream(file);
                buffer = inStream.getChannel().map(FileChannel.MapMode.READ_ONLY,
                        0, file.length());
            } finally {
                if (inStream != null) {
                    inStream.close();
                }
            }
            if (buffer != null) {
                return new BinaryDictDecoder.ByteBufferWrapper(buffer);
            }
            return null;
        }
    }

    /**
     * Creates FusionDictionaryBuffer from a byte array
     */
    public static final class FusionDictionaryBufferFromByteArrayFactory
            implements FusionDictionaryBufferFactory {
        @Override
        public FusionDictionaryBufferInterface getFusionDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException {
            FileInputStream inStream = null;
            try {
                inStream = new FileInputStream(file);
                final byte[] array = new byte[(int) file.length()];
                inStream.read(array);
                return new ByteArrayWrapper(array);
            } finally {
                if (inStream != null) {
                    inStream.close();
                }
            }
        }
    }

    /**
     * Creates FusionDictionaryBuffer from a RandomAccessFile.
     */
    @UsedForTesting
    public static final class FusionDictionaryBufferFromWritableByteBufferFactory
            implements FusionDictionaryBufferFactory {
        @Override
        public FusionDictionaryBufferInterface getFusionDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException {
            RandomAccessFile raFile = null;
            ByteBuffer buffer = null;
            try {
                raFile = new RandomAccessFile(file, "rw");
                buffer = raFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, file.length());
            } finally {
                if (raFile != null) {
                    raFile.close();
                }
            }
            if (buffer != null) {
                return new BinaryDictDecoder.ByteBufferWrapper(buffer);
            }
            return null;
        }
    }

    private final File mDictionaryBinaryFile;
    private FusionDictionaryBufferInterface mFusionDictionaryBuffer;

    public BinaryDictReader(final File file) {
        mDictionaryBinaryFile = file;
        mFusionDictionaryBuffer = null;
    }

    public void openBuffer(final FusionDictionaryBufferFactory factory)
            throws FileNotFoundException, IOException {
        mFusionDictionaryBuffer = factory.getFusionDictionaryBuffer(mDictionaryBinaryFile);
    }

    public FusionDictionaryBufferInterface getBuffer() {
        return mFusionDictionaryBuffer;
    }

    @UsedForTesting
    public FusionDictionaryBufferInterface openAndGetBuffer(
            final FusionDictionaryBufferFactory factory)
                    throws FileNotFoundException, IOException {
        openBuffer(factory);
        return getBuffer();
    }
}
