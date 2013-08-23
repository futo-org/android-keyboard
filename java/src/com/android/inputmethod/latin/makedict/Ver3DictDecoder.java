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
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.utils.JniUtils;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * An implementation of DictDecoder for version 3 binary dictionary.
 */
@UsedForTesting
public class Ver3DictDecoder implements DictDecoder {
    private static final String TAG = Ver3DictDecoder.class.getSimpleName();

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

    private final static class PtNodeReader {
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

        protected static int readFrequency(final DictBuffer dictBuffer) {
            return dictBuffer.readUnsignedByte();
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

        protected static int readBigrams(final DictBuffer dictBuffer,
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
                        final int offset = (dictBuffer.readUnsignedByte() << 16)
                                + dictBuffer.readUnsignedShort();
                        bigramAddress += sign * offset;
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

    private final File mDictionaryBinaryFile;
    private final DictionaryBufferFactory mBufferFactory;
    private DictBuffer mDictBuffer;

    public Ver3DictDecoder(final File file) {
        this(file, USE_READONLY_BYTEBUFFER);
    }

    public Ver3DictDecoder(final File file, final int factoryFlag) {
        mDictionaryBinaryFile = file;
        mDictBuffer = null;

        if ((factoryFlag & MASK_DICTBUFFER) == USE_READONLY_BYTEBUFFER) {
            mBufferFactory = new DictionaryBufferFromReadOnlyByteBufferFactory();
        } else if ((factoryFlag  & MASK_DICTBUFFER) == USE_BYTEARRAY) {
            mBufferFactory = new DictionaryBufferFromByteArrayFactory();
        } else if ((factoryFlag & MASK_DICTBUFFER) == USE_WRITABLE_BYTEBUFFER) {
            mBufferFactory = new DictionaryBufferFromWritableByteBufferFactory();
        } else {
            mBufferFactory = new DictionaryBufferFromReadOnlyByteBufferFactory();
        }
    }

    public Ver3DictDecoder(final File file, final DictionaryBufferFactory factory) {
        mDictionaryBinaryFile = file;
        mBufferFactory = factory;
    }

    public void openDictBuffer() throws FileNotFoundException, IOException {
        mDictBuffer = mBufferFactory.getDictionaryBuffer(mDictionaryBinaryFile);
    }

    /* package */ DictBuffer getDictBuffer() {
        return mDictBuffer;
    }

    @UsedForTesting
    /* package */ DictBuffer openAndGetDictBuffer() throws FileNotFoundException, IOException {
        openDictBuffer();
        return getDictBuffer();
    }

    @Override
    public FileHeader readHeader() throws IOException, UnsupportedFormatException {
        if (mDictBuffer == null) {
            openDictBuffer();
        }

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

    // TODO: Make this buffer multi thread safe.
    private final int[] mCharacterBuffer = new int[FormatSpec.MAX_WORD_LENGTH];
    @Override
    public PtNodeInfo readPtNode(final int ptNodePos, final FormatOptions options) {
        int addressPointer = ptNodePos;
        final int flags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
        ++addressPointer;

        final int parentAddress = PtNodeReader.readParentAddress(mDictBuffer, options);
        if (BinaryDictIOUtils.supportsDynamicUpdate(options)) {
            addressPointer += 3;
        }

        final int characters[];
        if (0 != (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS)) {
            int index = 0;
            int character = CharEncoding.readChar(mDictBuffer);
            addressPointer += CharEncoding.getCharSize(character);
            while (-1 != character) {
                // FusionDictionary is making sure that the length of the word is smaller than
                // MAX_WORD_LENGTH.
                // So we'll never write past the end of mCharacterBuffer.
                mCharacterBuffer[index++] = character;
                character = CharEncoding.readChar(mDictBuffer);
                addressPointer += CharEncoding.getCharSize(character);
            }
            characters = Arrays.copyOfRange(mCharacterBuffer, 0, index);
        } else {
            final int character = CharEncoding.readChar(mDictBuffer);
            addressPointer += CharEncoding.getCharSize(character);
            characters = new int[] { character };
        }
        final int frequency;
        if (0 != (FormatSpec.FLAG_IS_TERMINAL & flags)) {
            ++addressPointer;
            frequency = PtNodeReader.readFrequency(mDictBuffer);
        } else {
            frequency = PtNode.NOT_A_TERMINAL;
        }
        int childrenAddress = PtNodeReader.readChildrenAddress(mDictBuffer, flags, options);
        if (childrenAddress != FormatSpec.NO_CHILDREN_ADDRESS) {
            childrenAddress += addressPointer;
        }
        addressPointer += BinaryDictIOUtils.getChildrenAddressSize(flags, options);
        final ArrayList<WeightedString> shortcutTargets;
        if (0 != (flags & FormatSpec.FLAG_HAS_SHORTCUT_TARGETS)) {
            // readShortcut will add shortcuts to shortcutTargets.
            shortcutTargets = new ArrayList<WeightedString>();
            addressPointer += PtNodeReader.readShortcut(mDictBuffer, shortcutTargets);
        } else {
            shortcutTargets = null;
        }

        final ArrayList<PendingAttribute> bigrams;
        if (0 != (flags & FormatSpec.FLAG_HAS_BIGRAMS)) {
            bigrams = new ArrayList<PendingAttribute>();
            addressPointer += PtNodeReader.readBigrams(mDictBuffer, bigrams, addressPointer);
            if (bigrams.size() >= FormatSpec.MAX_BIGRAMS_IN_A_PTNODE) {
                MakedictLog.d("too many bigrams in a PtNode.");
            }
        } else {
            bigrams = null;
        }
        return new PtNodeInfo(ptNodePos, addressPointer, flags, characters, frequency,
                parentAddress, childrenAddress, shortcutTargets, bigrams);
    }

    @Override
    public FusionDictionary readDictionaryBinary(final FusionDictionary dict)
            throws FileNotFoundException, IOException, UnsupportedFormatException {
        if (mDictBuffer == null) {
            openDictBuffer();
        }
        try {
            return BinaryDictDecoderUtils.readDictionaryBinary(this, dict);
        } catch (IOException e) {
            Log.e(TAG, "The dictionary " + mDictionaryBinaryFile.getName() + " is broken.", e);
            if (!mDictionaryBinaryFile.delete()) {
                Log.e(TAG, "Failed to delete the broken dictionary.");
            }
            throw e;
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "The dictionary " + mDictionaryBinaryFile.getName() + " is broken.", e);
            if (!mDictionaryBinaryFile.delete()) {
                Log.e(TAG, "Failed to delete the broken dictionary.");
            }
            throw e;
        }
    }

    @Override
    public int getTerminalPosition(String word) throws IOException, UnsupportedFormatException {
        if (mDictBuffer == null) {
            openDictBuffer();
        }
        return BinaryDictIOUtils.getTerminalPosition(this, word);
    }

    @Override
    public void readUnigramsAndBigramsBinary(final TreeMap<Integer, String> words,
            final TreeMap<Integer, Integer> frequencies,
            final TreeMap<Integer, ArrayList<PendingAttribute>> bigrams)
            throws IOException, UnsupportedFormatException {
        if (mDictBuffer == null) {
            openDictBuffer();
        }
        BinaryDictIOUtils.readUnigramsAndBigramsBinary(this, words, frequencies, bigrams);
    }

}
