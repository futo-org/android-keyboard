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
import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.CharEncoding;
import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.DictBuffer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * An implementation of DictDecoder for version 2 binary dictionary.
 */
// TODO: Separate logics that are used only for testing.
@UsedForTesting
public class Ver2DictDecoder extends AbstractDictDecoder {
    /**
     * A utility class for reading a PtNode.
     */
    protected static class PtNodeReader {
        private static ProbabilityInfo readProbabilityInfo(final DictBuffer dictBuffer) {
            // Ver2 dicts don't contain historical information.
            return new ProbabilityInfo(dictBuffer.readUnsignedByte());
        }

        protected static int readPtNodeOptionFlags(final DictBuffer dictBuffer) {
            return dictBuffer.readUnsignedByte();
        }

        protected static int readChildrenAddress(final DictBuffer dictBuffer,
                final int ptNodeFlags) {
            switch (ptNodeFlags & FormatSpec.MASK_CHILDREN_ADDRESS_TYPE) {
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

    protected final File mDictionaryBinaryFile;
    protected final long mOffset;
    protected final long mLength;
    // TODO: Remove mBufferFactory and mDictBuffer from this class members because they are now
    // used only for testing.
    private final DictionaryBufferFactory mBufferFactory;
    protected DictBuffer mDictBuffer;

    @UsedForTesting
    /* package */ Ver2DictDecoder(final File file, final long offset, final long length,
            final int factoryFlag) {
        mDictionaryBinaryFile = file;
        mOffset = offset;
        mLength = length;
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

    /* package */ Ver2DictDecoder(final File file, final long offset, final long length,
            final DictionaryBufferFactory factory) {
        mDictionaryBinaryFile = file;
        mOffset = offset;
        mLength = length;
        mBufferFactory = factory;
    }

    @Override
    public void openDictBuffer() throws FileNotFoundException, IOException {
        mDictBuffer = mBufferFactory.getDictionaryBuffer(mDictionaryBinaryFile);
    }

    @Override
    public boolean isDictBufferOpen() {
        return mDictBuffer != null;
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
    public DictionaryHeader readHeader() throws IOException, UnsupportedFormatException {
        // dictType is not being used in dicttool. Passing an empty string.
        final BinaryDictionary binaryDictionary = new BinaryDictionary(
                mDictionaryBinaryFile.getAbsolutePath(), mOffset, mLength,
                true /* useFullEditDistance */, null /* locale */, "" /* dictType */,
                false /* isUpdatable */);
        final DictionaryHeader header = binaryDictionary.getHeader();
        binaryDictionary.close();
        if (header == null) {
            throw new IOException("Cannot read the dictionary header.");
        }
        if (header.mFormatOptions.mVersion != FormatSpec.VERSION2) {
            throw new UnsupportedFormatException("File header has a wrong version : "
                    + header.mFormatOptions.mVersion);
        }
        if (!isDictBufferOpen()) {
            openDictBuffer();
        }
        // Advance buffer reading position to the head of dictionary body.
        setPosition(header.mBodyOffset);
        return header;
    }

    // TODO: Make this buffer multi thread safe.
    private final int[] mCharacterBuffer = new int[FormatSpec.MAX_WORD_LENGTH];
    @Override
    public PtNodeInfo readPtNode(final int ptNodePos) {
        int addressPointer = ptNodePos;
        final int flags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
        addressPointer += FormatSpec.PTNODE_FLAGS_SIZE;
        final int characters[];
        if (0 != (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS)) {
            int index = 0;
            int character = CharEncoding.readChar(mDictBuffer);
            addressPointer += CharEncoding.getCharSize(character);
            while (FormatSpec.INVALID_CHARACTER != character) {
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
        final ProbabilityInfo probabilityInfo;
        if (0 != (FormatSpec.FLAG_IS_TERMINAL & flags)) {
            probabilityInfo = PtNodeReader.readProbabilityInfo(mDictBuffer);
            addressPointer += FormatSpec.PTNODE_FREQUENCY_SIZE;
        } else {
            probabilityInfo = null;
        }
        int childrenAddress = PtNodeReader.readChildrenAddress(mDictBuffer, flags);
        if (childrenAddress != FormatSpec.NO_CHILDREN_ADDRESS) {
            childrenAddress += addressPointer;
        }
        addressPointer += BinaryDictIOUtils.getChildrenAddressSize(flags);
        final ArrayList<WeightedString> shortcutTargets;
        if (0 != (flags & FormatSpec.FLAG_HAS_SHORTCUT_TARGETS)) {
            // readShortcut will add shortcuts to shortcutTargets.
            shortcutTargets = new ArrayList<>();
            addressPointer += PtNodeReader.readShortcut(mDictBuffer, shortcutTargets);
        } else {
            shortcutTargets = null;
        }

        final ArrayList<PendingAttribute> bigrams;
        if (0 != (flags & FormatSpec.FLAG_HAS_BIGRAMS)) {
            bigrams = new ArrayList<>();
            addressPointer += PtNodeReader.readBigramAddresses(mDictBuffer, bigrams,
                    addressPointer);
            if (bigrams.size() >= FormatSpec.MAX_BIGRAMS_IN_A_PTNODE) {
                throw new RuntimeException("Too many bigrams in a PtNode (" + bigrams.size()
                        + " but max is " + FormatSpec.MAX_BIGRAMS_IN_A_PTNODE + ")");
            }
        } else {
            bigrams = null;
        }
        return new PtNodeInfo(ptNodePos, addressPointer, flags, characters, probabilityInfo,
                childrenAddress, shortcutTargets, bigrams);
    }

    @Override
    public FusionDictionary readDictionaryBinary(final boolean deleteDictIfBroken)
            throws FileNotFoundException, IOException, UnsupportedFormatException {
        // dictType is not being used in dicttool. Passing an empty string.
        final BinaryDictionary binaryDictionary = new BinaryDictionary(
                mDictionaryBinaryFile.getAbsolutePath(), 0 /* offset */,
                mDictionaryBinaryFile.length() /* length */, true /* useFullEditDistance */,
                null /* locale */, "" /* dictType */, false /* isUpdatable */);
        final DictionaryHeader header = readHeader();
        final FusionDictionary fusionDict =
                new FusionDictionary(new FusionDictionary.PtNodeArray(), header.mDictionaryOptions);
        int token = 0;
        final ArrayList<WordProperty> wordProperties = new ArrayList<>();
        do {
            final BinaryDictionary.GetNextWordPropertyResult result =
                    binaryDictionary.getNextWordProperty(token);
            final WordProperty wordProperty = result.mWordProperty;
            if (wordProperty == null) {
                binaryDictionary.close();
                if (deleteDictIfBroken) {
                    mDictionaryBinaryFile.delete();
                }
                return null;
            }
            wordProperties.add(wordProperty);
            token = result.mNextToken;
        } while (token != 0);

        // Insert unigrams into the fusion dictionary.
        for (final WordProperty wordProperty : wordProperties) {
            if (wordProperty.mIsBlacklistEntry) {
                fusionDict.addBlacklistEntry(wordProperty.mWord, wordProperty.mShortcutTargets,
                        wordProperty.mIsNotAWord);
            } else {
                fusionDict.add(wordProperty.mWord, wordProperty.mProbabilityInfo,
                        wordProperty.mShortcutTargets, wordProperty.mIsNotAWord);
            }
        }
        // Insert bigrams into the fusion dictionary.
        for (final WordProperty wordProperty : wordProperties) {
            if (wordProperty.mBigrams == null) {
                continue;
            }
            final String word0 = wordProperty.mWord;
            for (final WeightedString bigram : wordProperty.mBigrams) {
                fusionDict.setBigram(word0, bigram.mWord, bigram.mProbabilityInfo);
            }
        }
        binaryDictionary.close();
        return fusionDict;
    }

    @Override
    public void setPosition(int newPos) {
        mDictBuffer.position(newPos);
    }

    @Override
    public int getPosition() {
        return mDictBuffer.position();
    }

    @Override
    public int readPtNodeCount() {
        return BinaryDictDecoderUtils.readPtNodeCount(mDictBuffer);
    }
}
