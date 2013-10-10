/*
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
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * An implementation of DictEncoder for version 4 binary dictionary.
 */
@UsedForTesting
public class Ver4DictEncoder implements DictEncoder {
    private final File mDictPlacedDir;
    private byte[] mTrieBuf;
    private int mTriePos;
    private int mHeaderSize;
    private OutputStream mTrieOutStream;
    private OutputStream mFreqOutStream;
    private OutputStream mTerminalAddressTableOutStream;
    private File mDictDir;
    private String mBaseFilename;
    private BigramContentWriter mBigramWriter;
    private ShortcutContentWriter mShortcutWriter;

    @UsedForTesting
    public Ver4DictEncoder(final File dictPlacedDir) {
        mDictPlacedDir = dictPlacedDir;
    }

    private interface SparseTableContentWriterInterface {
        public void write(final OutputStream outStream) throws IOException;
    }

    private static class SparseTableContentWriter {
        private final int mContentCount;
        private final SparseTable mSparseTable;
        private final File mLookupTableFile;
        protected final File mBaseDir;
        private final File[] mAddressTableFiles;
        private final File[] mContentFiles;
        protected final OutputStream[] mContentOutStreams;

        public SparseTableContentWriter(final String name, final int contentCount,
                final int initialCapacity, final int blockSize, final File baseDir,
                final String[] contentFilenames, final String[] contentIds) {
            if (contentFilenames.length != contentIds.length) {
                throw new RuntimeException("The length of contentFilenames and the length of"
                        + " contentIds are different " + contentFilenames.length + ", "
                        + contentIds.length);
            }
            mContentCount = contentCount;
            mSparseTable = new SparseTable(initialCapacity, blockSize, contentCount);
            mLookupTableFile = new File(baseDir, name + FormatSpec.LOOKUP_TABLE_FILE_SUFFIX);
            mAddressTableFiles = new File[mContentCount];
            mContentFiles = new File[mContentCount];
            mBaseDir = baseDir;
            for (int i = 0; i < mContentCount; ++i) {
                mAddressTableFiles[i] = new File(mBaseDir,
                        name + FormatSpec.CONTENT_TABLE_FILE_SUFFIX + contentIds[i]);
                mContentFiles[i] = new File(mBaseDir, contentFilenames[i] + contentIds[i]);
            }
            mContentOutStreams = new OutputStream[mContentCount];
        }

        public void openStreams() throws FileNotFoundException {
            for (int i = 0; i < mContentCount; ++i) {
                mContentOutStreams[i] = new FileOutputStream(mContentFiles[i]);
            }
        }

        protected void write(final int contentIndex, final int index,
                final SparseTableContentWriterInterface writer) throws IOException {
            mSparseTable.set(contentIndex, index, (int) mContentFiles[contentIndex].length());
            writer.write(mContentOutStreams[contentIndex]);
            mContentOutStreams[contentIndex].flush();
        }

        public void closeStreams() throws IOException {
            mSparseTable.writeToFiles(mLookupTableFile, mAddressTableFiles);
            for (int i = 0; i < mContentCount; ++i) {
                mContentOutStreams[i].close();
            }
        }
    }

    private static class BigramContentWriter extends SparseTableContentWriter {

        public BigramContentWriter(final String name, final int initialCapacity,
                final File baseDir) {
            super(name + FormatSpec.BIGRAM_FILE_EXTENSION, FormatSpec.BIGRAM_CONTENT_COUNT,
                    initialCapacity, FormatSpec.BIGRAM_ADDRESS_TABLE_BLOCK_SIZE, baseDir,
                    new String[] { name + FormatSpec.BIGRAM_FILE_EXTENSION },
                    new String[] { FormatSpec.BIGRAM_FREQ_CONTENT_ID });
        }

        public void writeBigramsForOneWord(final int terminalId,
                final Iterator<WeightedString> bigramIterator, final FusionDictionary dict)
                        throws IOException {
            write(FormatSpec.BIGRAM_FREQ_CONTENT_INDEX, terminalId,
                    new SparseTableContentWriterInterface() {
                        @Override
                        public void write(final OutputStream outStream) throws IOException {
                            writeBigramsForOneWordInternal(outStream, bigramIterator, dict);
                        }
            });
        }

