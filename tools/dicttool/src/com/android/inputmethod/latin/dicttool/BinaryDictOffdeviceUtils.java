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

import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils;
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Class grouping utilities for offline dictionary making.
 *
 * Those should not be used on-device, essentially because they are quite
 * liberal about I/O and performance.
 */
public final class BinaryDictOffdeviceUtils {
    // Prefix and suffix are arbitrary, the values do not really matter
    private final static String PREFIX = "dicttool";
    private final static String SUFFIX = ".tmp";

    public final static String COMPRESSION = "compressed";
    public final static String ENCRYPTION = "encrypted";

    private final static int MAX_DECODE_DEPTH = 8;

    public static class DecoderChainSpec {
        ArrayList<String> mDecoderSpec = new ArrayList<String>();
        File mFile;
        public DecoderChainSpec addStep(final String stepDescription) {
            mDecoderSpec.add(stepDescription);
            return this;
        }
        public String describeChain() {
            final StringBuilder s = new StringBuilder("raw");
            for (final String step : mDecoderSpec) {
                s.append(" > ");
                s.append(step);
            }
            return s.toString();
        }
    }

    public static void copy(final InputStream input, final OutputStream output) throws IOException {
        final byte[] buffer = new byte[1000];
        final BufferedInputStream in = new BufferedInputStream(input);
        final BufferedOutputStream out = new BufferedOutputStream(output);
        for (int readBytes = in.read(buffer); readBytes >= 0; readBytes = in.read(buffer))
            output.write(buffer, 0, readBytes);
        in.close();
        out.close();
    }

    /**
     * Returns a decrypted/uncompressed dictionary.
     *
     * This will decrypt/uncompress any number of times as necessary until it finds the
     * dictionary signature, and copy the decoded file to a temporary place.
     * If this is not a dictionary, the method returns null.
     */
    public static DecoderChainSpec getRawDictionaryOrNull(final File src) {
        return getRawDictionaryOrNullInternal(new DecoderChainSpec(), src, 0);
    }

    private static DecoderChainSpec getRawDictionaryOrNullInternal(
            final DecoderChainSpec spec, final File src, final int depth) {
        // Unfortunately the decoding scheme we use can consider any data to be encrypted
        // and will product some output, meaning it's not possible to reliably detect encrypted
        // data. Thus, some non-dictionary files (especially small) ones may successfully decrypt
        // over and over, ending in a stack overflow. Hence we limit the depth at which we try
        // decoding the file.
        if (depth > MAX_DECODE_DEPTH) return null;
        if (BinaryDictDecoderUtils.isBinaryDictionary(src)
                || CombinedInputOutput.isCombinedDictionary(src.getAbsolutePath())) {
            spec.mFile = src;
            return spec;
        }
        // It's not a raw dictionary - try to see if it's compressed.
        final File uncompressedFile = tryGetUncompressedFile(src);
        if (null != uncompressedFile) {
            final DecoderChainSpec newSpec =
                    getRawDictionaryOrNullInternal(spec, uncompressedFile, depth + 1);
            if (null == newSpec) return null;
            return newSpec.addStep(COMPRESSION);
        }
        // It's not a compressed either - try to see if it's crypted.
        final File decryptedFile = tryGetDecryptedFile(src);
        if (null != decryptedFile) {
            final DecoderChainSpec newSpec =
                    getRawDictionaryOrNullInternal(spec, decryptedFile, depth + 1);
            if (null == newSpec) return null;
            return newSpec.addStep(ENCRYPTION);
        }
        return null;
    }

    /* Try to uncompress the file passed as an argument.
     *
     * If the file can be uncompressed, the uncompressed version is returned. Otherwise, null
     * is returned.
     */
    private static File tryGetUncompressedFile(final File src) {
        try {
            final File dst = File.createTempFile(PREFIX, SUFFIX);
            dst.deleteOnExit();
            final FileOutputStream dstStream = new FileOutputStream(dst);
            copy(Compress.getUncompressedStream(new BufferedInputStream(new FileInputStream(src))),
                    new BufferedOutputStream(dstStream)); // #copy() closes the streams
            return dst;
        } catch (IOException e) {
            // Could not uncompress the file: presumably the file is simply not a compressed file
            return null;
        }
    }

    /* Try to decrypt the file passed as an argument.
     *
     * If the file can be decrypted, the decrypted version is returned. Otherwise, null
     * is returned.
     */
    private static File tryGetDecryptedFile(final File src) {
        try {
            final File dst = File.createTempFile(PREFIX, SUFFIX);
            dst.deleteOnExit();
            final FileOutputStream dstStream = new FileOutputStream(dst);
            copy(Crypt.getDecryptedStream(new BufferedInputStream(new FileInputStream(src))),
                    dstStream); // #copy() closes the streams
            return dst;
        } catch (IOException e) {
            // Could not decrypt the file: presumably the file is simply not a crypted file
            return null;
        }
    }

    static void crash(final String filename, final Exception e) {
        throw new RuntimeException("Can't read file " + filename, e);
    }

    static FusionDictionary getDictionary(final String filename, final boolean report) {
        final File file = new File(filename);
        if (report) {
            System.out.println("Dictionary : " + file.getAbsolutePath());
            System.out.println("Size : " + file.length() + " bytes");
        }
        try {
            if (XmlDictInputOutput.isXmlUnigramDictionary(filename)) {
                if (report) System.out.println("Format : XML unigram list");
                return XmlDictInputOutput.readDictionaryXml(
                        new BufferedInputStream(new FileInputStream(file)),
                        null /* shortcuts */, null /* bigrams */);
            } else {
                final DecoderChainSpec decodedSpec = getRawDictionaryOrNull(file);
                if (null == decodedSpec) {
                    crash(filename, new RuntimeException(
                            filename + " does not seem to be a dictionary file"));
                } else if (CombinedInputOutput.isCombinedDictionary(
                        decodedSpec.mFile.getAbsolutePath())){
                    if (report) {
                        System.out.println("Format : Combined format");
                        System.out.println("Packaging : " + decodedSpec.describeChain());
                        System.out.println("Uncompressed size : " + decodedSpec.mFile.length());
                    }
                    return CombinedInputOutput.readDictionaryCombined(
                            new BufferedInputStream(new FileInputStream(decodedSpec.mFile)));
                } else {
                    final DictDecoder dictDecoder = FormatSpec.getDictDecoder(decodedSpec.mFile,
                            DictDecoder.USE_BYTEARRAY);
                    if (report) {
                        System.out.println("Format : Binary dictionary format");
                        System.out.println("Packaging : " + decodedSpec.describeChain());
                        System.out.println("Uncompressed size : " + decodedSpec.mFile.length());
                    }
                    return dictDecoder.readDictionaryBinary(null, false /* deleteDictIfBroken */);
                }
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
}
