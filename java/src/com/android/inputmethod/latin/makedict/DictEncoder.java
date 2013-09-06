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

import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;

import java.io.IOException;
import java.util.ArrayList;

/**
 * An interface of binary dictionary encoder.
 */
public interface DictEncoder {
    public void writeDictionary(final FusionDictionary dict, final FormatOptions formatOptions)
            throws IOException, UnsupportedFormatException;

    public void setPosition(final int position);
    public int getPosition();
    public void writePtNodeCount(final int ptNodeCount);
    public void writePtNodeFlags(final PtNode ptNode, final int parentAddress,
           final FormatOptions formatOptions);
    public void writeParentPosition(final int parentPosition, final PtNode ptNode,
            final FormatOptions formatOptions);
    public void writeCharacters(final int[] characters, final boolean hasSeveralChars);
    public void writeFrequency(final int frequency);
    public void writeChildrenPosition(final PtNode ptNode, final FormatOptions formatOptions);

    /**
     * Write a shortcut attributes list to memory.
     *
     * @param shortcuts the shortcut attributes list.
     */
    public void writeShortcuts(final ArrayList<WeightedString> shortcuts);

    /**
     * Write a bigram attributes list to memory.
     *
     * @param bigrams the bigram attributes list.
     * @param dict the dictionary the node array is a part of (for relative offsets).
     */
    public void writeBigrams(final ArrayList<WeightedString> bigrams, final FusionDictionary dict);

    public void writeForwardLinkAddress(final int forwardLinkAddress);
}