        private void writeBigramsForOneWordInternal(final OutputStream outStream,
                final Iterator<WeightedString> bigramIterator, final FusionDictionary dict)
                        throws IOException {
            while (bigramIterator.hasNext()) {
                final WeightedString bigram = bigramIterator.next();
                final PtNode target =
                        FusionDictionary.findWordInTree(dict.mRootNodeArray, bigram.mWord);
                final int unigramFrequencyForThisWord = target.mFrequency;
                final int bigramFlags = BinaryDictEncoderUtils.makeBigramFlags(
                        bigramIterator.hasNext(), 0, bigram.mFrequency,
                        unigramFrequencyForThisWord, bigram.mWord);
                BinaryDictEncoderUtils.writeUIntToStream(outStream, bigramFlags,
                        FormatSpec.PTNODE_ATTRIBUTE_FLAGS_SIZE);
                BinaryDictEncoderUtils.writeUIntToStream(outStream, target.mTerminalId,
                        FormatSpec.PTNODE_ATTRIBUTE_MAX_ADDRESS_SIZE);
            }
        }
    }

    private static class ShortcutContentWriter extends SparseTableContentWriter {
        public ShortcutContentWriter(final String name, final int initialCapacity,
                final File baseDir) {
            super(name + FormatSpec.SHORTCUT_FILE_EXTENSION, FormatSpec.SHORTCUT_CONTENT_COUNT,
                    initialCapacity, FormatSpec.SHORTCUT_ADDRESS_TABLE_BLOCK_SIZE, baseDir,
                    new String[] { name + FormatSpec.SHORTCUT_FILE_EXTENSION },
                    new String[] { FormatSpec.SHORTCUT_CONTENT_ID });
        }

        public void writeShortcutForOneWord(final int terminalId,
                final Iterator<WeightedString> shortcutIterator) throws IOException {
            write(FormatSpec.SHORTCUT_CONTENT_INDEX, terminalId,
                    new SparseTableContentWriterInterface() {
                        @Override
                        public void write(final OutputStream outStream) throws IOException {
                            writeShortcutForOneWordInternal(outStream, shortcutIterator);
                        }
                    });
        }

        private void writeShortcutForOneWordInternal(final OutputStream outStream,
                final Iterator<WeightedString> shortcutIterator) throws IOException {
            while (shortcutIterator.hasNext()) {
                final WeightedString target = shortcutIterator.next();
                final int shortcutFlags = BinaryDictEncoderUtils.makeShortcutFlags(
                        shortcutIterator.hasNext(), target.mFrequency);
                BinaryDictEncoderUtils.writeUIntToStream(outStream, shortcutFlags,
                        FormatSpec.PTNODE_ATTRIBUTE_FLAGS_SIZE);
                CharEncoding.writeString(outStream, target.mWord);
            }
        }
    }

    private void openStreams(final FormatOptions formatOptions, final DictionaryOptions dictOptions)
            throws FileNotFoundException, IOException {
        final FileHeader header = new FileHeader(0, dictOptions, formatOptions);
        mBaseFilename = header.getId() + "." + header.getVersion();
        mDictDir = new File(mDictPlacedDir, mBaseFilename);
        final File trieFile = new File(mDictDir, mBaseFilename + FormatSpec.TRIE_FILE_EXTENSION);
        final File freqFile = new File(mDictDir, mBaseFilename + FormatSpec.FREQ_FILE_EXTENSION);
        final File terminalAddressTableFile = new File(mDictDir,
                mBaseFilename + FormatSpec.TERMINAL_ADDRESS_TABLE_FILE_EXTENSION);
        if (!mDictDir.isDirectory()) {
            if (mDictDir.exists()) mDictDir.delete();
            mDictDir.mkdirs();
        }
        if (!trieFile.exists()) trieFile.createNewFile();
        if (!freqFile.exists()) freqFile.createNewFile();
        if (!terminalAddressTableFile.exists()) terminalAddressTableFile.createNewFile();
        mTrieOutStream = new FileOutputStream(trieFile);
        mFreqOutStream = new FileOutputStream(freqFile);
        mTerminalAddressTableOutStream = new FileOutputStream(terminalAddressTableFile);
    }

    private void close() throws IOException {
        try {
            if (mTrieOutStream != null) {
                mTrieOutStream.close();
            }
            if (mFreqOutStream != null) {
                mFreqOutStream.close();
            }
            if (mTerminalAddressTableOutStream != null) {
                mTerminalAddressTableOutStream.close();
            }
        } finally {
            mTrieOutStream = null;
            mFreqOutStream = null;
            mTerminalAddressTableOutStream = null;
        }
    }

