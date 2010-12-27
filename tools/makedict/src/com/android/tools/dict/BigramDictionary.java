/*
 * Copyright (C) 2010 The Android Open Source Project
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Helper for MakeBinaryDictionary
 * Deals with all the bigram data
 */
public class BigramDictionary {

    /*
     * Must match the values in the client side which is located in dictionary.cpp & dictionary.h
     * Changing these values will generate totally different structure which must be also reflected
     * on the client side.
     */
    public static final int FLAG_BIGRAM_READ = 0x80;
    public static final int FLAG_BIGRAM_CHILDEXIST = 0x40;
    public static final int FLAG_BIGRAM_CONTINUED = 0x80;
    public static final int FLAG_BIGRAM_FREQ = 0x7F;

    public static final int FOR_REVERSE_LOOKUPALL = -99;

    public ArrayList<String> mBigramToFill = new ArrayList<String>();
    public ArrayList<Integer> mBigramToFillAddress = new ArrayList<Integer>();

    public HashMap<String, Bigram> mBi;

    public boolean mHasBigram;

    public BigramDictionary(String bigramSrcFilename, boolean hasBigram) {
        mHasBigram = hasBigram;
        loadBigram(bigramSrcFilename);
    }

    private void loadBigram(String filename) {
        mBi = new HashMap<String, Bigram>();
        if (!mHasBigram) {
            System.out.println("Number of bigrams = " + Bigram.sBigramNum);
            return;
        }
        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            parser.parse(new File(filename), new DefaultHandler() {
                String w1 = null;
                boolean inWord1 = false;
                boolean inWord2 = false;
                int freq = 0, counter = 0;
                Bigram tempBigram = null;

                @Override
                public void startElement(String uri, String localName,
                        String qName, Attributes attributes) {
                    if (qName.equals("bi")) {
                        inWord1 = true;
                        w1 = attributes.getValue(0);
                        int count = Integer.parseInt(attributes.getValue(1));
                        tempBigram = new Bigram(count);
                        counter = 0;
                    } else if (qName.equals("w")) {
                        inWord2 = true;
                        String word2 = attributes.getValue(0);
                        int freq = Integer.parseInt(attributes.getValue(1));
                        tempBigram.setWord2(counter, word2, freq);
                        counter++;
                        Bigram.sBigramNum++;
                    }
                }

                @Override
                public void endElement(String uri, String localName,
                        String qName) {
                    if (inWord2) {
                        inWord2 = false;
                    } else if (inWord1) {
                        inWord1 = false;
                        mBi.put(w1, tempBigram);
                    }
                }
            });
        } catch (Exception ioe) {
            System.err.println("Exception in parsing bigram\n" + ioe);
            ioe.printStackTrace();
        }
        System.out.println("Number of bigrams = " + Bigram.sBigramNum);
    }

    byte[] writeBigrams(byte[] dict, Map<String, Integer> mDictionary) {
        for (int i = 0; i < mBigramToFill.size(); i++) {
            String w1 = mBigramToFill.get(i);
            int address = mBigramToFillAddress.get(i);

            Bigram temp = mBi.get(w1);
            int word2Count = temp.count;
            int j4;
            for (int j = 0; j < word2Count; j++) {
                if (!mDictionary.containsKey(temp.word2[j])) {
                    System.out.println("Not in dictionary: " + temp.word2[j]);
                    System.exit(0);
                } else {
                    j4 = (j * 4);
                    int addressOfWord2 = mDictionary.get(temp.word2[j]);
                    dict[address + j4 + 0] = (byte) (((addressOfWord2 & 0x3F0000) >> 16)
                            | FLAG_BIGRAM_READ);
                    dict[address + j4 + 1] = (byte) ((addressOfWord2 & 0x00FF00) >> 8);
                    dict[address + j4 + 2] = (byte) ((addressOfWord2 & 0x0000FF));

                    if (j == (word2Count - 1)) {
                        dict[address + j4 + 3] = (byte) (temp.freq[j] & FLAG_BIGRAM_FREQ);
                    } else {
                        dict[address + j4 + 3] = (byte) ((temp.freq[j] & FLAG_BIGRAM_FREQ)
                                | FLAG_BIGRAM_CONTINUED);
                    }
                }
            }
        }

        return dict;
    }

    void reverseLookupAll(Map<String, Integer> mDictionary, byte[] dict) {
        Set<String> st = mDictionary.keySet();
        for (String s : st) {
            searchForTerminalNode(mDictionary.get(s), FOR_REVERSE_LOOKUPALL, dict);
        }
    }

    void searchForTerminalNode(int bigramAddress, int frequency, byte[] dict) {
        StringBuilder sb = new StringBuilder(48);
        int pos;
        boolean found = false;
        int followDownBranchAddress = 2;
        char followingChar = ' ';
        int depth = 0;
        int totalLoopCount = 0;

        while (!found) {
            boolean followDownAddressSearchStop = false;
            boolean firstAddress = true;
            boolean haveToSearchAll = true;

            if (depth > 0) {
                sb.append(followingChar);
            }
            pos = followDownBranchAddress; // pos start at count
            int count = dict[pos] & 0xFF;
            pos++;
            for (int i = 0; i < count; i++) {
                totalLoopCount++;
                // pos at data
                pos++;
                // pos now at flag
                if (!MakeBinaryDictionary.getFirstBitOfByte(pos, dict)) { // non-terminal
                    if (!followDownAddressSearchStop) {
                        int addr = MakeBinaryDictionary.get22BitAddress(pos, dict);
                        if (addr > bigramAddress) {
                            followDownAddressSearchStop = true;
                            if (firstAddress) {
                                firstAddress = false;
                                haveToSearchAll = true;
                            } else if (!haveToSearchAll) {
                                break;
                            }
                        } else {
                            followDownBranchAddress = addr;
                            followingChar = (char) (0xFF & dict[pos-1]);
                            if(firstAddress) {
                                firstAddress = false;
                                haveToSearchAll = false;
                            }
                        }
                    }
                    pos += 3;
                } else if (MakeBinaryDictionary.getFirstBitOfByte(pos, dict)) { // terminal
                    // found !!
                    if (bigramAddress == (pos-1)) {
                        sb.append((char) (0xFF & dict[pos-1]));
                        found = true;
                        break;
                    }

                    // address + freq (4 byte)
                    if (MakeBinaryDictionary.getSecondBitOfByte(pos, dict)) {
                        if (!followDownAddressSearchStop) {
                            int addr = MakeBinaryDictionary.get22BitAddress(pos, dict);
                            if (addr > bigramAddress) {
                                followDownAddressSearchStop = true;
                                if (firstAddress) {
                                    firstAddress = false;
                                    haveToSearchAll = true;
                                } else if (!haveToSearchAll) {
                                    break;
                                }
                            } else {
                                followDownBranchAddress = addr;
                                followingChar = (char) (0xFF & dict[pos-1]);
                                if(firstAddress) {
                                    firstAddress = false;
                                    haveToSearchAll = true;
                                }
                            }
                        }
                        pos += 4;
                    } else { // freq only (2 byte)
                        pos += 2;
                    }
                    // skipping bigram
                    int bigramExist = (dict[pos] & FLAG_BIGRAM_READ);
                    if (bigramExist > 0) {
                        int nextBigramExist = 1;
                        while (nextBigramExist > 0) {
                            pos += 3;
                            nextBigramExist = (dict[pos++] & FLAG_BIGRAM_CONTINUED);
                        }
                    } else {
                        pos++;
                    }
                }
            }
            depth++;
            if (followDownBranchAddress == 2) {
                System.out.println("ERROR!!! Cannot find bigram!!");
                System.exit(0);
            }
        }

        if (frequency == FOR_REVERSE_LOOKUPALL) {
            System.out.println("Reverse: " + sb.toString() + " (" + bigramAddress + ")"
                    + "   Loop: " + totalLoopCount);
        } else {
            System.out.println("   bigram: " + sb.toString() + " (" + bigramAddress + ") freq: "
                    + frequency + "   Loop: " + totalLoopCount);
        }
    }

    static class Bigram {
        String[] word2;
        int[] freq;
        int count;
        static int sBigramNum = 0;

        String getSecondWord(int i) {
            return word2[i];
        }

        int getFrequency(int i) {
            return (freq[i] == 0) ? 1 : freq[i];
        }

        void setWord2(int index, String word2, int freq) {
            this.word2[index] = word2;
            this.freq[index] = freq;
        }

        public Bigram(int word2Count) {
            count = word2Count;
            word2 = new String[word2Count];
            freq = new int[word2Count];
        }
    }
}
