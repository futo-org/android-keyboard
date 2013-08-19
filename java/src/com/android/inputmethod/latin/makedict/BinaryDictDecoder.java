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
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.CharEncoding;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;
import com.android.inputmethod.latin.makedict.decoder.HeaderReader;
import com.android.inputmethod.latin.utils.ByteArrayDictBuffer;
import com.android.inputmethod.latin.utils.JniUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;

@UsedForTesting
public class BinaryDictDecoder implements HeaderReader {

    static {
        JniUtils.loadNativeLibrary();
    }

    // TODO: implement something sensical instead of just a phony method
    private static native int doNothing();

    public interface DictionaryBufferFactory {
        public DictBuffer getDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException;
    }

    /**
     * Creates DictionaryBuffer using a ByteBuffer
     *
     * This class uses less memory than DictionaryBufferFromByteArrayFactory,
     * but doesn't perform as fast.
     * When operating on a big dictionary, this class is preferred.
     */
    public static final class DictionaryBufferFromReadOnlyByteBufferFactory
            implements DictionaryBufferFactory {
        @Override
        public DictBuffer getDictionaryBuffer(final File file)
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
                return new BinaryDictDecoderUtils.ByteBufferDictBuffer(buffer);
            }
            return null;
        }
    }

    /**
     * Creates DictionaryBuffer using a byte array
     *
     * This class performs faster than other classes, but consumes more memory.
     * When operating on a small dictionary, this class is preferred.
     */
    public static final class DictionaryBufferFromByteArrayFactory
            implements DictionaryBufferFactory {
        @Override
        public DictBuffer getDictionaryBuffer(final File file)
                throws FileNotFoundException, IOException {
            FileInputStream inStream = null;
            try {
                inStream = new FileInputStream(file);
                final byte[] array = new byte[(int) file.length()];
                inStream.read(array);
                return new ByteArrayDictBuffer(array);
            } finally {
                if (inStream != null) {
                    inStream.close();
                }
            }
        }
    }

    /**
     * Creates DictionaryBuffer using a writable ByteBuffer and a RandomAccessFile.
     *
     * This class doesn't perform as fast as other classes,
     * but this class is the only option available for destructive operations (insert or delete)
     * on a dictionary.
     */
    @UsedForTesting
    public static final class DictionaryBufferFromWritableByteBufferFactory
            implements DictionaryBufferFactory {
        @Override
        public DictBuffer getDictionaryBuffer(final File file)
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
                return new BinaryDictDecoderUtils.ByteBufferDictBuffer(buffer);
            }
            return null;
        }
    }

    private final File mDictionaryBinaryFile;
    private DictBuffer mDictBuffer;

    public BinaryDictDecoder(final File file) {
        mDictionaryBinaryFile = file;
        mDictBuffer = null;
    }

    public void openDictBuffer(final DictionaryBufferFactory factory)
            throws FileNotFoundException, IOException {
        mDictBuffer = factory.getDictionaryBuffer(mDictionaryBinaryFile);
    }

    public DictBuffer getDictBuffer() {
        return mDictBuffer;
    }

    @UsedForTesting
    public DictBuffer openAndGetDictBuffer(
            final DictionaryBufferFactory factory)
                    throws FileNotFoundException, IOException {
        openDictBuffer(factory);
        return getDictBuffer();
    }

    // The implementation of HeaderReader
    @Override
    public int readVersion() throws IOException, UnsupportedFormatException {
        return BinaryDictDecoderUtils.checkFormatVersion(mDictBuffer);
    }

    @Override
    public int readOptionFlags() {
        return mDictBuffer.readUnsignedShort();
    }

    @Override
    public int readHeaderSize() {
        return mDictBuffer.readInt();
    }

    @Override
    public HashMap<String, String> readAttributes(final int headerSize) {
        final HashMap<String, String> attributes = new HashMap<String, String>();
        while (mDictBuffer.position() < headerSize) {
            // We can avoid infinite loop here since mFusionDictonary.position() is always increased
            // by calling CharEncoding.readString.
            final String key = CharEncoding.readString(mDictBuffer);
            final String value = CharEncoding.readString(mDictBuffer);
            attributes.put(key, value);
        }
        mDictBuffer.position(headerSize);
        return attributes;
    }
}
