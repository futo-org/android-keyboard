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

import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils.CharEncoding;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
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
 * An implementation of DictEncoder for version 3 binary dictionary.
 */
public class Ver3DictEncoder implements DictEncoder {

    private final File mDictFile;
    private OutputStream mOutStream;
    private byte[] mBuffer;
    private int mPosition;

    public Ver3DictEncoder(final File dictFile) {
        mDictFile = dictFile;
        mOutStream = null;
        mBuffer = null;
    }

    // This constructor is used only by BinaryDictOffdeviceUtilsTests.
    // If you want to use this in the production code, you should consider keeping consistency of
    // the interface of Ver3DictDecoder by using factory.
    public Ver3DictEncoder(final OutputStream outStream) {
        mDictFile = null;
        mOutStream = outStream;
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

    @Override
    public void writeDictionary(final FusionDictionary dict, final FormatOptions formatOptions)
            throws IOException, UnsupportedFormatException {
        if (formatOptions.mVersion > FormatSpec.VERSION3) {
            throw new UnsupportedFormatException(
                    "The given format options has wrong version number : "
                    + formatOptions.mVersion);
        }

        if (mOutStream == null) {
            openStream();
        }
        BinaryDictEncoderUtils.writeDictionaryHeader(mOutStream, dict, formatOptions);

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
        BinaryDictEncoderUtils.computeAddresses(dict, flatNodes, formatOptions);
        MakedictLog.i("Checking PtNode array...");
        if (MakedictLog.DBG) BinaryDictEncoderUtils.checkFlatPtNodeArrayList(flatNodes);

        // Create a buffer that matches the final dictionary size.
        final PtNodeArray lastNodeArray = flatNodes.get(flatNodes.size() - 1);
        final int bufferSize = lastNodeArray.mCachedAddressAfterUpdate + lastNodeArray.mCachedSize;
        mBuffer = new byte[bufferSize];

        MakedictLog.i("Writing file...");

        for (PtNodeArray nodeArray : flatNodes) {
            BinaryDictEncoderUtils.writePlacedPtNodeArray(dict, this, nodeArray, formatOptions);
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

    private void writePtNodeFlags(final PtNode ptNode, final FormatOptions formatOptions) {
        final int childrenPos = BinaryDictEncoderUtils.getChildrenPosition(ptNode, formatOptions);
        mPosition = BinaryDictEncoderUtils.writeUIntToBuffer(mBuffer, mPosition,
                BinaryDictEncoderUtils.makePtNodeFlags(ptNode, childrenPos, formatOptions),
                FormatSpec.PTNODE_FLAGS_SIZE);
    }

    private void writeParentPosition(final int parentPosition, final PtNode ptNode,
            final FormatOptions formatOptions) {
        if (parentPosition == FormatSpec.NO_PARENT_ADDRESS) {
            mPosition = BinaryDictEncoderUtils.writeParentAddress(mBuffer, mPosition,
                    parentPosition, formatOptions);
        } else {
            mPosition = BinaryDictEncoderUtils.writeParentAddress(mBuffer, mPosition,
                    parentPosition - ptNode.mCachedAddressAfterUpdate, formatOptions);
        }
    }

    private void writeCharacters(final int[] codePoints, final boolean hasSeveralChars) {
        mPosition = CharEncoding.writeCharArray(codePoints, mBuffer, mPosition);
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

    private void writeChildrenPosition(final PtNode ptNode, final FormatOptions formatOptions) {
        final int childrenPos = BinaryDictEncoderUtils.getChildrenPosition(ptNode, formatOptions);
        if (formatOptions.mSupportsDynamicUpdate) {
            mPosition += BinaryDictEncoderUtils.writeSignedChildrenPosition(mBuffer, mPosition,
                    childrenPos);
        } else {
            mPosition += BinaryDictEncoderUtils.writeChildrenPosition(mBuffer, mPosition,
                    childrenPos);
        }
    }

    /**
     * Write a shortcut attributes list to mBuffer.
     *
     * @param shortcuts the shortcut attributes list.
     */
    private void writeShortcuts(final ArrayList<WeightedString> shortcuts) {
        if (null == shortcuts || shortcuts.isEmpty()) return;

        final int indexOfShortcutByteSize = mPosition;
        mPosition += FormatSpec.PTNODE_SHORTCUT_LIST_SIZE_SIZE;
        final Iterator<WeightedString> shortcutIterator = shortcuts.iterator();
        while (shortcutIterator.hasNext()) {
            final WeightedString target = shortcutIterator.next();
            final int shortcutFlags = BinaryDictEncoderUtils.makeShortcutFlags(
                    shortcutIterator.hasNext(),
                    target.mFrequency);
            mPosition = BinaryDictEncoderUtils.writeUIntToBuffer(mBuffer, mPosition, shortcutFlags,
                    FormatSpec.PTNODE_ATTRIBUTE_FLAGS_SIZE);
            final int shortcutShift = CharEncoding.writeString(mBuffer, mPosition, target.mWord);
            mPosition += shortcutShift;
        }
        final int shortcutByteSize = mPosition - indexOfShortcutByteSize;
        if (shortcutByteSize > FormatSpec.MAX_SHORTCUT_LIST_SIZE_IN_A_PTNODE) {
            throw new RuntimeException("Shortcut list too large");
        }
        BinaryDictEncoderUtils.writeUIntToBuffer(mBuffer, indexOfShortcutByteSize, shortcutByteSize,
                FormatSpec.PTNODE_SHORTCUT_LIST_SIZE_SIZE);
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
            final int unigramFrequencyForThisWord = target.mFrequency;
            final int offset = addressOfBigram
                    - (mPosition + FormatSpec.PTNODE_ATTRIBUTE_FLAGS_SIZE);
            final int bigramFlags = BinaryDictEncoderUtils.makeBigramFlags(bigramIterator.hasNext(),
                    offset, bigram.mFrequency, unigramFrequencyForThisWord, bigram.mWord);
            mPosition = BinaryDictEncoderUtils.writeUIntToBuffer(mBuffer, mPosition, bigramFlags,
                    FormatSpec.PTNODE_ATTRIBUTE_FLAGS_SIZE);
            mPosition += BinaryDictEncoderUtils.writeChildrenPosition(mBuffer, mPosition,
                    Math.abs(offset));
        }
    }

    @Override
    public void writeForwardLinkAddress(final int forwardLinkAddress) {
        mPosition = BinaryDictEncoderUtils.writeUIntToBuffer(mBuffer, mPosition, forwardLinkAddress,
                FormatSpec.FORWARD_LINK_ADDRESS_SIZE);
    }

    @Override
    public void writePtNode(final PtNode ptNode, final int parentPosition,
            final FormatOptions formatOptions, final FusionDictionary dict) {
        writePtNodeFlags(ptNode, formatOptions);
        writeParentPosition(parentPosition, ptNode, formatOptions);
        writeCharacters(ptNode.mChars, ptNode.hasSeveralChars());
        writeFrequency(ptNode.mFrequency);
        writeChildrenPosition(ptNode, formatOptions);
        writeShortcuts(ptNode.mShortcutTargets);
        writeBigrams(ptNode.mBigrams, dict);
    }
}
