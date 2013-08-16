/*
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
import com.android.inputmethod.latin.makedict.FusionDictionary.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.makedict.Word;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * Reads and writes combined format for a FusionDictionary.
 *
 * All functions in this class are static.
 */
public class CombinedInputOutput {

    private static final String DICTIONARY_TAG = "dictionary";
    private static final String BIGRAM_TAG = "bigram";
    private static final String SHORTCUT_TAG = "shortcut";
    private static final String FREQUENCY_TAG = "f";
    private static final String WORD_TAG = "word";
    private static final String NOT_A_WORD_TAG = "not_a_word";
    private static final String WHITELIST_TAG = "whitelist";
    private static final String OPTIONS_TAG = "options";
    private static final String GERMAN_UMLAUT_PROCESSING_OPTION = "german_umlaut_processing";
    private static final String FRENCH_LIGATURE_PROCESSING_OPTION = "french_ligature_processing";
    private static final String COMMENT_LINE_STARTER = "#";

    /**
     * Basic test to find out whether the file is in the combined format or not.
     *
     * Concretely this only tests the header line.
     *
     * @param filename The name of the file to test.
     * @return true if the file is in the combined format, false otherwise
     */
    public static boolean isCombinedDictionary(final String filename) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(new File(filename)));
            String firstLine = reader.readLine();
            while (firstLine.startsWith(COMMENT_LINE_STARTER)) {
                firstLine = reader.readLine();
            }
            return firstLine.matches("^" + DICTIONARY_TAG + "=[^:]+(:[^=]+=[^:]+)*");
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
    }

    /**
     * Reads a dictionary from a combined format file.
     *
     * This is the public method that will read a combined file and return the corresponding memory
     * representation.
     *
     * @param source the file to read the data from.
     * @return the in-memory representation of the dictionary.
     */
    public static FusionDictionary readDictionaryCombined(final InputStream source)
            throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(source, "UTF-8"));
        String headerLine = reader.readLine();
        while (headerLine.startsWith(COMMENT_LINE_STARTER)) {
            headerLine = reader.readLine();
        }
        final String header[] = headerLine.split(",");
        final HashMap<String, String> attributes = new HashMap<String, String>();
        for (String item : header) {
            final String keyValue[] = item.split("=");
            if (2 != keyValue.length) {
                throw new RuntimeException("Wrong header format : " + headerLine);
            }
            attributes.put(keyValue[0], keyValue[1]);
        }

        final boolean processUmlauts =
                GERMAN_UMLAUT_PROCESSING_OPTION.equals(attributes.get(OPTIONS_TAG));
        final boolean processLigatures =
                FRENCH_LIGATURE_PROCESSING_OPTION.equals(attributes.get(OPTIONS_TAG));
        attributes.remove(OPTIONS_TAG);
        final FusionDictionary dict = new FusionDictionary(new PtNodeArray(), new DictionaryOptions(
                attributes, processUmlauts, processLigatures));

        String line;
        String word = null;
        int freq = 0;
        boolean isNotAWord = false;
        ArrayList<WeightedString> bigrams = new ArrayList<WeightedString>();
        ArrayList<WeightedString> shortcuts = new ArrayList<WeightedString>();
        while (null != (line = reader.readLine())) {
            if (line.startsWith(COMMENT_LINE_STARTER)) continue;
            final String args[] = line.trim().split(",");
            if (args[0].matches(WORD_TAG + "=.*")) {
                if (null != word) {
                    dict.add(word, freq, shortcuts.isEmpty() ? null : shortcuts, isNotAWord);
                    for (WeightedString s : bigrams) {
                        dict.setBigram(word, s.mWord, s.mFrequency);
                    }
                }
                if (!shortcuts.isEmpty()) shortcuts = new ArrayList<WeightedString>();
                if (!bigrams.isEmpty()) bigrams = new ArrayList<WeightedString>();
                isNotAWord = false;
                for (String param : args) {
                    final String params[] = param.split("=", 2);
                    if (2 != params.length) throw new RuntimeException("Wrong format : " + line);
                    if (WORD_TAG.equals(params[0])) {
                        word = params[1];
                    } else if (FREQUENCY_TAG.equals(params[0])) {
                        freq = Integer.parseInt(params[1]);
                    } else if (NOT_A_WORD_TAG.equals(params[0])) {
                        isNotAWord = "true".equals(params[1]);
                    }
                }
            } else if (args[0].matches(SHORTCUT_TAG + "=.*")) {
                String shortcut = null;
                int shortcutFreq = 0;
                for (String param : args) {
                    final String params[] = param.split("=", 2);
                    if (2 != params.length) throw new RuntimeException("Wrong format : " + line);
                    if (SHORTCUT_TAG.equals(params[0])) {
                        shortcut = params[1];
                    } else if (FREQUENCY_TAG.equals(params[0])) {
                        shortcutFreq = WHITELIST_TAG.equals(params[1])
                                ? FormatSpec.SHORTCUT_WHITELIST_FREQUENCY
                                : Integer.parseInt(params[1]);
                    }
                }
                if (null != shortcut) {
                    shortcuts.add(new WeightedString(shortcut, shortcutFreq));
                } else {
                    throw new RuntimeException("Wrong format : " + line);
                }
            } else if (args[0].matches(BIGRAM_TAG + "=.*")) {
                String secondWordOfBigram = null;
                int bigramFreq = 0;
                for (String param : args) {
                    final String params[] = param.split("=", 2);
                    if (2 != params.length) throw new RuntimeException("Wrong format : " + line);
                    if (BIGRAM_TAG.equals(params[0])) {
                        secondWordOfBigram = params[1];
                    } else if (FREQUENCY_TAG.equals(params[0])) {
                        bigramFreq = Integer.parseInt(params[1]);
                    }
                }
                if (null != secondWordOfBigram) {
                    bigrams.add(new WeightedString(secondWordOfBigram, bigramFreq));
                } else {
                    throw new RuntimeException("Wrong format : " + line);
                }
            }
        }
        if (null != word) {
            dict.add(word, freq, shortcuts.isEmpty() ? null : shortcuts, isNotAWord);
            for (WeightedString s : bigrams) {
                dict.setBigram(word, s.mWord, s.mFrequency);
            }
        }

        return dict;
    }

    /**
     * Writes a dictionary to a combined file.
     *
     * @param destination a destination stream to write to.
     * @param dict the dictionary to write.
     */
    public static void writeDictionaryCombined(Writer destination, FusionDictionary dict)
            throws IOException {
        final TreeSet<Word> set = new TreeSet<Word>();
        for (Word word : dict) {
            set.add(word); // This for ordering by frequency, then by asciibetic order
        }
        final HashMap<String, String> options = dict.mOptions.mAttributes;
        destination.write(DICTIONARY_TAG + "=");
        if (options.containsKey(DICTIONARY_TAG)) {
            destination.write(options.get(DICTIONARY_TAG));
            options.remove(DICTIONARY_TAG);
        }
        if (dict.mOptions.mGermanUmlautProcessing) {
            destination.write("," + OPTIONS_TAG + "=" + GERMAN_UMLAUT_PROCESSING_OPTION);
        } else if (dict.mOptions.mFrenchLigatureProcessing) {
            destination.write("," + OPTIONS_TAG + "=" + FRENCH_LIGATURE_PROCESSING_OPTION);
        }
        for (final String key : dict.mOptions.mAttributes.keySet()) {
            final String value = dict.mOptions.mAttributes.get(key);
            destination.write("," + key + "=" + value);
        }
        destination.write("\n");
        for (Word word : set) {
            destination.write(" " + WORD_TAG + "=" + word.mWord + ","
                    + FREQUENCY_TAG + "=" + word.mFrequency
                    + (word.mIsNotAWord ? "," + NOT_A_WORD_TAG + "=true\n" : "\n"));
            if (null != word.mShortcutTargets) {
                for (WeightedString target : word.mShortcutTargets) {
                    destination.write("  " + SHORTCUT_TAG + "=" + target.mWord + ","
                            + FREQUENCY_TAG + "=" + target.mFrequency + "\n");
                }
            }
            if (null != word.mBigrams) {
                for (WeightedString bigram : word.mBigrams) {
                    destination.write("  " + BIGRAM_TAG + "=" + bigram.mWord + ","
                            + FREQUENCY_TAG + "=" + bigram.mFrequency + "\n");
                }
            }
        }
        destination.close();
    }
}
