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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.FusionDictionary.WeightedString;

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

    private static final String WORD_TAG = "w";
    private static final String BIGRAM_TAG = "bigram";
    private static final String FREQUENCY_ATTR = "f";
    private static final String WORD_ATTR = "word";

    /**
     * SAX handler for a unigram XML file.
     */
    static private class UnigramHandler extends DefaultHandler {
        // Parser states
        private static final int NONE = 0;
        private static final int START = 1;
        private static final int WORD = 2;
        private static final int BIGRAM = 4;
        private static final int END = 5;
        private static final int UNKNOWN = 6;

        final FusionDictionary mDictionary;
        int mState; // the state of the parser
        int mFreq; // the currently read freq
        String mWord; // the current word
        final HashMap<String, ArrayList<WeightedString>> mBigramsMap;

        /**
         * Create the handler.
         *
         * @param dict the dictionary to construct.
         * @param bigrams the bigrams as a map. This may be empty, but may not be null.
         */
        public UnigramHandler(FusionDictionary dict,
                HashMap<String, ArrayList<WeightedString>> bigrams) {
            mDictionary = dict;
            mBigramsMap = bigrams;
            mWord = "";
            mState = START;
            mFreq = 0;
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
                mDictionary.add(mWord, mFreq, mBigramsMap.get(mWord));
                mState = START;
            }
        }
    }

    /**
     * SAX handler for a bigram XML file.
     */
    static private class BigramHandler extends DefaultHandler {
        private final static String BIGRAM_W1_TAG = "bi";
        private final static String BIGRAM_W2_TAG = "w";
        private final static String BIGRAM_W1_ATTRIBUTE = "w1";
        private final static String BIGRAM_W2_ATTRIBUTE = "w2";
        private final static String BIGRAM_FREQ_ATTRIBUTE = "p";

        String mW1;
        final HashMap<String, ArrayList<WeightedString>> mBigramsMap;

        public BigramHandler() {
            mW1 = null;
            mBigramsMap = new HashMap<String, ArrayList<WeightedString>>();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            if (BIGRAM_W1_TAG.equals(localName)) {
                mW1 = attrs.getValue(uri, BIGRAM_W1_ATTRIBUTE);
            } else if (BIGRAM_W2_TAG.equals(localName)) {
                String w2 = attrs.getValue(uri, BIGRAM_W2_ATTRIBUTE);
                int freq = Integer.parseInt(attrs.getValue(uri, BIGRAM_FREQ_ATTRIBUTE));
                WeightedString bigram = new WeightedString(w2, freq / 8);
                ArrayList<WeightedString> bigramList = mBigramsMap.get(mW1);
                if (null == bigramList) bigramList = new ArrayList<WeightedString>();
                bigramList.add(bigram);
                mBigramsMap.put(mW1, bigramList);
            }
        }

        public HashMap<String, ArrayList<WeightedString>> getBigramMap() {
            return mBigramsMap;
        }
    }

    /**
     * Reads a dictionary from an XML file.
     *
     * This is the public method that will parse an XML file and return the corresponding memory
     * representation.
     *
     * @param unigrams the file to read the data from.
     * @return the in-memory representation of the dictionary.
     */
    public static FusionDictionary readDictionaryXml(InputStream unigrams, InputStream bigrams)
            throws SAXException, IOException, ParserConfigurationException {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final SAXParser parser = factory.newSAXParser();
        final BigramHandler bigramHandler = new BigramHandler();
        if (null != bigrams) parser.parse(bigrams, bigramHandler);

        final FusionDictionary dict = new FusionDictionary();
        final UnigramHandler unigramHandler =
                new UnigramHandler(dict, bigramHandler.getBigramMap());
        parser.parse(unigrams, unigramHandler);
        return dict;
    }

    /**
     * Reads a dictionary in the first, legacy XML format
     *
     * This method reads data from the parser and creates a new FusionDictionary with it.
     * The format parsed by this method is the format used before Ice Cream Sandwich,
     * which has no support for bigrams or shortcuts.
     * It is important to note that this method expects the parser to have already eaten
     * the first, all-encompassing tag.
     *
     * @param xpp the parser to read the data from.
     * @return the parsed dictionary.
     */

    /**
     * Writes a dictionary to an XML file.
     *
     * The output format is the "second" format, which supports bigrams and shortcuts.
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
        destination.write("<wordlist format=\"2\">\n");
        for (Word word : set) {
            destination.write("  <" + WORD_TAG + " " + WORD_ATTR + "=\"" + word.mWord + "\" "
                    + FREQUENCY_ATTR + "=\"" + word.mFrequency + "\">");
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