    @Override
    public void writeDictionary(final FusionDictionary dict, final FormatOptions formatOptions)
            throws IOException, UnsupportedFormatException {
        if (formatOptions.mVersion != FormatSpec.VERSION4) {
            throw new UnsupportedFormatException("File header has a wrong version number : "
                    + formatOptions.mVersion);
        }
        if (!mDictPlacedDir.isDirectory()) {
            throw new UnsupportedFormatException("Given path is not a directory.");
        }

        if (mTrieOutStream == null) {
            openStreams(formatOptions, dict.mOptions);
        }

        mHeaderSize = BinaryDictEncoderUtils.writeDictionaryHeader(mTrieOutStream, dict,
                formatOptions);

        MakedictLog.i("Flattening the tree...");
        ArrayList<PtNodeArray> flatNodes = BinaryDictEncoderUtils.flattenTree(dict.mRootNodeArray);
        int terminalCount = 0;
        for (final PtNodeArray array : flatNodes) {
            for (final PtNode node : array.mData) {
                if (node.isTerminal()) node.mTerminalId = terminalCount++;
            }
        }

        MakedictLog.i("Computing addresses...");
        BinaryDictEncoderUtils.computeAddresses(dict, flatNodes, formatOptions);
        if (MakedictLog.DBG) BinaryDictEncoderUtils.checkFlatPtNodeArrayList(flatNodes);

        writeTerminalData(flatNodes, terminalCount);
        mBigramWriter = new BigramContentWriter(mBaseFilename, terminalCount, mDictDir);
        writeBigrams(flatNodes, dict);
        mShortcutWriter = new ShortcutContentWriter(mBaseFilename, terminalCount, mDictDir);
        writeShortcuts(flatNodes);

        final PtNodeArray lastNodeArray = flatNodes.get(flatNodes.size() - 1);
        final int bufferSize = lastNodeArray.mCachedAddressAfterUpdate + lastNodeArray.mCachedSize;
        mTrieBuf = new byte[bufferSize];

        MakedictLog.i("Writing file...");
        for (PtNodeArray nodeArray : flatNodes) {
            BinaryDictEncoderUtils.writePlacedPtNodeArray(dict, this, nodeArray, formatOptions);
        }
        if (MakedictLog.DBG) {
            BinaryDictEncoderUtils.showStatistics(flatNodes);
            MakedictLog.i("has " + terminalCount + " terminals.");
        }
        mTrieOutStream.write(mTrieBuf);

        MakedictLog.i("Done");
        close();
    }

    @Override
    public void setPosition(int position) {
        if (mTrieBuf == null || position < 0 || position >- mTrieBuf.length) return;
        mTriePos = position;
    }

    @Override
    public int getPosition() {
        return mTriePos;
    }

    @Override
    public void writePtNodeCount(int ptNodeCount) {
        final int countSize = BinaryDictIOUtils.getPtNodeCountSize(ptNodeCount);
        // ptNodeCount must fit on one byte or two bytes.
        // Please see comments in FormatSpec
        if (countSize != 1 && countSize != 2) {
            throw new RuntimeException("Strange size from getPtNodeCountSize : " + countSize);
        }
        mTriePos = BinaryDictEncoderUtils.writeUIntToBuffer(mTrieBuf, mTriePos, ptNodeCount,
                countSize);
    }

    private void writePtNodeFlags(final PtNode ptNode, final FormatOptions formatOptions) {
        final int childrenPos = BinaryDictEncoderUtils.getChildrenPosition(ptNode, formatOptions);
        mTriePos = BinaryDictEncoderUtils.writeUIntToBuffer(mTrieBuf, mTriePos,
                BinaryDictEncoderUtils.makePtNodeFlags(ptNode, childrenPos, formatOptions),
                FormatSpec.PTNODE_FLAGS_SIZE);
    }

    private void writeParentPosition(int parentPos, final PtNode ptNode,
            final FormatOptions formatOptions) {
        if (parentPos != FormatSpec.NO_PARENT_ADDRESS) {
            parentPos -= ptNode.mCachedAddressAfterUpdate;
        }
        mTriePos = BinaryDictEncoderUtils.writeParentAddress(mTrieBuf, mTriePos, parentPos,
                formatOptions);
    }

