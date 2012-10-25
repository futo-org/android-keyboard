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

import com.android.inputmethod.latin.dicttool.BinaryDictOffdeviceUtils.DecoderChainSpec;
import com.android.inputmethod.latin.makedict.BinaryDictInputOutput;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.Word;

import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import javax.xml.parsers.ParserConfigurationException;

public class Info extends Dicttool.Command {
    public static final String COMMAND = "info";

    public Info() {
    }

    @Override
    public String getHelp() {
        return COMMAND + "<filename>: prints various information about a dictionary file";
    }

    private static void crash(final String filename, final Exception e) {
        throw new RuntimeException("Can't read file " + filename, e);
    }

    private static FusionDictionary getDictionary(final String filename) {
        final File file = new File(filename);
        System.out.println("Dictionary : " + file.getAbsolutePath());
        System.out.println("Size : " + file.length() + " bytes");
        try {
            if (XmlDictInputOutput.isXmlUnigramDictionary(filename)) {
                System.out.println("Format : XML unigram list");
                return XmlDictInputOutput.readDictionaryXml(
                        new BufferedInputStream(new FileInputStream(file)),
                        null /* shortcuts */, null /* bigrams */);
            } else if (CombinedInputOutput.isCombinedDictionary(filename)) {
                System.out.println("Format : Combined format");
                return CombinedInputOutput.readDictionaryCombined(
                        new BufferedInputStream(new FileInputStream(file)));
            } else {
                final DecoderChainSpec decodedSpec =
                        BinaryDictOffdeviceUtils.getRawBinaryDictionaryOrNull(file);
                if (null == decodedSpec) {
                    crash(filename, new RuntimeException(
                            filename + " does not seem to be a dictionary file"));
                }
                final FileInputStream inStream = new FileInputStream(decodedSpec.mFile);
                final ByteBuffer buffer = inStream.getChannel().map(
                        FileChannel.MapMode.READ_ONLY, 0, decodedSpec.mFile.length());
                System.out.println("Format : Binary dictionary format");
                System.out.println("Packaging : " + decodedSpec.describeChain());
                System.out.println("Uncompressed size : " + decodedSpec.mFile.length());
                return BinaryDictInputOutput.readDictionaryBinary(
                        new BinaryDictInputOutput.ByteBufferWrapper(buffer), null);
            }
        } catch (IOException e) {
            crash(filename, e);
        } catch (SAXException e) {
            crash(filename, e);
        } catch (ParserConfigurationException e) {
            crash(filename, e);
        } catch (UnsupportedFormatException e) {
            crash(filename, e);
        }
        return null;
    }

    private static void showInfo(final FusionDictionary dict) {
        System.out.println("Header attributes :");
        System.out.print(dict.mOptions.toString(2));
        int wordCount = 0;
        int bigramCount = 0;
        int shortcutCount = 0;
        int whitelistCount = 0;
        for (final Word w : dict) {
            ++wordCount;
            if (null != w.mBigrams) {
                bigramCount += w.mBigrams.size();
            }
            if (null != w.mShortcutTargets) {
                shortcutCount += w.mShortcutTargets.size();
                for (WeightedString shortcutTarget : w.mShortcutTargets) {
                    if (FormatSpec.SHORTCUT_WHITELIST_FREQUENCY == shortcutTarget.mFrequency) {
                        ++whitelistCount;
                    }
                }
            }
        }
        System.out.println("Words in the dictionary : " + wordCount);
        System.out.println("Bigram count : " + bigramCount);
        System.out.println("Shortcuts : " + shortcutCount + " (out of which " + whitelistCount
                + " whitelist entries)");
    }

    @Override
    public void run() {
        if (mArgs.length < 1) {
            throw new RuntimeException("Not enough arguments for command " + COMMAND);
        }
        final String filename = mArgs[0];
        final FusionDictionary dict = getDictionary(filename);
        showInfo(dict);
    }
}
