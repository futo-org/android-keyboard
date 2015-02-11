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
import com.android.inputmethod.latin.makedict.BinaryDictEncoderUtils.CodePointTable;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * An implementation of DictEncoder for version 2 binary dictionary.
 */
@UsedForTesting
public class Ver2DictEncoder implements DictEncoder {

    private final File mDictFile;
    private OutputStream mOutStream;
    private byte[] mBuffer;
    private int mPosition;
    private final int mCodePointTableMode;
    public static final int CODE_POINT_TABLE_OFF = 0;
    public static final int CODE_POINT_TABLE_ON = 1;

    @UsedForTesting
    public Ver2DictEncoder(final File dictFile, final int codePointTableMode) {
        mDictFile = dictFile;
        mOutStream = null;
        mBuffer = null;
        mCodePointTableMode = codePointTableMode;
    }

    // This constructor is used only by BinaryDictOffdeviceUtilsTests.
    // If you want to use this in the production code, you should consider keeping consistency of
    // the interface of Ver3DictDecoder by using factory.
    @UsedForTesting
    public Ver2DictEncoder(final OutputStream outStream) {
        mDictFile = null;
        mOutStream = outStream;
        mCodePointTableMode = CODE_POINT_TABLE_OFF;
    }

    private void openStream() throws FileNotFoundException {
        mOutStream = new FileOutputStream(mDictFile);
    }

    private void close() throws IOException {
        if (mOutStream != null) {
            mOutStream.close();
            mOutStream = null;
        }
    }

    // Package for testing
    static CodePointTable makeCodePointTable(final FusionDictionary dict) {
        final HashMap<Integer, Integer> codePointOccurrenceCounts = new HashMap<>();
        for (final WordProperty word : dict) {
            // Store per code point occurrence
            final String wordString = word.mWord;
            for (int i = 0; i < wordString.length(); ++i) {
                final int codePoint = Character.codePointAt(wordString, i);
                if (codePointOccurrenceCounts.containsKey(codePoint)) {
                    codePointOccurrenceCounts.put(codePoint,
                            codePointOccurrenceCounts.get(codePoint) + 1);
                } else {
                    codePointOccurrenceCounts.put(codePoint, 1);
                }
            }
        }
        final ArrayList<Entry<Integer, Integer>> codePointOccurrenceArray =
                new ArrayList<>(codePointOccurrenceCounts.entrySet());
        // Descending order sort by occurrence (value side)
        Collections.sort(codePointOccurrenceArray, new Comparator<Entry<Integer, Integer>>() {
            @Override
            public int compare(final Entry<Integer, Integer> a, final Entry<Integer, Integer> b) {
                if (a.getValue() != b.getValue()) {
                    return b.getValue().compareTo(a.getValue());
                }
                return b.getKey().compareTo(a.getKey());
            }
        });
        int currentCodePointTableIndex = FormatSpec.MINIMAL_ONE_BYTE_CHARACTER_VALUE;
        // Temporary map for writing of nodes
        final HashMap<Integer, Integer> codePointToOneByteCodeMap = new HashMap<>();
        for (final Entry<Integer, Integer> entry : codePointOccurrenceArray) {
            // Put a relation from the original code point to the one byte code.
            codePointToOneByteCodeMap.put(entry.getKey(), currentCodePointTableIndex);
            if (FormatSpec.MAXIMAL_ONE_BYTE_CHARACTER_VALUE < ++currentCodePointTableIndex) {
                break;
            }
        }
        // codePointToOneByteCodeMap for writing the trie
        // codePointOccurrenceArray for writing the header
        return new CodePointTable(codePointToOneByteCodeMap, codePointOccurrenceArray);
    }

    @Override
    public void writeDictionary(final FusionDictionary dict, final FormatOptions formatOptions)
            throws IOException, UnsupportedFormatException {
        // We no longer support anything but the latest version of v2.
        if (formatOptions.mVersion != FormatSpec.VERSION202) {
            throw new UnsupportedFormatException(
                    "The given format options has wrong version number : "
                    + formatOptions.mVersion);
        }

        if (mOutStream == null) {
            openStream();
        }

        // Make code point conversion table ordered by occurrence of code points
        // Version 201 or later have codePointTable
        final CodePointTable codePointTable;
        if (mCodePointTableMode == CODE_POINT_TABLE_OFF || formatOptions.mVersion
                < FormatSpec.MINIMUM_SUPPORTED_VERSION_OF_CODE_POINT_TABLE) {
            codePointTable = new CodePointTable();
        } else {
            codePointTable = makeCodePointTable(dict);
        }

        BinaryDictEncoderUtils.writeDictionaryHeader(mOutStream, dict, formatOptions,
                codePointTable.mCodePointOccurrenceArray);

        // Addresses are limited to 3 bytes, but since addresses can be relative to each node
        // array, the structure itself is not limited to 16MB. However, if it is over 16MB deciding
        // the order of the PtNode arrays becomes a quite complicated problem, because though the
        // dictionary itself does not have a size limit, each node array must still be within 16MB
        // of all its children and parents. As long as this is ensured, the dictionary file may
        // grow to any size.

        // Leave the choice of the optimal node order to the flattenTree function.
        MakedictLog.i("Flattening the tree...");
        ArrayList<PtNodeArray> flatNodes = BinaryDictEncoderUtils.flattenTree(dict.mRootNodeArray);

        MakedictLog.i("Computing addresses...");
        BinaryDictEncoderUtils.computeAddresses(dict, flatNodes,
                codePointTable.mCodePointToOneByteCodeMap);
        MakedictLog.i("Checking PtNode array...");
        if (MakedictLog.DBG) BinaryDictEncoderUtils.checkFlatPtNodeArrayList(flatNodes);

        // Create a buffer that matches the final dictionary size.
        final PtNodeArray lastNodeArray = flatNodes.get(flatNodes.size() - 1);
        final int bufferSize = lastNodeArray.mCachedAddressAfterUpdate + lastNodeArray.mCachedSize;
        mBuffer = new byte[bufferSize];

        MakedictLog.i("Writing file...");

        for (PtNodeArray nodeArray : flatNodes) {
            BinaryDictEncoderUtils.writePlacedPtNodeArray(dict, this, nodeArray,
                    codePointTable.mCodePointToOneByteCodeMap);
        }
        if (MakedictLog.DBG) BinaryDictEncoderUtils.showStatistics(flatNodes);
        mOutStream.write(mBuffer, 0, mPosition);

        MakedictLog.i("Done");
        close();
    }

