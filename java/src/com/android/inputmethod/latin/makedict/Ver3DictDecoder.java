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

/**
 * An implementation of DictDecoder for version 3 binary dictionary.
 */
@UsedForTesting
public class Ver3DictDecoder extends AbstractDictDecoder {
    private static final String TAG = Ver3DictDecoder.class.getSimpleName();

    static {
        JniUtils.loadNativeLibrary();
    }

    // TODO: implement something sensical instead of just a phony method
    private static native int doNothing();

    protected static class PtNodeReader extends AbstractDictDecoder.PtNodeReader {
        private static int readFrequency(final DictBuffer dictBuffer) {
            return dictBuffer.readUnsignedByte();
        }
    }

    protected final File mDictionaryBinaryFile;
    private final DictionaryBufferFactory mBufferFactory;
    protected DictBuffer mDictBuffer;

    /* package */ Ver3DictDecoder(final File file, final int factoryFlag) {
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

    /* package */ Ver3DictDecoder(final File file, final DictionaryBufferFactory factory) {
        mDictionaryBinaryFile = file;
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
    public FileHeader readHeader() throws IOException, UnsupportedFormatException {
        if (mDictBuffer == null) {
            openDictBuffer();
        }
        final FileHeader header = super.readHeader(mDictBuffer);
        final int version = header.mFormatOptions.mVersion;
        if (!(version >= 2 && version <= 3)) {
          throw new UnsupportedFormatException("File header has a wrong version : " + version);
        }
        return header;
    }

    // TODO: Make this buffer multi thread safe.
    private final int[] mCharacterBuffer = new int[FormatSpec.MAX_WORD_LENGTH];
    @Override
    public PtNodeInfo readPtNode(final int ptNodePos, final FormatOptions options) {
        int addressPointer = ptNodePos;
        final int flags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
        addressPointer += FormatSpec.PTNODE_FLAGS_SIZE;

        final int parentAddress = PtNodeReader.readParentAddress(mDictBuffer, options);
        if (BinaryDictIOUtils.supportsDynamicUpdate(options)) {
            addressPointer += FormatSpec.PARENT_ADDRESS_SIZE;
        }

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
        final int frequency;
        if (0 != (FormatSpec.FLAG_IS_TERMINAL & flags)) {
            frequency = PtNodeReader.readFrequency(mDictBuffer);
            addressPointer += FormatSpec.PTNODE_FREQUENCY_SIZE;
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
            addressPointer += PtNodeReader.readBigramAddresses(mDictBuffer, bigrams, 
                    addressPointer);
            if (bigrams.size() >= FormatSpec.MAX_BIGRAMS_IN_A_PTNODE) {
                throw new RuntimeException("Too many bigrams in a PtNode (" + bigrams.size()
                        + " but max is " + FormatSpec.MAX_BIGRAMS_IN_A_PTNODE + ")");
            }
        } else {
            bigrams = null;
        }
        return new PtNodeInfo(ptNodePos, addressPointer, flags, characters, frequency,
                parentAddress, childrenAddress, shortcutTargets, bigrams);
    }

    @Override
    public FusionDictionary readDictionaryBinary(final FusionDictionary dict,
            final boolean deleteDictIfBroken)
            throws FileNotFoundException, IOException, UnsupportedFormatException {
        if (mDictBuffer == null) {
            openDictBuffer();
        }
        try {
            return BinaryDictDecoderUtils.readDictionaryBinary(this, dict);
        } catch (IOException e) {
            Log.e(TAG, "The dictionary " + mDictionaryBinaryFile.getName() + " is broken.", e);
            if (deleteDictIfBroken && !mDictionaryBinaryFile.delete()) {
                Log.e(TAG, "Failed to delete the broken dictionary.");
            }
            throw e;
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "The dictionary " + mDictionaryBinaryFile.getName() + " is broken.", e);
            if (deleteDictIfBroken && !mDictionaryBinaryFile.delete()) {
                Log.e(TAG, "Failed to delete the broken dictionary.");
            }
            throw e;
        }
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

    @Override
    public boolean readAndFollowForwardLink() {
        final int nextAddress = mDictBuffer.readUnsignedInt24();
        if (nextAddress >= 0 && nextAddress < mDictBuffer.limit()) {
            mDictBuffer.position(nextAddress);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasNextPtNodeArray() {
        return mDictBuffer.position() != FormatSpec.NO_FORWARD_LINK_ADDRESS;
    }

    @Override
    public void skipPtNode(final FormatOptions formatOptions) {
        final int flags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
        PtNodeReader.readParentAddress(mDictBuffer, formatOptions);
        BinaryDictIOUtils.skipString(mDictBuffer,
                (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS) != 0);
        PtNodeReader.readChildrenAddress(mDictBuffer, flags, formatOptions);
        if ((flags & FormatSpec.FLAG_IS_TERMINAL) != 0) PtNodeReader.readFrequency(mDictBuffer);
        if ((flags & FormatSpec.FLAG_HAS_SHORTCUT_TARGETS) != 0) {
            final int shortcutsSize = mDictBuffer.readUnsignedShort();
            mDictBuffer.position(mDictBuffer.position() + shortcutsSize
                    - FormatSpec.PTNODE_SHORTCUT_LIST_SIZE_SIZE);
        }
        if ((flags & FormatSpec.FLAG_HAS_BIGRAMS) != 0) {
            int bigramCount = 0;
            while (bigramCount++ < FormatSpec.MAX_BIGRAMS_IN_A_PTNODE) {
                final int bigramFlags = mDictBuffer.readUnsignedByte();
                switch (bigramFlags & FormatSpec.MASK_BIGRAM_ATTR_ADDRESS_TYPE) {
                    case FormatSpec.FLAG_BIGRAM_ATTR_ADDRESS_TYPE_ONEBYTE:
                        mDictBuffer.readUnsignedByte();
                        break;
                    case FormatSpec.FLAG_BIGRAM_ATTR_ADDRESS_TYPE_TWOBYTES:
                        mDictBuffer.readUnsignedShort();
                        break;
                    case FormatSpec.FLAG_BIGRAM_ATTR_ADDRESS_TYPE_THREEBYTES:
                        mDictBuffer.readUnsignedInt24();
                        break;
                }
                if ((bigramFlags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_HAS_NEXT) == 0) break;
            }
            if (bigramCount >= FormatSpec.MAX_BIGRAMS_IN_A_PTNODE) {
                throw new RuntimeException("Too many bigrams in a PtNode.");
            }
        }
    }
}
