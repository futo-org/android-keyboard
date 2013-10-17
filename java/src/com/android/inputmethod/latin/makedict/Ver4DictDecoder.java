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
import com.android.inputmethod.latin.utils.CollectionUtils;

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
public class Ver4DictDecoder extends AbstractDictDecoder {
    private static final String TAG = Ver4DictDecoder.class.getSimpleName();

    private static final int FILETYPE_TRIE = 1;
    private static final int FILETYPE_FREQUENCY = 2;
    private static final int FILETYPE_TERMINAL_ADDRESS_TABLE = 3;
    private static final int FILETYPE_BIGRAM_FREQ = 4;
    private static final int FILETYPE_SHORTCUT = 5;

    private final File mDictDirectory;
    private final DictionaryBufferFactory mBufferFactory;
    protected DictBuffer mDictBuffer;
    private DictBuffer mFrequencyBuffer;
    private DictBuffer mTerminalAddressTableBuffer;
    private BigramContentReader mBigramReader;
    private ShortcutContentReader mShortcutReader;

    /**
     * Raw PtNode info straight out of a trie file in version 4 dictionary.
     */
    protected static final class Ver4PtNodeInfo {
        public final int mFlags;
        public final int[] mCharacters;
        public final int mTerminalId;
        public final int mChildrenPos;
        public final int mParentPos;
        public final int mNodeSize;

