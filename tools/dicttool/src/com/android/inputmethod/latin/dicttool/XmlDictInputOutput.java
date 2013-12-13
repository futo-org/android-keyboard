/*
 * Copyright (C) 2011 The Android Open Source Project
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
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads and writes XML files for a FusionDictionary.
 *
 * All functions in this class are static.
 */
public class XmlDictInputOutput {

    private static final String ROOT_TAG = "wordlist";
    private static final String WORD_TAG = "w";
    private static final String BIGRAM_TAG = "bigram";
    private static final String SHORTCUT_TAG = "shortcut";
    private static final String FREQUENCY_ATTR = "f";
    private static final String WORD_ATTR = "word";
    private static final String NOT_A_WORD_ATTR = "not_a_word";

    private static final String OPTIONS_KEY = "options";
    private static final String GERMAN_UMLAUT_PROCESSING_OPTION = "german_umlaut_processing";
    private static final String FRENCH_LIGATURE_PROCESSING_OPTION = "french_ligature_processing";

    /**
     * SAX handler for a unigram XML file.
     */
    static private class UnigramHandler extends DefaultHandler {
        // Parser states
        private static final int START = 1;
        private static final int WORD = 2;
        private static final int UNKNOWN = 3;

        FusionDictionary mDictionary;
        int mState; // the state of the parser
        int mFreq; // the currently read freq
        String mWord; // the current word
        final HashMap<String, ArrayList<WeightedString>> mShortcutsMap;

        /**
         * Create the handler.
         *
         * @param shortcuts the shortcuts as a map. This may be empty, but may not be null.
         */
        public UnigramHandler(final HashMap<String, ArrayList<WeightedString>> shortcuts) {
            mDictionary = null;
            mShortcutsMap = shortcuts;
            mWord = "";
            mState = START;
            mFreq = 0;
        }

