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
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * A base class of the binary dictionary decoder.
 */
public abstract class AbstractDictDecoder implements DictDecoder {
    protected FileHeader readHeader(final DictBuffer dictBuffer)
            throws IOException, UnsupportedFormatException {
        if (dictBuffer == null) {
            openDictBuffer();
        }

        final int version = HeaderReader.readVersion(dictBuffer);
        if (version < FormatSpec.MINIMUM_SUPPORTED_VERSION
                || version > FormatSpec.MAXIMUM_SUPPORTED_VERSION) {
          throw new UnsupportedFormatException("Unsupported version : " + version);
        }
        // TODO: Remove this field.
        final int optionsFlags = HeaderReader.readOptionFlags(dictBuffer);

        final int headerSize = HeaderReader.readHeaderSize(dictBuffer);

        if (headerSize < 0) {
            throw new UnsupportedFormatException("header size can't be negative.");
        }

        final HashMap<String, String> attributes = HeaderReader.readAttributes(dictBuffer,
                headerSize);

        final FileHeader header = new FileHeader(headerSize,
                new FusionDictionary.DictionaryOptions(attributes,
                        0 != (optionsFlags & FormatSpec.GERMAN_UMLAUT_PROCESSING_FLAG),
                        0 != (optionsFlags & FormatSpec.FRENCH_LIGATURE_PROCESSING_FLAG)),
                        new FormatOptions(version,
                                0 != (optionsFlags & FormatSpec.SUPPORTS_DYNAMIC_UPDATE)));
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
     * A utility class for reading a PtNode.
     */
    protected static class PtNodeReader {
        protected static int readPtNodeOptionFlags(final DictBuffer dictBuffer) {
            return dictBuffer.readUnsignedByte();
        }

        protected static int readParentAddress(final DictBuffer dictBuffer,
                final FormatOptions formatOptions) {
            if (BinaryDictIOUtils.supportsDynamicUpdate(formatOptions)) {
                return BinaryDictDecoderUtils.readSInt24(dictBuffer);
            } else {
                return FormatSpec.NO_PARENT_ADDRESS;
            }
        }

        protected static int readChildrenAddress(final DictBuffer dictBuffer, final int optionFlags,
                final FormatOptions formatOptions) {
            if (BinaryDictIOUtils.supportsDynamicUpdate(formatOptions)) {
                final int address = BinaryDictDecoderUtils.readSInt24(dictBuffer);
                if (address == 0) return FormatSpec.NO_CHILDREN_ADDRESS;
                return address;
            } else {
                switch (optionFlags & FormatSpec.MASK_CHILDREN_ADDRESS_TYPE) {
                    case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_ONEBYTE:
                        return dictBuffer.readUnsignedByte();
                    case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_TWOBYTES:
                        return dictBuffer.readUnsignedShort();
                    case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_THREEBYTES:
                        return dictBuffer.readUnsignedInt24();
                    case FormatSpec.FLAG_CHILDREN_ADDRESS_TYPE_NOADDRESS:
                    default:
                        return FormatSpec.NO_CHILDREN_ADDRESS;
                }
            }
        }

        // Reads shortcuts and returns the read length.
        protected static int readShortcut(final DictBuffer dictBuffer,
                final ArrayList<WeightedString> shortcutTargets) {
            final int pointerBefore = dictBuffer.position();
            dictBuffer.readUnsignedShort(); // skip the size
            while (true) {
                final int targetFlags = dictBuffer.readUnsignedByte();
                final String word = CharEncoding.readString(dictBuffer);
                shortcutTargets.add(new WeightedString(word,
                        targetFlags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_FREQUENCY));
                if (0 == (targetFlags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_HAS_NEXT)) break;
            }
            return dictBuffer.position() - pointerBefore;
        }

        protected static int readBigramAddresses(final DictBuffer dictBuffer,
                final ArrayList<PendingAttribute> bigrams, final int baseAddress) {
            int readLength = 0;
            int bigramCount = 0;
            while (bigramCount++ < FormatSpec.MAX_BIGRAMS_IN_A_PTNODE) {
                final int bigramFlags = dictBuffer.readUnsignedByte();
                ++readLength;
                final int sign = 0 == (bigramFlags & FormatSpec.FLAG_BIGRAM_ATTR_OFFSET_NEGATIVE)
                        ? 1 : -1;
                int bigramAddress = baseAddress + readLength;
                switch (bigramFlags & FormatSpec.MASK_BIGRAM_ATTR_ADDRESS_TYPE) {
                    case FormatSpec.FLAG_BIGRAM_ATTR_ADDRESS_TYPE_ONEBYTE:
                        bigramAddress += sign * dictBuffer.readUnsignedByte();
                        readLength += 1;
                        break;
                    case FormatSpec.FLAG_BIGRAM_ATTR_ADDRESS_TYPE_TWOBYTES:
                        bigramAddress += sign * dictBuffer.readUnsignedShort();
                        readLength += 2;
                        break;
                    case FormatSpec.FLAG_BIGRAM_ATTR_ADDRESS_TYPE_THREEBYTES:
                        bigramAddress += sign * dictBuffer.readUnsignedInt24();
                        readLength += 3;
                        break;
                    default:
                        throw new RuntimeException("Has bigrams with no address");
                }
                bigrams.add(new PendingAttribute(
                        bigramFlags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_FREQUENCY,
                        bigramAddress));
                if (0 == (bigramFlags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_HAS_NEXT)) break;
            }
            return readLength;
        }
    }
}
