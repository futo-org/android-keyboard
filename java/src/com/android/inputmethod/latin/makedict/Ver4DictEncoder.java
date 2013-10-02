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

import java.io.ByteArrayOutputStream;
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
    private SparseTable mBigramAddressTable;
    private OutputStream mTrieOutStream;
    private OutputStream mFreqOutStream;
    private OutputStream mTerminalAddressTableOutStream;
    private OutputStream mBigramOutStream;
    private File mDictDir;
    private String mBaseFilename;

    @UsedForTesting
    public Ver4DictEncoder(final File dictPlacedDir) {
        mDictPlacedDir = dictPlacedDir;
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
        final File bigramFile = new File(mDictDir,
                mBaseFilename + FormatSpec.BIGRAM_FILE_EXTENSION);
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
        mBigramOutStream = new FileOutputStream(bigramFile);
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
            if (mBigramOutStream != null) {
                mBigramOutStream.close();
            }
        } finally {
            mTrieOutStream = null;
            mFreqOutStream = null;
            mTerminalAddressTableOutStream = null;
            mBigramOutStream = null;
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
        mBigramAddressTable = new SparseTable(terminalCount,
                FormatSpec.BIGRAM_ADDRESS_TABLE_BLOCK_SIZE, 1 /* contentTableCount */);
        writeBigrams(flatNodes, dict);
        writeBigramAddressSparseTable();

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

    private void writeShortcuts(ArrayList<WeightedString> shortcuts) {
        if (null == shortcuts || shortcuts.isEmpty()) return;

        final int indexOfShortcutByteSize = mTriePos;
        mTriePos += FormatSpec.PTNODE_SHORTCUT_LIST_SIZE_SIZE;
        final Iterator<WeightedString> shortcutIterator = shortcuts.iterator();
        while (shortcutIterator.hasNext()) {
            final WeightedString target = shortcutIterator.next();
            final int shortcutFlags = BinaryDictEncoderUtils.makeShortcutFlags(
                    shortcutIterator.hasNext(), target.mFrequency);
            mTrieBuf[mTriePos++] = (byte)shortcutFlags;
            final int shortcutShift = CharEncoding.writeString(mTrieBuf, mTriePos,
                    target.mWord);
            mTriePos += shortcutShift;
        }
        final int shortcutByteSize = mTriePos - indexOfShortcutByteSize;
        if (shortcutByteSize > FormatSpec.MAX_SHORTCUT_LIST_SIZE_IN_A_PTNODE) {
            throw new RuntimeException("Shortcut list too large : " + shortcutByteSize);
        }
        BinaryDictEncoderUtils.writeUIntToBuffer(mTrieBuf, indexOfShortcutByteSize,
                shortcutByteSize, FormatSpec.PTNODE_SHORTCUT_LIST_SIZE_SIZE);
    }

    private void writeBigrams(final ArrayList<PtNodeArray> flatNodes, final FusionDictionary dict)
            throws IOException {
        final ByteArrayOutputStream bigramBuffer = new ByteArrayOutputStream();

        for (final PtNodeArray nodeArray : flatNodes) {
            for (final PtNode ptNode : nodeArray.mData) {
                if (ptNode.mBigrams != null) {
                    final int startPos = bigramBuffer.size();
                    mBigramAddressTable.set(0 /* contentTableIndex */, ptNode.mTerminalId,
                            startPos);
                    final Iterator<WeightedString> bigramIterator = ptNode.mBigrams.iterator();
                    while (bigramIterator.hasNext()) {
                        final WeightedString bigram = bigramIterator.next();
                        final PtNode target =
                            FusionDictionary.findWordInTree(dict.mRootNodeArray, bigram.mWord);
                        final int unigramFrequencyForThisWord = target.mFrequency;
                        final int bigramFlags = BinaryDictEncoderUtils.makeBigramFlags(
                                bigramIterator.hasNext(), 0, bigram.mFrequency,
                                unigramFrequencyForThisWord, bigram.mWord);
                        BinaryDictEncoderUtils.writeUIntToStream(bigramBuffer, bigramFlags,
                                FormatSpec.PTNODE_ATTRIBUTE_FLAGS_SIZE);
                        BinaryDictEncoderUtils.writeUIntToStream(bigramBuffer, target.mTerminalId,
                                FormatSpec.PTNODE_ATTRIBUTE_MAX_ADDRESS_SIZE);
                    }
                }
            }
        }
        bigramBuffer.writeTo(mBigramOutStream);
    }

    private void writeBigramAddressSparseTable() throws IOException {
        final File lookupIndexFile =
                new File(mDictDir, mBaseFilename + FormatSpec.BIGRAM_LOOKUP_TABLE_FILE_EXTENSION);
        final File contentFile =
                new File(mDictDir, mBaseFilename + FormatSpec.BIGRAM_ADDRESS_TABLE_FILE_EXTENSION);
        mBigramAddressTable.writeToFiles(lookupIndexFile, new File[] { contentFile });
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
        writeShortcuts(ptNode.mShortcutTargets);
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
