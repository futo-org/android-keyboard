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
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.utils.JniUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

/**
 * An implementation of DictDecoder for version 3 binary dictionary.
 */
@UsedForTesting
public class Ver3DictDecoder implements DictDecoder {

    static {
        JniUtils.loadNativeLibrary();
    }

    // TODO: implement something sensical instead of just a phony method
    private static native int doNothing();

    private final static class HeaderReader {
        protected static int readVersion(final DictBuffer dictBuffer)
                throws IOException, UnsupportedFormatException {
            return BinaryDictDecoderUtils.checkFormatVersion(dictBuffer);
        }

        protected static int readOptionFlags(final DictBuffer dictBuffer) {
            return dictBuffer.readUnsignedShort();
        }

        protected static int readHeaderSize(final DictBuffer dictBuffer) {
            return dictBuffer.readInt();
        }

        protected static HashMap<String, String> readAttributes(final DictBuffer dictBuffer,
                final int headerSize) {
            final HashMap<String, String> attributes = new HashMap<String, String>();
            while (dictBuffer.position() < headerSize) {
                // We can avoid an infinite loop here since dictBuffer.position() is always
                // increased by calling CharEncoding.readString.
                final String key = CharEncoding.readString(dictBuffer);
                final String value = CharEncoding.readString(dictBuffer);
                attributes.put(key, value);
            }
            dictBuffer.position(headerSize);
            return attributes;
        }
    }

    private final File mDictionaryBinaryFile;
    private DictBuffer mDictBuffer;

    public Ver3DictDecoder(final File file) {
        mDictionaryBinaryFile = file;
        mDictBuffer = null;
    }

    public void openDictBuffer(final DictDecoder.DictionaryBufferFactory factory)
            throws FileNotFoundException, IOException {
        mDictBuffer = factory.getDictionaryBuffer(mDictionaryBinaryFile);
    }

    public DictBuffer getDictBuffer() {
        return mDictBuffer;
    }

    @UsedForTesting
    public DictBuffer openAndGetDictBuffer(final DictDecoder.DictionaryBufferFactory factory)
                    throws FileNotFoundException, IOException {
        openDictBuffer(factory);
        return getDictBuffer();
    }

    @Override
    public FileHeader readHeader() throws IOException, UnsupportedFormatException {
        final int version = HeaderReader.readVersion(mDictBuffer);
        final int optionsFlags = HeaderReader.readOptionFlags(mDictBuffer);

        final int headerSize = HeaderReader.readHeaderSize(mDictBuffer);

        if (headerSize < 0) {
            throw new UnsupportedFormatException("header size can't be negative.");
        }

        final HashMap<String, String> attributes = HeaderReader.readAttributes(mDictBuffer,
                headerSize);

        final FileHeader header = new FileHeader(headerSize,
                new FusionDictionary.DictionaryOptions(attributes,
                        0 != (optionsFlags & FormatSpec.GERMAN_UMLAUT_PROCESSING_FLAG),
                        0 != (optionsFlags & FormatSpec.FRENCH_LIGATURE_PROCESSING_FLAG)),
                new FormatOptions(version,
                        0 != (optionsFlags & FormatSpec.SUPPORTS_DYNAMIC_UPDATE)));
        return header;
    }
}