        public Ver4PtNodeInfo(final int flags, final int[] characters, final int terminalId,
                final int childrenPos, final int parentPos, final int nodeSize) {
            mFlags = flags;
            mCharacters = characters;
            mTerminalId = terminalId;
            mChildrenPos = childrenPos;
            mParentPos = parentPos;
            mNodeSize = nodeSize;
        }
    }

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
        } else if (fileType == FILETYPE_TERMINAL_ADDRESS_TABLE) {
            return new File(mDictDirectory,
                    mDictDirectory.getName() + FormatSpec.TERMINAL_ADDRESS_TABLE_FILE_EXTENSION);
        } else if (fileType == FILETYPE_BIGRAM_FREQ) {
            return new File(mDictDirectory,
                    mDictDirectory.getName() + FormatSpec.BIGRAM_FILE_EXTENSION
                            + FormatSpec.BIGRAM_FREQ_CONTENT_ID);
        } else if (fileType == FILETYPE_SHORTCUT) {
            return new File(mDictDirectory,
                    mDictDirectory.getName() + FormatSpec.SHORTCUT_FILE_EXTENSION
                            + FormatSpec.SHORTCUT_CONTENT_ID);
        } else {
            throw new RuntimeException("Unsupported kind of file : " + fileType);
        }
    }

    @Override
    public void openDictBuffer() throws FileNotFoundException, IOException {
        mDictBuffer = mBufferFactory.getDictionaryBuffer(getFile(FILETYPE_TRIE));
        mFrequencyBuffer = mBufferFactory.getDictionaryBuffer(getFile(FILETYPE_FREQUENCY));
        mTerminalAddressTableBuffer = mBufferFactory.getDictionaryBuffer(
                getFile(FILETYPE_TERMINAL_ADDRESS_TABLE));
        mBigramReader = new BigramContentReader(mDictDirectory.getName(),
                mDictDirectory, mBufferFactory, false);
        mBigramReader.openBuffers();
        mShortcutReader = new ShortcutContentReader(mDictDirectory.getName(), mDictDirectory,
                mBufferFactory);
        mShortcutReader.openBuffers();
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

    /**
     * An auxiliary class for reading bigrams.
     */
    protected static class BigramContentReader extends SparseTableContentReader {
        private final boolean mHasTimestamp;

        public BigramContentReader(final String name, final File baseDir,
                final DictionaryBufferFactory factory, final boolean hasTimestamp) {
            super(name + FormatSpec.BIGRAM_FILE_EXTENSION,
                    FormatSpec.BIGRAM_ADDRESS_TABLE_BLOCK_SIZE, baseDir,
                    getContentFilenames(name, hasTimestamp), getContentIds(hasTimestamp), factory);
            mHasTimestamp = hasTimestamp;
        }

        // TODO: Consolidate this method and BigramContentWriter.getContentFilenames.
        private static String[] getContentFilenames(final String name, final boolean hasTimestamp) {
            final String[] contentFilenames;
            if (hasTimestamp) {
                contentFilenames = new String[] { name + FormatSpec.BIGRAM_FILE_EXTENSION,
                        name + FormatSpec.BIGRAM_FILE_EXTENSION };
            } else {
                contentFilenames = new String[] { name + FormatSpec.BIGRAM_FILE_EXTENSION };
            }
            return contentFilenames;
        }

        // TODO: Consolidate this method and BigramContentWriter.getContentIds.
        private static String[] getContentIds(final boolean hasTimestamp) {
            final String[] contentIds;
            if (hasTimestamp) {
                contentIds = new String[] { FormatSpec.BIGRAM_FREQ_CONTENT_ID,
                        FormatSpec.BIGRAM_TIMESTAMP_CONTENT_ID };
            } else {
                contentIds = new String[] { FormatSpec.BIGRAM_FREQ_CONTENT_ID };
            }
            return contentIds;
        }

        public ArrayList<PendingAttribute> readTargetsAndFrequencies(final int terminalId,
                final DictBuffer terminalAddressTableBuffer) {
            final ArrayList<PendingAttribute> bigrams = CollectionUtils.newArrayList();
            read(FormatSpec.BIGRAM_FREQ_CONTENT_INDEX, terminalId,
                    new SparseTableContentReaderInterface() {
                        @Override
                        public void read(final DictBuffer buffer) {
                            while (bigrams.size() < FormatSpec.MAX_BIGRAMS_IN_A_PTNODE) {
                                // If bigrams.size() reaches FormatSpec.MAX_BIGRAMS_IN_A_PTNODE,
                                // remaining bigram entries are ignored.
                                final int bigramFlags = buffer.readUnsignedByte();
                                final int targetTerminalId = buffer.readUnsignedInt24();
                                terminalAddressTableBuffer.position(targetTerminalId
                                        * FormatSpec.TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE);
                                final int targetAddress =
                                        terminalAddressTableBuffer.readUnsignedInt24();
                                bigrams.add(new PendingAttribute(bigramFlags
                                        & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_FREQUENCY,
                                        targetAddress));
                                if (0 == (bigramFlags
                                        & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_HAS_NEXT)) {
                                    break;
                                }
                            }
                            if (bigrams.size() >= FormatSpec.MAX_BIGRAMS_IN_A_PTNODE) {
                                throw new RuntimeException("Too many bigrams in a PtNode ("
                                        + bigrams.size() + " but max is "
                                        + FormatSpec.MAX_BIGRAMS_IN_A_PTNODE + ")");
                            }
                        }
                    });
            if (bigrams.isEmpty()) return null;
            return bigrams;
        }
    }

    /**
     * An auxiliary class for reading shortcuts.
     */
    protected static class ShortcutContentReader extends SparseTableContentReader {
        public ShortcutContentReader(final String name, final File baseDir,
                final DictionaryBufferFactory factory) {
            super(name + FormatSpec.SHORTCUT_FILE_EXTENSION,
                    FormatSpec.SHORTCUT_ADDRESS_TABLE_BLOCK_SIZE, baseDir,
                    new String[] { name + FormatSpec.SHORTCUT_FILE_EXTENSION },
                    new String[] { FormatSpec.SHORTCUT_CONTENT_ID }, factory);
        }

        public ArrayList<WeightedString> readShortcuts(final int terminalId) {
            final ArrayList<WeightedString> shortcuts = CollectionUtils.newArrayList();
            read(FormatSpec.SHORTCUT_CONTENT_INDEX, terminalId,
                    new SparseTableContentReaderInterface() {
                        @Override
                        public void read(final DictBuffer buffer) {
                            while (true) {
                                final int flags = buffer.readUnsignedByte();
                                final String word = CharEncoding.readString(buffer);
                                shortcuts.add(new WeightedString(word,
                                        flags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_FREQUENCY));
                                if (0 == (flags & FormatSpec.FLAG_BIGRAM_SHORTCUT_ATTR_HAS_NEXT)) {
                                    break;
                                }
                            }
                        }
                    });
            if (shortcuts.isEmpty()) return null;
            return shortcuts;
        }
    }

    protected static class PtNodeReader extends AbstractDictDecoder.PtNodeReader {
        protected static int readFrequency(final DictBuffer frequencyBuffer, final int terminalId) {
            frequencyBuffer.position(terminalId * FormatSpec.FREQUENCY_AND_FLAGS_SIZE + 1);
            return frequencyBuffer.readUnsignedByte();
        }

        protected static int readTerminalId(final DictBuffer dictBuffer) {
            return dictBuffer.readInt();
        }
    }

    private final int[] mCharacterBufferForReadingVer4PtNodeInfo
            = new int[FormatSpec.MAX_WORD_LENGTH];

    /**
     * Reads PtNode from ptNodePos in the trie file and returns Ver4PtNodeInfo.
     *
     * @param ptNodePos the position of PtNode.
     * @param options the format options.
     * @return Ver4PtNodeInfo.
     */
    // TODO: Make this buffer thread safe.
    // TODO: Support words longer than FormatSpec.MAX_WORD_LENGTH.
    protected Ver4PtNodeInfo readVer4PtNodeInfo(final int ptNodePos, final FormatOptions options) {
        int readingPos = ptNodePos;
        final int flags = PtNodeReader.readPtNodeOptionFlags(mDictBuffer);
        readingPos += FormatSpec.PTNODE_FLAGS_SIZE;

        final int parentPos = PtNodeReader.readParentAddress(mDictBuffer, options);
        if (BinaryDictIOUtils.supportsDynamicUpdate(options)) {
            readingPos += FormatSpec.PARENT_ADDRESS_SIZE;
        }

        final int characters[];
        if (0 != (flags & FormatSpec.FLAG_HAS_MULTIPLE_CHARS)) {
            int index = 0;
            int character = CharEncoding.readChar(mDictBuffer);
            readingPos += CharEncoding.getCharSize(character);
            while (FormatSpec.INVALID_CHARACTER != character
                    && index < FormatSpec.MAX_WORD_LENGTH) {
                mCharacterBufferForReadingVer4PtNodeInfo[index++] = character;
                character = CharEncoding.readChar(mDictBuffer);
                readingPos += CharEncoding.getCharSize(character);
            }
            characters = Arrays.copyOfRange(mCharacterBufferForReadingVer4PtNodeInfo, 0, index);
        } else {
            final int character = CharEncoding.readChar(mDictBuffer);
            readingPos += CharEncoding.getCharSize(character);
            characters = new int[] { character };
        }
        final int terminalId;
        if (0 != (FormatSpec.FLAG_IS_TERMINAL & flags)) {
            terminalId = PtNodeReader.readTerminalId(mDictBuffer);
            readingPos += FormatSpec.PTNODE_TERMINAL_ID_SIZE;
        } else {
            terminalId = PtNode.NOT_A_TERMINAL;
        }

        int childrenPos = PtNodeReader.readChildrenAddress(mDictBuffer, flags, options);
        if (childrenPos != FormatSpec.NO_CHILDREN_ADDRESS) {
            childrenPos += readingPos;
        }
        readingPos += BinaryDictIOUtils.getChildrenAddressSize(flags, options);

        return new Ver4PtNodeInfo(flags, characters, terminalId, childrenPos, parentPos,
                readingPos - ptNodePos);
    }

    @Override
    public PtNodeInfo readPtNode(int ptNodePos, FormatOptions options) {
        final Ver4PtNodeInfo nodeInfo = readVer4PtNodeInfo(ptNodePos, options);

        final int frequency;
        if (0 != (FormatSpec.FLAG_IS_TERMINAL & nodeInfo.mFlags)) {
            frequency = PtNodeReader.readFrequency(mFrequencyBuffer, nodeInfo.mTerminalId);
        } else {
            frequency = PtNode.NOT_A_TERMINAL;
        }

        final ArrayList<WeightedString> shortcutTargets = mShortcutReader.readShortcuts(
                nodeInfo.mTerminalId);
        final ArrayList<PendingAttribute> bigrams = mBigramReader.readTargetsAndFrequencies(
                nodeInfo.mTerminalId, mTerminalAddressTableBuffer);

        return new PtNodeInfo(ptNodePos, ptNodePos + nodeInfo.mNodeSize, nodeInfo.mFlags,
                nodeInfo.mCharacters, frequency, nodeInfo.mParentPos, nodeInfo.mChildrenPos,
                shortcutTargets, bigrams);
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
        final int forwardLinkPos = mDictBuffer.position();
        int nextRelativePos = BinaryDictDecoderUtils.readSInt24(mDictBuffer);
        if (nextRelativePos != FormatSpec.NO_FORWARD_LINK_ADDRESS) {
            final int nextPos = forwardLinkPos + nextRelativePos;
            if (nextPos >= 0 && nextPos < mDictBuffer.limit()) {
              mDictBuffer.position(nextPos);
              return true;
            }
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
        if ((flags & FormatSpec.FLAG_IS_TERMINAL) != 0) PtNodeReader.readTerminalId(mDictBuffer);
        PtNodeReader.readChildrenAddress(mDictBuffer, flags, formatOptions);
    }
}