    private void writeCharacters(final int[] characters, final boolean hasSeveralChars) {
        mTriePos = CharEncoding.writeCharArray(characters, mTrieBuf, mTriePos);
        if (hasSeveralChars) {
            mTrieBuf[mTriePos++] = FormatSpec.PTNODE_CHARACTERS_TERMINATOR;
        }
    }

    private void writeTerminalId(final int terminalId) {
        mTriePos = BinaryDictEncoderUtils.writeUIntToBuffer(mTrieBuf, mTriePos, terminalId,
                FormatSpec.PTNODE_TERMINAL_ID_SIZE);
    }

    private void writeChildrenPosition(PtNode ptNode, FormatOptions formatOptions) {
        final int childrenPos = BinaryDictEncoderUtils.getChildrenPosition(ptNode, formatOptions);
        if (formatOptions.mSupportsDynamicUpdate) {
            mTriePos += BinaryDictEncoderUtils.writeSignedChildrenPosition(mTrieBuf,
                    mTriePos, childrenPos);
        } else {
            mTriePos += BinaryDictEncoderUtils.writeChildrenPosition(mTrieBuf,
                    mTriePos, childrenPos);
        }
    }

    private void writeBigrams(final ArrayList<PtNodeArray> flatNodes, final FusionDictionary dict)
            throws IOException {
        mBigramWriter.openStreams();
        for (final PtNodeArray nodeArray : flatNodes) {
            for (final PtNode ptNode : nodeArray.mData) {
                if (ptNode.mBigrams != null) {
                    mBigramWriter.writeBigramsForOneWord(ptNode.mTerminalId,
                            ptNode.mBigrams.iterator(), dict);
                }
            }
        }
        mBigramWriter.closeStreams();
    }

    private void writeShortcuts(final ArrayList<PtNodeArray> flatNodes) throws IOException {
        mShortcutWriter.openStreams();
        for (final PtNodeArray nodeArray : flatNodes) {
            for (final PtNode ptNode : nodeArray.mData) {
                if (ptNode.mShortcutTargets != null && !ptNode.mShortcutTargets.isEmpty()) {
                    mShortcutWriter.writeShortcutForOneWord(ptNode.mTerminalId,
                            ptNode.mShortcutTargets.iterator());
                }
            }
        }
        mShortcutWriter.closeStreams();
    }

    @Override
    public void writeForwardLinkAddress(int forwardLinkAddress) {
        mTriePos = BinaryDictEncoderUtils.writeUIntToBuffer(mTrieBuf, mTriePos,
                forwardLinkAddress, FormatSpec.FORWARD_LINK_ADDRESS_SIZE);
    }

    @Override
    public void writePtNode(final PtNode ptNode, final int parentPosition,
            final FormatOptions formatOptions, final FusionDictionary dict) {
        writePtNodeFlags(ptNode, formatOptions);
        writeParentPosition(parentPosition, ptNode, formatOptions);
        writeCharacters(ptNode.mChars, ptNode.hasSeveralChars());
        if (ptNode.isTerminal()) {
            writeTerminalId(ptNode.mTerminalId);
        }
        writeChildrenPosition(ptNode, formatOptions);
    }

    private void writeTerminalData(final ArrayList<PtNodeArray> flatNodes,
          final int terminalCount) throws IOException {
        final byte[] freqBuf = new byte[terminalCount * FormatSpec.FREQUENCY_AND_FLAGS_SIZE];
        final byte[] terminalAddressTableBuf =
                new byte[terminalCount * FormatSpec.TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE];
        for (final PtNodeArray nodeArray : flatNodes) {
            for (final PtNode ptNode : nodeArray.mData) {
                if (ptNode.isTerminal()) {
                    BinaryDictEncoderUtils.writeUIntToBuffer(freqBuf,
                            ptNode.mTerminalId * FormatSpec.FREQUENCY_AND_FLAGS_SIZE,
                            ptNode.mFrequency, FormatSpec.FREQUENCY_AND_FLAGS_SIZE);
                    BinaryDictEncoderUtils.writeUIntToBuffer(terminalAddressTableBuf,
                            ptNode.mTerminalId * FormatSpec.TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE,
                            ptNode.mCachedAddressAfterUpdate + mHeaderSize,
                            FormatSpec.TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE);
                }
            }
        }
        mFreqOutStream.write(freqBuf);
        mTerminalAddressTableOutStream.write(terminalAddressTableBuf);
    }
}
