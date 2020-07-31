/**
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin.dicttool;

import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.WeightedString;
import com.android.inputmethod.latin.makedict.WordProperty;

import java.util.Arrays;
import java.util.ArrayList;

public class Info extends Dicttool.Command {
    public static final String COMMAND = "info";

    public Info() {
    }

    @Override
    public String getHelp() {
        return COMMAND + " <filename>: prints various information about a dictionary file";
    }

    private static void showInfo(final FusionDictionary dict, final boolean plumbing) {
        System.out.println("Header attributes :");
        System.out.print(dict.mOptions.toString(2, plumbing));
        int wordCount = 0;
        int bigramCount = 0;
        int shortcutCount = 0;
        int allowlistCount = 0;
        for (final WordProperty wordProperty : dict) {
            ++wordCount;
            if (wordProperty.mHasNgrams) {
                bigramCount += wordProperty.mNgrams.size();
            }
        }
        System.out.println("Words in the dictionary : " + wordCount);
        System.out.println("Bigram count : " + bigramCount);
        System.out.println("Shortcuts : " + shortcutCount + " (out of which " + allowlistCount
                + " allowlist entries)");
    }

    private static void showWordInfo(final FusionDictionary dict, final String word) {
        final PtNode ptNode = FusionDictionary.findWordInTree(dict.mRootNodeArray, word);
        if (null == ptNode) {
            System.out.println(word + " is not in the dictionary");
            return;
        }
        System.out.println("Word: " + word);
        System.out.println("  Freq: " + ptNode.getProbability());
        if (ptNode.getIsNotAWord()) {
            System.out.println("  Is not a word");
        }
        if (ptNode.getIsPossiblyOffensive()) {
            System.out.println("  Is possibly offensive");
        }
        final ArrayList<WeightedString> bigrams = ptNode.getBigrams();
        if (null == bigrams || bigrams.isEmpty()) {
            System.out.println("  No bigrams");
        } else {
            for (final WeightedString bigram : bigrams) {
                System.out.println(
                        "  Bigram: " + bigram.mWord + " (" + bigram.getProbability() + ")");
            }
        }
    }

    @Override
    public void run() {
        if (mArgs.length < 1) {
            throw new RuntimeException("Not enough arguments for command " + COMMAND);
        }
        final boolean plumbing;
        if ("-p".equals(mArgs[0])) {
            plumbing = true;
            mArgs = Arrays.copyOfRange(mArgs, 1, mArgs.length);
            if (mArgs.length != 1) { // There should be only 1 argument left
                throw new RuntimeException("Wrong number of arguments for command " + COMMAND);
            }
        } else {
            plumbing = false;
        }
        final String filename = mArgs[0];
        final boolean hasWordArguments = (1 == mArgs.length);
        final FusionDictionary dict = BinaryDictOffdeviceUtils.getDictionary(filename,
                hasWordArguments /* report */);
        if (hasWordArguments) {
            showInfo(dict, plumbing);
        } else {
            for (int i = 1; i < mArgs.length; ++i) {
                showWordInfo(dict, mArgs[i]);
            }
        }
    }
}