        public FusionDictionary getFinalDictionary() {
            final FusionDictionary dict = mDictionary;
            for (final String shortcutOnly : mShortcutsMap.keySet()) {
                if (dict.hasWord(shortcutOnly)) continue;
                dict.add(shortcutOnly, 1, mShortcutsMap.get(shortcutOnly), true /* isNotAWord */);
            }
            mDictionary = null;
            mShortcutsMap.clear();
            mWord = "";
            mState = START;
            mFreq = 0;
            return dict;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            if (WORD_TAG.equals(localName)) {
                mState = WORD;
                mWord = "";
                for (int attrIndex = 0; attrIndex < attrs.getLength(); ++attrIndex) {
                    final String attrName = attrs.getLocalName(attrIndex);
                    if (FREQUENCY_ATTR.equals(attrName)) {
                        mFreq = Integer.parseInt(attrs.getValue(attrIndex));
                    }
                }
            } else if (ROOT_TAG.equals(localName)) {
                final HashMap<String, String> attributes = new HashMap<String, String>();
                for (int attrIndex = 0; attrIndex < attrs.getLength(); ++attrIndex) {
                    final String attrName = attrs.getLocalName(attrIndex);
                    attributes.put(attrName, attrs.getValue(attrIndex));
                }
                final String optionsString = attributes.get(OPTIONS_KEY);
                final boolean processUmlauts =
                        GERMAN_UMLAUT_PROCESSING_OPTION.equals(optionsString);
                final boolean processLigatures =
                        FRENCH_LIGATURE_PROCESSING_OPTION.equals(optionsString);
                mDictionary = new FusionDictionary(new PtNodeArray(),
                        new DictionaryOptions(attributes, processUmlauts, processLigatures));
            } else {
                mState = UNKNOWN;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (WORD == mState) {
                // The XML parser is free to return text in arbitrary chunks one after the
                // other. In particular, this happens in some implementations when it finds
                // an escape code like "&amp;".
                mWord += String.copyValueOf(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (WORD == mState) {
                mDictionary.add(mWord, mFreq, mShortcutsMap.get(mWord), false /* isNotAWord */);
                mState = START;
            }
        }
    }

    static private class AssociativeListHandler extends DefaultHandler {
        private final String SRC_TAG;
        private final String SRC_ATTRIBUTE;
        private final String DST_TAG;
        private final String DST_ATTRIBUTE;
        private final String DST_FREQ;

        // In this version of the XML file, the bigram frequency is given as an int 0..XML_MAX
        private final static int XML_MAX = 256;
        // In memory and in the binary dictionary the bigram frequency is 0..MEMORY_MAX
        private final static int MEMORY_MAX = 256;
        private final static int XML_TO_MEMORY_RATIO = XML_MAX / MEMORY_MAX;

        private String mSrc;
        private final HashMap<String, ArrayList<WeightedString>> mAssocMap;

        public AssociativeListHandler(final String srcTag, final String srcAttribute,
                final String dstTag, final String dstAttribute, final String dstFreq) {
            SRC_TAG = srcTag;
            SRC_ATTRIBUTE = srcAttribute;
            DST_TAG = dstTag;
            DST_ATTRIBUTE = dstAttribute;
            DST_FREQ = dstFreq;
            mSrc = null;
            mAssocMap = new HashMap<String, ArrayList<WeightedString>>();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            if (SRC_TAG.equals(localName)) {
                mSrc = attrs.getValue(uri, SRC_ATTRIBUTE);
            } else if (DST_TAG.equals(localName)) {
                String dst = attrs.getValue(uri, DST_ATTRIBUTE);
                int freq = getValueFromFreqString(attrs.getValue(uri, DST_FREQ));
                WeightedString bigram = new WeightedString(dst, freq / XML_TO_MEMORY_RATIO);
                ArrayList<WeightedString> bigramList = mAssocMap.get(mSrc);
                if (null == bigramList) bigramList = new ArrayList<WeightedString>();
                bigramList.add(bigram);
                mAssocMap.put(mSrc, bigramList);
            }
        }

        protected int getValueFromFreqString(final String freqString) {
            return Integer.parseInt(freqString);
        }

        // This may return an empty map, but will never return null.
        public HashMap<String, ArrayList<WeightedString>> getAssocMap() {
            return mAssocMap;
        }
    }

    /**
     * SAX handler for a bigram XML file.
     */
    static private class BigramHandler extends AssociativeListHandler {
        private final static String BIGRAM_W1_TAG = "bi";
        private final static String BIGRAM_W2_TAG = "w";
        private final static String BIGRAM_W1_ATTRIBUTE = "w1";
        private final static String BIGRAM_W2_ATTRIBUTE = "w2";
        private final static String BIGRAM_FREQ_ATTRIBUTE = "p";

        public BigramHandler() {
            super(BIGRAM_W1_TAG, BIGRAM_W1_ATTRIBUTE, BIGRAM_W2_TAG, BIGRAM_W2_ATTRIBUTE,
                    BIGRAM_FREQ_ATTRIBUTE);
        }

        // As per getAssocMap(), this never returns null.
        public HashMap<String, ArrayList<WeightedString>> getBigramMap() {
            return getAssocMap();
        }
    }

    /**
     * SAX handler for a shortcut & whitelist XML file.
     */
    static private class ShortcutAndWhitelistHandler extends AssociativeListHandler {
        private final static String ENTRY_TAG = "entry";
        private final static String ENTRY_ATTRIBUTE = "shortcut";
        private final static String TARGET_TAG = "target";
        private final static String REPLACEMENT_ATTRIBUTE = "replacement";
        private final static String TARGET_PRIORITY_ATTRIBUTE = "priority";
        private final static String WHITELIST_MARKER = "whitelist";
        private final static int WHITELIST_FREQ_VALUE = 15;
        private final static int MIN_FREQ = 0;
        private final static int MAX_FREQ = 14;

        public ShortcutAndWhitelistHandler() {
            super(ENTRY_TAG, ENTRY_ATTRIBUTE, TARGET_TAG, REPLACEMENT_ATTRIBUTE,
                    TARGET_PRIORITY_ATTRIBUTE);
        }

        @Override
        protected int getValueFromFreqString(final String freqString) {
            if (WHITELIST_MARKER.equals(freqString)) {
                return WHITELIST_FREQ_VALUE;
            } else {
                final int intValue = super.getValueFromFreqString(freqString);
                if (intValue < MIN_FREQ || intValue > MAX_FREQ) {
                    throw new RuntimeException("Shortcut freq out of range. Accepted range is "
                            + MIN_FREQ + ".." + MAX_FREQ);
                }
                return intValue;
            }
        }

        // As per getAssocMap(), this never returns null.
        public HashMap<String, ArrayList<WeightedString>> getShortcutAndWhitelistMap() {
            return getAssocMap();
        }
    }

    /**
     * Basic test to find out whether the file is in the unigram XML format or not.
     *
     * Concretely this only tests the header line.
     *
     * @param filename The name of the file to test.
     * @return true if the file is in the unigram XML format, false otherwise
     */
    public static boolean isXmlUnigramDictionary(final String filename) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(new File(filename)));
            final String firstLine = reader.readLine();
            return firstLine.matches("^\\s*<wordlist .*>\\s*$");
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
     * Reads a dictionary from an XML file.
     *
     * This is the public method that will parse an XML file and return the corresponding memory
     * representation.
     *
     * @param unigrams the file to read the data from.
     * @param shortcuts the file to read the shortcuts & whitelist from, or null.
     * @param bigrams the file to read the bigrams from, or null.
     * @return the in-memory representation of the dictionary.
     */
    public static FusionDictionary readDictionaryXml(final InputStream unigrams,
            final InputStream shortcuts, final InputStream bigrams)
            throws SAXException, IOException, ParserConfigurationException {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final SAXParser parser = factory.newSAXParser();
        final BigramHandler bigramHandler = new BigramHandler();
        if (null != bigrams) parser.parse(bigrams, bigramHandler);

        final ShortcutAndWhitelistHandler shortcutAndWhitelistHandler =
                new ShortcutAndWhitelistHandler();
        if (null != shortcuts) parser.parse(shortcuts, shortcutAndWhitelistHandler);

        final UnigramHandler unigramHandler =
                new UnigramHandler(shortcutAndWhitelistHandler.getShortcutAndWhitelistMap());
        parser.parse(unigrams, unigramHandler);
        final FusionDictionary dict = unigramHandler.getFinalDictionary();
        final HashMap<String, ArrayList<WeightedString>> bigramMap = bigramHandler.getBigramMap();
        for (final String firstWord : bigramMap.keySet()) {
            if (!dict.hasWord(firstWord)) continue;
            final ArrayList<WeightedString> bigramList = bigramMap.get(firstWord);
            for (final WeightedString bigram : bigramList) {
                if (!dict.hasWord(bigram.mWord)) continue;
                dict.setBigram(firstWord, bigram.mWord, bigram.mFrequency);
            }
        }
        return dict;
    }

    /**
     * Reads a dictionary in the first, legacy XML format
     *
     * This method reads data from the parser and creates a new FusionDictionary with it.
     * The format parsed by this method is the format used before Ice Cream Sandwich,
     * which has no support for bigrams or shortcuts/whitelist.
     * It is important to note that this method expects the parser to have already eaten
     * the first, all-encompassing tag.
     *
     * @param xpp the parser to read the data from.
     * @return the parsed dictionary.
     */

    /**
     * Writes a dictionary to an XML file.
     *
     * The output format is the "second" format, which supports bigrams and shortcuts/whitelist.
     *
     * @param destination a destination stream to write to.
     * @param dict the dictionary to write.
     */
    public static void writeDictionaryXml(Writer destination, FusionDictionary dict)
            throws IOException {
        final TreeSet<Word> set = new TreeSet<Word>();
        for (Word word : dict) {
            set.add(word);
        }
        // TODO: use an XMLSerializer if this gets big
        destination.write("<wordlist format=\"2\"");
        final HashMap<String, String> options = dict.mOptions.mAttributes;
        if (dict.mOptions.mGermanUmlautProcessing) {
            destination.write(" " + OPTIONS_KEY + "=\"" + GERMAN_UMLAUT_PROCESSING_OPTION + "\"");
        } else if (dict.mOptions.mFrenchLigatureProcessing) {
            destination.write(" " + OPTIONS_KEY + "=\"" + FRENCH_LIGATURE_PROCESSING_OPTION + "\"");
        }
        for (final String key : dict.mOptions.mAttributes.keySet()) {
            final String value = dict.mOptions.mAttributes.get(key);
            destination.write(" " + key + "=\"" + value + "\"");
        }
        destination.write(">\n");
        destination.write("<!-- Warning: there is no code to read this format yet. -->\n");
        for (Word word : set) {
            destination.write("  <" + WORD_TAG + " " + WORD_ATTR + "=\"" + word.mWord + "\" "
                    + FREQUENCY_ATTR + "=\"" + word.mFrequency
                    + (word.mIsNotAWord ? "\" " + NOT_A_WORD_ATTR + "=\"true" : "") + "\">");
            if (null != word.mShortcutTargets) {
                destination.write("\n");
                for (WeightedString target : word.mShortcutTargets) {
                    destination.write("    <" + SHORTCUT_TAG + " " + FREQUENCY_ATTR + "=\""
                            + target.mFrequency + "\">" + target.mWord + "</" + SHORTCUT_TAG
                            + ">\n");
                }
                destination.write("  ");
            }
            if (null != word.mBigrams) {
                destination.write("\n");
                for (WeightedString bigram : word.mBigrams) {
                    destination.write("    <" + BIGRAM_TAG + " " + FREQUENCY_ATTR + "=\""
                            + bigram.mFrequency + "\">" + bigram.mWord + "</" + BIGRAM_TAG + ">\n");
                }
                destination.write("  ");
            }
            destination.write("</" + WORD_TAG + ">\n");
        }
        destination.write("</wordlist>\n");
        destination.close();
    }
}