    @Override
    public void setPosition(final int position) {
        if (mBuffer == null || position < 0 || position >= mBuffer.length) return;
        mPosition = position;
    }

    @Override
    public int getPosition() {
        return mPosition;
    }

    @Override
    public void writePtNodeCount(final int ptNodeCount) {
        final int countSize = BinaryDictIOUtils.getPtNodeCountSize(ptNodeCount);
        if (countSize != 1 && countSize != 2) {
            throw new RuntimeException("Strange size from getGroupCountSize : " + countSize);
        }
        final int encodedPtNodeCount = (countSize == 2) ?
                (ptNodeCount | FormatSpec.LARGE_PTNODE_ARRAY_SIZE_FIELD_SIZE_FLAG) : ptNodeCount;
        mPosition = BinaryDictEncoderUtils.writeUIntToBuffer(mBuffer, mPosition, encodedPtNodeCount,
                countSize);
    }

    private void writePtNodeFlags(final PtNode ptNode,
            final HashMap<Integer, Integer> codePointToOneByteCodeMap) {
        final int childrenPos = BinaryDictEncoderUtils.getChildrenPosition(ptNode,
                codePointToOneByteCodeMap);
        mPosition = BinaryDictEncoderUtils.writeUIntToBuffer(mBuffer, mPosition,
                BinaryDictEncoderUtils.makePtNodeFlags(ptNode, childrenPos),
                FormatSpec.PTNODE_FLAGS_SIZE);
    }

    private void writeCharacters(final int[] codePoints, final boolean hasSeveralChars,
            final HashMap<Integer, Integer> codePointToOneByteCodeMap) {
        mPosition = CharEncoding.writeCharArray(codePoints, mBuffer, mPosition,
                codePointToOneByteCodeMap);
        if (hasSeveralChars) {
            mBuffer[mPosition++] = FormatSpec.PTNODE_CHARACTERS_TERMINATOR;
        }
    }

    private void writeFrequency(final int frequency) {
        if (frequency >= 0) {
            mPosition = BinaryDictEncoderUtils.writeUIntToBuffer(mBuffer, mPosition, frequency,
                    FormatSpec.PTNODE_FREQUENCY_SIZE);
        }
    }

    private void writeChildrenPosition(final PtNode ptNode,
            final HashMap<Integer, Integer> codePointToOneByteCodeMap) {
        final int childrenPos = BinaryDictEncoderUtils.getChildrenPosition(ptNode,
                codePointToOneByteCodeMap);
        mPosition += BinaryDictEncoderUtils.writeChildrenPosition(mBuffer, mPosition,
                childrenPos);
    }

    /**
     * Write a bigram attributes list to mBuffer.
     *
     * @param bigrams the bigram attributes list.
     * @param dict the dictionary the node array is a part of (for relative offsets).
     */
    private void writeBigrams(final ArrayList<WeightedString> bigrams,
            final FusionDictionary dict) {
        if (bigrams == null) return;

        final Iterator<WeightedString> bigramIterator = bigrams.iterator();
        while (bigramIterator.hasNext()) {
            final WeightedString bigram = bigramIterator.next();
            final PtNode target =
                    FusionDictionary.findWordInTree(dict.mRootNodeArray, bigram.mWord);
            final int addressOfBigram = target.mCachedAddressAfterUpdate;
            final int unigramFrequencyForThisWord = target.getProbability();
            final int offset = addressOfBigram
                    - (mPosition + FormatSpec.PTNODE_ATTRIBUTE_FLAGS_SIZE);
            final int bigramFlags = BinaryDictEncoderUtils.makeBigramFlags(bigramIterator.hasNext(),
                    offset, bigram.getProbability(), unigramFrequencyForThisWord, bigram.mWord);
            mPosition = BinaryDictEncoderUtils.writeUIntToBuffer(mBuffer, mPosition, bigramFlags,
                    FormatSpec.PTNODE_ATTRIBUTE_FLAGS_SIZE);
            mPosition += BinaryDictEncoderUtils.writeChildrenPosition(mBuffer, mPosition,
                    Math.abs(offset));
        }
    }

    @Override
    public void writePtNode(final PtNode ptNode, final FusionDictionary dict,
            final HashMap<Integer, Integer> codePointToOneByteCodeMap) {
        writePtNodeFlags(ptNode, codePointToOneByteCodeMap);
        writeCharacters(ptNode.mChars, ptNode.hasSeveralChars(), codePointToOneByteCodeMap);
        writeFrequency(ptNode.getProbability());
        writeChildrenPosition(ptNode, codePointToOneByteCodeMap);
        writeBigrams(ptNode.mBigrams, dict);
    }
}
