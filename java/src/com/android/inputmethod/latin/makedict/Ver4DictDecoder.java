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

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * An implementation of binary dictionary decoder for version 4 binary dictionary.
 */
@UsedForTesting
public class Ver4DictDecoder extends DictDecoder {
    private static final String TAG = Ver4DictDecoder.class.getSimpleName();

    private static final int FILETYPE_TRIE = 1;
    private static final int FILETYPE_FREQUENCY = 2;

    private final File mDictDirectory;
    private final DictionaryBufferFactory mBufferFactory;
    private DictBuffer mDictBuffer;
    private DictBuffer mFrequencyBuffer;

    @UsedForTesting
    /* package */ Ver4DictDecoder(final File dictDirectory, final int factoryFlag) {
        mDictDirectory = dictDirectory;
        mDictBuffer = mFrequencyBuffer = null;

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

    @UsedForTesting
    /* package */ Ver4DictDecoder(final File dictDirectory, final DictionaryBufferFactory factory) {
        mDictDirectory = dictDirectory;
        mBufferFactory = factory;
        mDictBuffer = mFrequencyBuffer = null;
    }

    private File getFile(final int fileType) {
        if (fileType == FILETYPE_TRIE) {
            return new File(mDictDirectory,
                    mDictDirectory.getName() + FormatSpec.TRIE_FILE_EXTENSION);
        } else if (fileType == FILETYPE_FREQUENCY) {
            return new File(mDictDirectory,
                    mDictDirectory.getName() + FormatSpec.FREQ_FILE_EXTENSION);
        } else {
            throw new RuntimeException("Unsupported kind of file : " + fileType);
        }
    }

    @Override
    public void openDictBuffer() throws FileNotFoundException, IOException {
        final String filename = mDictDirectory.getName();
        mDictBuffer = mBufferFactory.getDictionaryBuffer(getFile(FILETYPE_TRIE));
        mFrequencyBuffer = mBufferFactory.getDictionaryBuffer(getFile(FILETYPE_FREQUENCY));
    }

    @Override
    public boolean isDictBufferOpen() {
        return mDictBuffer != null;
    }

    /* package */ DictBuffer getDictBuffer() {
        return mDictBuffer;
    }

    @Override
    public FileHeader readHeader() throws IOException, UnsupportedFormatException {
        if (mDictBuffer == null) {
            openDictBuffer();
        }
        final FileHeader header = super.readHeader(mDictBuffer);
        final int version = header.mFormatOptions.mVersion;
        if (version != 4) {
            throw new UnsupportedFormatException("File header has a wrong version : " + version);
        }
        return header;
    }

    protected static class PtNodeReader extends DictDecoder.PtNodeReader {
        protected static int readFrequency(final DictBuffer frequencyBuffer, final int terminalId) {
            frequencyBuffer.position(terminalId * FormatSpec.FREQUENCY_AND_FLAGS_SIZE + 1);
            return frequencyBuffer.readUnsignedByte();
        }

        protected static int readTerminalId(final DictBuffer dictBuffer) {
            return dictBuffer.readInt();
        }
    }

    // TODO: Make this buffer thread safe.
    // TODO: Support words longer than FormatSpec.MAX_WORD_LENGTH.
    private final int[] mCharacterBuffer = new int[FormatSpec.MAX_WORD_LENGTH];
    @Override
    public PtNodeInfo readPtNode(int ptNodePos, FormatOptions options) {
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
            while (FormatSpec.INVALID_CHARACTER != character
                    && index < FormatSpec.MAX_WORD_LENGTH) {
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
        final int terminalId;
        if (0 != (FormatSpec.FLAG_IS_TERMINAL & flags)) {
            terminalId = PtNodeReader.readTerminalId(mDictBuffer);
            addressPointer += FormatSpec.PTNODE_TERMINAL_ID_SIZE;
        } else {
            terminalId = PtNode.NOT_A_TERMINAL;
        }

        final int frequency;
        if (0 != (FormatSpec.FLAG_IS_TERMINAL & flags)) {
            frequency = PtNodeReader.readFrequency(mFrequencyBuffer, terminalId);
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
                MakedictLog.d("too many bigrams in a node.");
            }
        } else {
            bigrams = null;
        }
        return new PtNodeInfo(ptNodePos, addressPointer, flags, characters, frequency,
                parentAddress, childrenAddress, shortcutTargets, bigrams);
    }

    private void deleteDictFiles() {
        final File[] files = mDictDirectory.listFiles();
        for (int i = 0; i < files.length; ++i) {
            files[i].delete();
        }
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
            Log.e(TAG, "The dictionary " + mDictDirectory.getName() + " is broken.", e);
            if (deleteDictIfBroken) {
                deleteDictFiles();
            }
            throw e;
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "The dictionary " + mDictDirectory.getName() + " is broken.", e);
            if (deleteDictIfBroken) {
                deleteDictFiles();
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
}
