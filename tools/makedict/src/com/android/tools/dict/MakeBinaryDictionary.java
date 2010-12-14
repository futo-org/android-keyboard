/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.tools.dict;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Compresses a list of words, frequencies, and bigram data
 * into a tree structured binary dictionary.
 * Dictionary Version: 200 (may contain bigrams)
 *  Version number started from 200 rather than 1 because we wanted to prevent number of roots in
 *  any old dictionaries being mistaken as the version number. There is not a chance that there
 *  will be more than 200 roots. Version number should be increased when there is structural change
 *  in the data. There is no need to increase the version when only the words in the data changes.
 */
public class MakeBinaryDictionary {

    private static final int VERSION_NUM = 200;

    public static final int ALPHA_SIZE = 256;

    public static final String TAG_WORD = "w";
    public static final String ATTR_FREQ = "f";

    private static final int FLAG_ADDRESS_MASK  = 0x400000;
    private static final int FLAG_TERMINAL_MASK = 0x800000;
    private static final int ADDRESS_MASK = 0x3FFFFF;

    /**
     * Unit for this variable is in bytes
     * If destination file name is main.dict and file limit causes dictionary to be separated into
     * multiple file, it will generate main0.dict, main1.dict, and so forth.
     */
    private static int sOutputFileSize;
    private static boolean sSplitOutput;

    public static final CharNode EMPTY_NODE = new CharNode();

    List<CharNode> roots;
    Map<String, Integer> mDictionary;
    int mWordCount;

    BigramDictionary bigramDict;

    static class CharNode {
        char data;
        int freq;
        boolean terminal;
        List<CharNode> children;
        static int sNodes;

        public CharNode() {
            sNodes++;
        }
    }

    public static void usage() {
        System.err.println("Usage: makedict -s <src_dict.xml> [-b <src_bigram.xml>] "
                + "-d <dest.dict> [--size filesize]");
        System.exit(-1);
    }
    
    public static void main(String[] args) {
        int checkSource = -1;
        int checkBigram = -1;
        int checkDest = -1;
        int checkFileSize = -1;
        for (int i = 0; i < args.length; i+=2) {
            if (args[i].equals("-s")) checkSource = (i + 1);
            if (args[i].equals("-b")) checkBigram = (i + 1);
            if (args[i].equals("-d")) checkDest = (i + 1);
            if (args[i].equals("--size")) checkFileSize = (i + 1);
        }
        if (checkFileSize >= 0) {
            sSplitOutput = true;
            sOutputFileSize = Integer.parseInt(args[checkFileSize]);
        } else {
            sSplitOutput = false;
        }
        if (checkDest >= 0 && !args[checkDest].endsWith(".dict")) {
            System.err.println("Error: Dictionary output file extension should be \".dict\"");
            usage();
        } else if (checkSource >= 0 && checkBigram >= 0 && checkDest >= 0 &&
                ((!sSplitOutput && args.length == 6) || (sSplitOutput && args.length == 8))) {
            new MakeBinaryDictionary(args[checkSource], args[checkBigram], args[checkDest]);
        } else if (checkSource >= 0 && checkDest >= 0 &&
                ((!sSplitOutput && args.length == 4) || (sSplitOutput && args.length == 6))) {
            new MakeBinaryDictionary(args[checkSource], null, args[checkDest]);
        } else {
            usage();
        }
    }

    public MakeBinaryDictionary(String srcFilename, String bigramSrcFilename, String destFilename){
        System.out.println("Generating dictionary version " + VERSION_NUM);
        bigramDict = new BigramDictionary(bigramSrcFilename, (bigramSrcFilename != null));
        populateDictionary(srcFilename);
        writeToDict(destFilename);

        // Enable the code below to verify that the generated tree is traversable
        // and bigram data is stored correctly.
        if (false) {
            bigramDict.reverseLookupAll(mDictionary, dict);
            traverseDict(2, new char[32], 0);
        }
    }

