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

import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNode;
import com.android.inputmethod.latin.makedict.WeightedString;
import com.android.inputmethod.latin.makedict.WordProperty;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;

public class Diff extends Dicttool.Command {
    public static final String COMMAND = "diff";

    public Diff() {
    }

    @Override
    public String getHelp() {
        return COMMAND + " [-p] <dict> <dict> : shows differences between two dictionaries.\n"
                + "  If -p (plumbing) option is given, produce output suitable for a script";
    }

    @Override
    public void run() {
        if (mArgs.length < 2) {
            throw new RuntimeException("Not enough arguments for command " + COMMAND);
        }
        final boolean plumbing;
        if ("-p".equals(mArgs[0])) {
            plumbing = true;
            mArgs = Arrays.copyOfRange(mArgs, 1, mArgs.length);
            if (mArgs.length != 2) { // There should be only 2 arguments left
                throw new RuntimeException("Wrong number of arguments for command " + COMMAND);
            }
        } else {
            plumbing = false;
        }
        final FusionDictionary dict0 =
                BinaryDictOffdeviceUtils.getDictionary(mArgs[0], false /* report */);
        if (null == dict0) throw new RuntimeException("Can't read dictionary " + mArgs[0]);
        final FusionDictionary dict1 =
                BinaryDictOffdeviceUtils.getDictionary(mArgs[1], false /* report */);
        if (null == dict1) throw new RuntimeException("Can't read dictionary " + mArgs[1]);
        if (!plumbing) {
            System.out.println("Header :");
            diffHeaders(dict0, dict1);
            if (languageDiffers(dict0, dict1)) {
                // We only check for the language here. The rationale is that one may meaningfully
                // diff a en_US with a en_GB dictionary, but someone who diffs a de dict with a
                // pt_BR dict is almost certainly only interested in header-level diff, and the word
                // diff would be very large, meaningless, and annoying.
                return;
            }
            System.out.println("Body :");
        }
        diffWords(dict0, dict1);
    }

    private static boolean languageDiffers(final FusionDictionary dict0,
            final FusionDictionary dict1) {
        // If either of the dictionaries have no locale, assume it's okay
        if (null == dict0.mOptions.mAttributes.get("locale")) return false;
        if (null == dict1.mOptions.mAttributes.get("locale")) return false;
        final String dict0Lang = dict0.mOptions.mAttributes.get("locale").split("_", 3)[0];
        final String dict1Lang = dict1.mOptions.mAttributes.get("locale").split("_", 3)[0];
        return !dict0Lang.equals(dict1Lang);
    }

    private static void diffHeaders(final FusionDictionary dict0, final FusionDictionary dict1) {
        boolean hasDifferences = false;
        final HashMap<String, String> options1 = new HashMap<>(dict1.mOptions.mAttributes);
        for (final String optionKey : dict0.mOptions.mAttributes.keySet()) {
            if (!dict0.mOptions.mAttributes.get(optionKey).equals(
                    dict1.mOptions.mAttributes.get(optionKey))) {
                System.out.println("  " + optionKey + " : "
                        + dict0.mOptions.mAttributes.get(optionKey) + " <=> "
                        + dict1.mOptions.mAttributes.get(optionKey));
                hasDifferences = true;
            }
            options1.remove(optionKey);
        }
        for (final String optionKey : options1.keySet()) {
            System.out.println("  " + optionKey + " : null <=> " + options1.get(optionKey));
            hasDifferences = true;
        }
        if (!hasDifferences) {
            System.out.println("  No differences");
        }
    }

    private static void diffWords(final FusionDictionary dict0, final FusionDictionary dict1) {
        boolean hasDifferences = false;
        for (final WordProperty word0Property : dict0) {
            final PtNode word1PtNode = FusionDictionary.findWordInTree(dict1.mRootNodeArray,
                    word0Property.mWord);
            if (null == word1PtNode) {
                // This word is not in dict1
                System.out.println("Deleted: " + word0Property.mWord + " "
                        + word0Property.getProbability());
                hasDifferences = true;
            } else {
                // We found the word. Compare frequencies, shortcuts, bigrams
                if (word0Property.getProbability() != word1PtNode.getProbability()) {
                    System.out.println("Probability changed: " + word0Property.mWord + " "
                            + word0Property.getProbability() + " -> "
                            + word1PtNode.getProbability());
                    hasDifferences = true;
                }
                if (word0Property.mIsNotAWord != word1PtNode.getIsNotAWord()) {
                    System.out.println("Not a word: " + word0Property.mWord + " "
                            + word0Property.mIsNotAWord + " -> " + word1PtNode.getIsNotAWord());
                    hasDifferences = true;
                }
                if (word0Property.mIsPossiblyOffensive != word1PtNode.getIsPossiblyOffensive()) {
                    System.out.println("Possibly-offensive: " + word0Property.mWord + " "
                            + word0Property.mIsPossiblyOffensive + " -> "
                            + word1PtNode.getIsPossiblyOffensive());
                    hasDifferences = true;
                }
                hasDifferences |= hasAttributesDifferencesAndPrintThemIfAny(word0Property.mWord,
                        "Bigram", word0Property.getBigrams(), word1PtNode.getBigrams());
            }
        }
        for (final WordProperty word1Property : dict1) {
            final PtNode word0PtNode = FusionDictionary.findWordInTree(dict0.mRootNodeArray,
                    word1Property.mWord);
            if (null == word0PtNode) {
                // This word is not in dict0
                System.out.println("Added: " + word1Property.mWord + " "
                        + word1Property.getProbability());
                hasDifferences = true;
            }
        }
        if (!hasDifferences) {
            System.out.println("  No differences");
        }
    }

    private static boolean hasAttributesDifferencesAndPrintThemIfAny(final String word,
            final String type, final ArrayList<WeightedString> list0,
            final ArrayList<WeightedString> list1) {
        if (null == list1) {
            if (null == list0) return false;
            for (final WeightedString attribute0 : list0) {
                System.out.println(type + " removed: " + word + " " + attribute0.mWord + " "
                        + attribute0.getProbability());
            }
            return true;
        }
        boolean hasDifferences = false;
        if (null != list0) {
            for (final WeightedString attribute0 : list0) {
                // The following tests with #equals(). The WeightedString#equals() method returns
                // true if both the string and the frequency are the same.
                if (!list1.contains(attribute0)) {
                    hasDifferences = true;
                    // Search for a word with the same string but a different frequency
                    boolean foundString = false;
                    for (final WeightedString attribute1 : list1) {
                        if (attribute0.mWord.equals(attribute1.mWord)) {
                            System.out.println(type + " freq changed: " + word + " "
                                    + attribute0.mWord + " " + attribute0.getProbability() + " -> "
                                    + attribute1.getProbability());
                            list1.remove(attribute1);
                            foundString = true;
                            break;
                        }
                    }
                    if (!foundString) {
                        // We come here if we haven't found any matching string.
                        System.out.println(type + " removed: " + word + " " + attribute0.mWord + " "
                                + attribute0.getProbability());
                    }
                } else {
                    list1.remove(attribute0);
                }
            }
        }
        // We removed any matching word that we found, so now list1 only contains words that
        // are not included in list0.
        for (final WeightedString attribute1 : list1) {
            hasDifferences = true;
            System.out.println(type + " added: " + word + " " + attribute1.mWord + " "
                    + attribute1.getProbability());
        }
        return hasDifferences;
    }
}
