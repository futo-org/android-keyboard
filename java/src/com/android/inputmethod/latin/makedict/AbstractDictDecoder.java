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
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * A base class of the binary dictionary decoder.
 */
public abstract class AbstractDictDecoder implements DictDecoder {
    private static final int SUCCESS = 0;
    private static final int ERROR_CANNOT_READ = 1;
    private static final int ERROR_WRONG_FORMAT = 2;

    protected DictionaryHeader readHeader(final DictBuffer headerBuffer)
            throws IOException, UnsupportedFormatException {
        if (headerBuffer == null) {
            openDictBuffer();
        }

        final int version = HeaderReader.readVersion(headerBuffer);
        if (version < FormatSpec.MINIMUM_SUPPORTED_VERSION
                || version > FormatSpec.MAXIMUM_SUPPORTED_VERSION) {
          throw new UnsupportedFormatException("Unsupported version : " + version);
        }
        // TODO: Remove this field.
        HeaderReader.readOptionFlags(headerBuffer);
        final int headerSize = HeaderReader.readHeaderSize(headerBuffer);
        if (headerSize < 0) {
            throw new UnsupportedFormatException("header size can't be negative.");
        }

        final HashMap<String, String> attributes = HeaderReader.readAttributes(headerBuffer,
                headerSize);

        final DictionaryHeader header = new DictionaryHeader(headerSize,
                new FusionDictionary.DictionaryOptions(attributes),
                new FormatOptions(version, DictionaryHeader.ATTRIBUTE_VALUE_TRUE.equals(
                        attributes.get(DictionaryHeader.HAS_HISTORICAL_INFO_KEY))));
        return header;
    }

    @Override @UsedForTesting
    public int getTerminalPosition(final String word)
            throws IOException, UnsupportedFormatException {
        if (!isDictBufferOpen()) {
            openDictBuffer();
        }
        return BinaryDictIOUtils.getTerminalPosition(this, word);
    }

    @Override @UsedForTesting
    public void readUnigramsAndBigramsBinary(final TreeMap<Integer, String> words,
            final TreeMap<Integer, Integer> frequencies,
            final TreeMap<Integer, ArrayList<PendingAttribute>> bigrams)
            throws IOException, UnsupportedFormatException {
        if (!isDictBufferOpen()) {
            openDictBuffer();
        }
        BinaryDictIOUtils.readUnigramsAndBigramsBinary(this, words, frequencies, bigrams);
    }

    /**
     * A utility class for reading a file header.
     */
    protected static class HeaderReader {
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

    /**
     * Check whether the header contains the expected information. This is a no-error method,
     * that will return an error code and never throw a checked exception.
     * @return an error code, either ERROR_* or SUCCESS.
     */
    private int checkHeader() {
        try {
            readHeader();
        } catch (IOException e) {
            return ERROR_CANNOT_READ;
        } catch (UnsupportedFormatException e) {
            return ERROR_WRONG_FORMAT;
        }
        return SUCCESS;
    }

    @Override
    public boolean hasValidRawBinaryDictionary() {
        return checkHeader() == SUCCESS;
    }

    // Placeholder implementations below. These are actually unused.
    @Override
    public void openDictBuffer() throws FileNotFoundException, IOException,
            UnsupportedFormatException {
    }

    @Override
    public boolean isDictBufferOpen() {
        return false;
    }

    @Override
    public PtNodeInfo readPtNode(final int ptNodePos, final FormatOptions options) {
        return null;
    }

    @Override
    public void setPosition(int newPos) {
    }

    @Override
    public int getPosition() {
        return 0;
    }

    @Override
    public int readPtNodeCount() {
        return 0;
    }

    @Override
    public boolean readAndFollowForwardLink() {
        return false;
    }

    @Override
    public boolean hasNextPtNodeArray() {
        return false;
    }
}
