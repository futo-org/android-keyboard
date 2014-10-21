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
import com.android.inputmethod.latin.makedict.BinaryDictIOUtils;
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;

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

    private final static int COPY_BUFFER_SIZE = 8192;

    public static class DecoderChainSpec {
        public final static int COMPRESSION = 1;
        public final static int ENCRYPTION = 2;

        private final static int[][] VALID_DECODER_CHAINS = {
            { }, { COMPRESSION }, { ENCRYPTION, COMPRESSION }
        };

        private final int mDecoderSpecIndex;
        File mFile;

        public DecoderChainSpec() {
            mDecoderSpecIndex = 0;
            mFile = null;
        }

        private DecoderChainSpec(final DecoderChainSpec src) {
            mDecoderSpecIndex = src.mDecoderSpecIndex + 1;
            mFile = src.mFile;
        }

        private String getStepDescription(final int step) {
            switch (step) {
            case COMPRESSION:
                return "compression";
            case ENCRYPTION:
                return "encryption";
            default:
                return "unknown";
            }
        }

        public String describeChain() {
            final StringBuilder s = new StringBuilder("raw");
            for (final int step : VALID_DECODER_CHAINS[mDecoderSpecIndex]) {
                s.append(" > ");
                s.append(getStepDescription(step));
            }
            return s.toString();
        }

        /**
         * Returns the next sequential spec. If exhausted, return null.
         */
        public DecoderChainSpec next() {
            if (mDecoderSpecIndex + 1 >= VALID_DECODER_CHAINS.length) {
                return null;
            }
            return new DecoderChainSpec(this);
        }

        public InputStream getStream(final File src) throws FileNotFoundException, IOException {
            InputStream input = new BufferedInputStream(new FileInputStream(src));
            for (final int step : VALID_DECODER_CHAINS[mDecoderSpecIndex]) {
                switch (step) {
                case COMPRESSION:
                    input = Compress.getUncompressedStream(input);
                    break;
                case ENCRYPTION:
                    input = Crypt.getDecryptedStream(input);
                    break;
                }
            }
            return input;
        }
    }

    public static void copy(final InputStream input, final OutputStream output) throws IOException {
        final byte[] buffer = new byte[COPY_BUFFER_SIZE];
        for (int readBytes = input.read(buffer); readBytes >= 0; readBytes = input.read(buffer)) {
            output.write(buffer, 0, readBytes);
        }
    }

    /**
     * Returns a decrypted/uncompressed dictionary.
     *
     * This will decrypt/uncompress any number of times as necessary until it finds the
     * dictionary signature, and copy the decoded file to a temporary place.
     * If this is not a dictionary, the method returns null.
     */
    public static DecoderChainSpec getRawDictionaryOrNull(final File src) {
        DecoderChainSpec spec = new DecoderChainSpec();
        if (BinaryDictDecoderUtils.isBinaryDictionary(src)
                || CombinedInputOutput.isCombinedDictionary(src.getAbsolutePath())) {
            spec.mFile = src;
            return spec;
        }
        while (null != spec) {
            try {
                final File dst = File.createTempFile(PREFIX, SUFFIX);
                dst.deleteOnExit();
                try (final InputStream input = spec.getStream(src);
                        final OutputStream output =
                                new BufferedOutputStream(new FileOutputStream(dst))) {
                        copy(input, output);
                        output.flush();
                        output.close();
                        if (BinaryDictDecoderUtils.isBinaryDictionary(dst)
                                || CombinedInputOutput.isCombinedDictionary(
                                        dst.getAbsolutePath())) {
                            spec.mFile = dst;
                            return spec;
                        }
                    }
            } catch (IOException e) {
                // This was not the right format, fall through and try the next
                System.out.println("Rejecting " + spec.describeChain() + " : " + e);
                System.out.println(e.getStackTrace()[0].toString());
            }
            spec = spec.next();
        }
        return null;
    }

    static FusionDictionary getDictionary(final String filename, final boolean report) {
        final File file = new File(filename);
        if (report) {
            System.out.println("Dictionary : " + file.getAbsolutePath());
            System.out.println("Size : " + file.length() + " bytes");
        }
        try {
            final DecoderChainSpec decodedSpec = getRawDictionaryOrNull(file);
            if (null == decodedSpec) {
                throw new RuntimeException("Does not seem to be a dictionary file " + filename);
            }
            if (CombinedInputOutput.isCombinedDictionary(decodedSpec.mFile.getAbsolutePath())) {
                if (report) {
                    System.out.println("Format : Combined format");
                    System.out.println("Packaging : " + decodedSpec.describeChain());
                    System.out.println("Uncompressed size : " + decodedSpec.mFile.length());
                }
                try (final BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(decodedSpec.mFile), "UTF-8"))) {
                    return CombinedInputOutput.readDictionaryCombined(reader);
                }
            }
            final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(
                    decodedSpec.mFile, 0, decodedSpec.mFile.length(),
                    DictDecoder.USE_BYTEARRAY);
            if (report) {
                System.out.println("Format : Binary dictionary format");
                System.out.println("Packaging : " + decodedSpec.describeChain());
                System.out.println("Uncompressed size : " + decodedSpec.mFile.length());
            }
            return dictDecoder.readDictionaryBinary(false /* deleteDictIfBroken */);
        } catch (final IOException | UnsupportedFormatException e) {
            throw new RuntimeException("Can't read file " + filename, e);
        }
    }
}