    private void populateDictionary(String filename) {
        roots = new ArrayList<CharNode>();
        mDictionary = new HashMap<String, Integer>();
        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            parser.parse(new File(filename), new DefaultHandler() {
                boolean inWord;
                int freq;
                StringBuilder wordBuilder = new StringBuilder(48);

                @Override
                public void startElement(String uri, String localName,
                        String qName, Attributes attributes) {
                    if (qName.equals("w")) {
                        inWord = true;
                        freq = Integer.parseInt(attributes.getValue(0));
                        wordBuilder.setLength(0);
                    }
                }

                @Override
                public void characters(char[] data, int offset, int length) {
                    // Ignore other whitespace
                    if (!inWord) return;
                    wordBuilder.append(data, offset, length);
                }

                @Override
                public void endElement(String uri, String localName,
                        String qName) {
                    if (qName.equals("w")) {
                        if (wordBuilder.length() > 1) {
                            addWordTop(wordBuilder.toString(), freq);
                            mWordCount++;
                        }
                        inWord = false;
                    }
                }
            });
        } catch (Exception ioe) {
            System.err.println("Exception in parsing\n" + ioe);
            ioe.printStackTrace();
        }
        System.out.println("Nodes = " + CharNode.sNodes);
    }

    private int indexOf(List<CharNode> children, char c) {
        if (children == null) {
            return -1;
        }
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).data == c) {
                return i;
            }
        }
        return -1;
    }

    private void addWordTop(String word, int occur) {
        if (occur > 255) occur = 255;
        char firstChar = word.charAt(0);
        int index = indexOf(roots, firstChar);
        if (index == -1) {
            CharNode newNode = new CharNode();
            newNode.data = firstChar;
            newNode.freq = occur;
            index = roots.size();
            roots.add(newNode);
        } else {
            roots.get(index).freq += occur;
        }
        if (word.length() > 1) {
            addWordRec(roots.get(index), word, 1, occur);
        } else {
            roots.get(index).terminal = true;
        }
    }

    private void addWordRec(CharNode parent, String word, int charAt, int occur) {
        CharNode child = null;
        char data = word.charAt(charAt);
        if (parent.children == null) {
            parent.children = new ArrayList<CharNode>();
        } else {
            for (int i = 0; i < parent.children.size(); i++) {
                CharNode node = parent.children.get(i);
                if (node.data == data) {
                    child = node;
                    break;
                }
            }
        }
        if (child == null) {
            child = new CharNode();
            parent.children.add(child);
        }
        child.data = data;
        if (child.freq == 0) child.freq = occur;
        if (word.length() > charAt + 1) {
            addWordRec(child, word, charAt + 1, occur);
        } else {
            child.terminal = true;
            child.freq = occur;
        }
    }

    byte[] dict;
    int dictSize;
    static final int CHAR_WIDTH = 8;
    static final int FLAGS_WIDTH = 1; // Terminal flag (word end)
    static final int ADDR_WIDTH = 23; // Offset to children
    static final int FREQ_WIDTH_BYTES = 1;
    static final int COUNT_WIDTH_BYTES = 1;

    private void addCount(int count) {
        dict[dictSize++] = (byte) (0xFF & count);
    }

    private void addNode(CharNode node, String word1) {
        if (node.terminal) { // store address of each word1
            mDictionary.put(word1, dictSize);
        }
        int charData = 0xFFFF & node.data;
        if (charData > 254) {
            dict[dictSize++] = (byte) 255;
            dict[dictSize++] = (byte) ((node.data >> 8) & 0xFF);
            dict[dictSize++] = (byte) (node.data & 0xFF);
        } else {
            dict[dictSize++] = (byte) (0xFF & node.data);
        }
        if (node.children != null) {
            dictSize += 3; // Space for children address
        } else {
            dictSize += 1; // Space for just the terminal/address flags
        }
        if ((0xFFFFFF & node.freq) > 255) {
            node.freq = 255;
        }
        if (node.terminal) {
            byte freq = (byte) (0xFF & node.freq);
            dict[dictSize++] = freq;
            // bigram
            if (bigramDict.mBi.containsKey(word1)) {
                int count = bigramDict.mBi.get(word1).count;
                bigramDict.mBigramToFill.add(word1);
                bigramDict.mBigramToFillAddress.add(dictSize);
                dictSize += (4 * count);
            } else {
                dict[dictSize++] = (byte) (0x00);
            }
        }
    }

    int nullChildrenCount = 0;
    int notTerminalCount = 0;

    private void updateNodeAddress(int nodeAddress, CharNode node,
            int childrenAddress) {
        if ((dict[nodeAddress] & 0xFF) == 0xFF) { // 3 byte character
            nodeAddress += 2;
        }
        childrenAddress = ADDRESS_MASK & childrenAddress;
        if (childrenAddress == 0) {
            nullChildrenCount++;
        } else {
            childrenAddress |= FLAG_ADDRESS_MASK;
        }
        if (node.terminal) {
            childrenAddress |= FLAG_TERMINAL_MASK;
        } else {
            notTerminalCount++;
        }
        dict[nodeAddress + 1] = (byte) (childrenAddress >> 16);
        if ((childrenAddress & FLAG_ADDRESS_MASK) != 0) {
            dict[nodeAddress + 2] = (byte) ((childrenAddress & 0xFF00) >> 8);
            dict[nodeAddress + 3] = (byte) ((childrenAddress & 0xFF));
        }
    }

    void writeWordsRec(List<CharNode> children, StringBuilder word) {
        if (children == null || children.size() == 0) {
            return;
        }
        final int childCount = children.size();
        addCount(childCount);
        int[] childrenAddresses = new int[childCount];
        for (int j = 0; j < childCount; j++) {
            CharNode node = children.get(j);
            childrenAddresses[j] = dictSize;
            word.append(children.get(j).data);
            addNode(node, word.toString());
            word.deleteCharAt(word.length()-1);
        }
        for (int j = 0; j < childCount; j++) {
            CharNode node = children.get(j);
            int nodeAddress = childrenAddresses[j];
            int cacheDictSize = dictSize;
            word.append(children.get(j).data);
            writeWordsRec(node.children, word);
            word.deleteCharAt(word.length()-1);
            updateNodeAddress(nodeAddress, node, node.children != null
                    ? cacheDictSize : 0);
        }
    }

    void writeToDict(String dictFilename) {
        // 4MB max, 22-bit offsets
        dict = new byte[4 * 1024 * 1024]; // 4MB upper limit. Actual is probably
                                          // < 1MB in most cases, as there is a limit in the
                                          // resource size in apks.
        dictSize = 0;

        dict[dictSize++] = (byte) (0xFF & VERSION_NUM); // version info
        dict[dictSize++] = (byte) (0xFF & (bigramDict.mHasBigram ? 1 : 0));

        StringBuilder word = new StringBuilder(48);
        writeWordsRec(roots, word);
        dict = bigramDict.writeBigrams(dict, mDictionary);
        System.out.println("Dict Size = " + dictSize);
        if (!sSplitOutput) {
            sOutputFileSize = dictSize;
        }
        try {
            int currentLoc = 0;
            int i = 0;
            int extension = dictFilename.indexOf(".dict");
            String filename = dictFilename.substring(0, extension);
            while (dictSize > 0) {
                FileOutputStream fos;
                if (sSplitOutput) {
                    fos = new FileOutputStream(filename + i + ".dict");
                } else {
                    fos = new FileOutputStream(filename + ".dict");
                }
                if (dictSize > sOutputFileSize) {
                    fos.write(dict, currentLoc, sOutputFileSize);
                    dictSize -= sOutputFileSize;
                    currentLoc += sOutputFileSize;
                } else {
                    fos.write(dict, currentLoc, dictSize);
                    dictSize = 0;
                }
                fos.close();
                i++;
            }
        } catch (IOException ioe) {
            System.err.println("Error writing dict file:" + ioe);
        }
    }

    void traverseDict(int pos, char[] word, int depth) {
        int count = dict[pos++] & 0xFF;
        for (int i = 0; i < count; i++) {
            char c = (char) (dict[pos++] & 0xFF);
            if (c == 0xFF) { // two byte character
                c = (char) (((dict[pos] & 0xFF) << 8) | (dict[pos+1] & 0xFF));
                pos += 2;
            }
            word[depth] = c;
            boolean terminal = getFirstBitOfByte(pos, dict);
            int address = 0;
            if ((dict[pos] & (FLAG_ADDRESS_MASK >> 16)) > 0) { // address check
                address = get22BitAddress(pos, dict);
                pos += 3;
            } else {
                pos += 1;
            }
            if (terminal) {
                showWord(word, depth + 1, dict[pos] & 0xFF);
                pos++;

                int bigramExist = (dict[pos] & bigramDict.FLAG_BIGRAM_READ);
                if (bigramExist > 0) {
                    int nextBigramExist = 1;
                    while (nextBigramExist > 0) {
                        int bigramAddress = get22BitAddress(pos, dict);
                        pos += 3;
                        int frequency = (bigramDict.FLAG_BIGRAM_FREQ & dict[pos]);
                        bigramDict.searchForTerminalNode(bigramAddress, frequency, dict);
                        nextBigramExist = (dict[pos++] & bigramDict.FLAG_BIGRAM_CONTINUED);
                    }
                } else {
                    pos++;
                }
            }
            if (address != 0) {
                traverseDict(address, word, depth + 1);
            }
        }
    }

    void showWord(char[] word, int size, int freq) {
        System.out.print(new String(word, 0, size) + " " + freq + "\n");
    }

    static int get22BitAddress(int pos, byte[] dict) {
        return ((dict[pos + 0] & 0x3F) << 16)
                | ((dict[pos + 1] & 0xFF) << 8)
                | ((dict[pos + 2] & 0xFF));
    }

    static boolean getFirstBitOfByte(int pos, byte[] dict) {
        return (dict[pos] & 0x80) > 0;
    }

    static boolean getSecondBitOfByte(int pos, byte[] dict) {
        return (dict[pos] & 0x40) > 0;
    }
}
