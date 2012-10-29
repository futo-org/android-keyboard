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
import com.android.inputmethod.latin.makedict.FusionDictionary.CharGroup;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.makedict.Word;

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
                + "  If -p (porcelain) option is given, produce output suitable for a script";
    }

    @Override
    public void run() {
        if (mArgs.length < 2) {
            throw new RuntimeException("Not enough arguments for command " + COMMAND);
        }
        final boolean porcelain;
        if ("-p".equals(mArgs[0])) {
            porcelain = true;
            mArgs = Arrays.copyOfRange(mArgs, 1, mArgs.length);
            if (mArgs.length != 2) { // There should be only 2 arguments left
                throw new RuntimeException("Wrong number of arguments for command " + COMMAND);
            }
        } else {
            porcelain = false;
        }
        final FusionDictionary dict0 =
                BinaryDictOffdeviceUtils.getDictionary(mArgs[0], false /* report */);
        if (null == dict0) throw new RuntimeException("Can't read dictionary " + mArgs[0]);
        final FusionDictionary dict1 =
                BinaryDictOffdeviceUtils.getDictionary(mArgs[1], false /* report */);
        if (null == dict1) throw new RuntimeException("Can't read dictionary " + mArgs[1]);
        if (!porcelain) {
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
        if (null == dict0.mOptions.mAttributes.get("locale")) return true;
        if (null == dict1.mOptions.mAttributes.get("locale")) return true;
        final String dict0Lang = dict0.mOptions.mAttributes.get("locale").split("_", 3)[0];
        final String dict1Lang = dict1.mOptions.mAttributes.get("locale").split("_", 3)[0];
        return !dict0Lang.equals(dict1Lang);
    }

    private static void diffHeaders(final FusionDictionary dict0, final FusionDictionary dict1) {
        if (dict0.mOptions.mFrenchLigatureProcessing != dict1.mOptions.mFrenchLigatureProcessing) {
            System.out.println("  French ligature processing : "
                    + dict0.mOptions.mFrenchLigatureProcessing + " <=> "
                    + dict1.mOptions.mFrenchLigatureProcessing);
        }
        else if (dict0.mOptions.mGermanUmlautProcessing != dict1.mOptions.mGermanUmlautProcessing) {
            System.out.println("  German umlaut processing : "
                    + dict0.mOptions.mGermanUmlautProcessing + " <=> "
                    + dict1.mOptions.mGermanUmlautProcessing);
        }
        final HashMap<String, String> options1 =
                new HashMap<String, String>(dict1.mOptions.mAttributes);
        for (final String optionKey : dict0.mOptions.mAttributes.keySet()) {
            if (!dict0.mOptions.mAttributes.get(optionKey).equals(
                    dict1.mOptions.mAttributes.get(optionKey))) {
                System.out.println("  " + optionKey + " : "
                        + dict0.mOptions.mAttributes.get(optionKey) + " <=> "
                        + dict1.mOptions.mAttributes.get(optionKey));
            }
            options1.remove(optionKey);
        }
        for (final String optionKey : options1.keySet()) {
            System.out.println("  " + optionKey + " : null <=> " + options1.get(optionKey));
        }
    }

    private static void diffWords(final FusionDictionary dict0, final FusionDictionary dict1) {
        for (final Word word0 : dict0) {
            final CharGroup word1 = FusionDictionary.findWordInTree(dict1.mRoot, word0.mWord);
            if (null == word1) {
                // This word is not in dict1
                System.out.println("Deleted: " + word0.mWord + " " + word0.mFrequency);
            } else {
                // We found the word. Compare frequencies, shortcuts, bigrams
                if (word0.mFrequency != word1.getFrequency()) {
                    System.out.println("Freq changed: " + word0.mWord + " " + word0.mFrequency
                            + " -> " + word1.getFrequency());
                }
                if (word0.mIsNotAWord != word1.getIsNotAWord()) {
                    System.out.println("Not a word: " + word0.mWord + " " + word0.mIsNotAWord
                            + " -> " + word1.getIsNotAWord());
                }
                if (word0.mIsBlacklistEntry != word1.getIsBlacklistEntry()) {
                    System.out.println("Blacklist: " + word0.mWord + " " + word0.mIsBlacklistEntry
                            + " -> " + word1.getIsBlacklistEntry());
                }
                diffAttributes(word0.mWord, word0.mBigrams, word1.getBigrams());
                diffAttributes(word0.mWord, word0.mShortcutTargets, word1.getShortcutTargets());
            }
        }
    }

    private static void diffAttributes(final String word, final ArrayList<WeightedString> list0,
            final ArrayList<WeightedString> list1) {
        if (null == list1) {
            if (null == list0) return;
            for (final WeightedString attribute0 : list0) {
                System.out.println("Bigram removed: " + word + " " + attribute0.mWord + " "
                        + attribute0.mFrequency);
            }
        } else if (null != list0) {
            for (final WeightedString attribute0 : list0) {
                // The following tests with #equals(). The WeightedString#equals() method returns
                // true if both the string and the frequency are the same.
                if (!list1.contains(attribute0)) {
                    // Search for a word with the same string but a different frequency
                    for (final WeightedString attribute1 : list1) {
                        if (attribute0.mWord.equals(attribute1.mWord)) {
                            System.out.println("Bigram freq changed: " + word + " "
                                    + attribute0.mWord + " " + attribute0.mFrequency + " -> "
                                    + attribute1.mFrequency);
                            list1.remove(attribute1);
                            break;
                        }
                        // We come here if we haven't found any matching string.
                        System.out.println("Bigram removed: " + word + " " + attribute0.mWord);
                    }
                } else {
                    list1.remove(attribute0);
                }
            }
        }
        // We removed any matching word that we found, so now list1 only contains words that
        // are not included in list0.
        for (final WeightedString attribute1 : list1) {
            System.out.println("Bigram added: " + word + " " + attribute1.mWord + " "
                    + attribute1.mFrequency);
        }
    }
}
