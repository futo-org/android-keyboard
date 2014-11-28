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
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FormatSpec.DictionaryOptions;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
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
import java.util.HashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    public static class DecoderChainSpec<T> {
        public final static int COMPRESSION = 1;
        public final static int ENCRYPTION = 2;

        private final static int[][] VALID_DECODER_CHAINS = {
            { }, { COMPRESSION }, { ENCRYPTION, COMPRESSION }
        };

        private final int mDecoderSpecIndex;
        public T mResult;

        public DecoderChainSpec() {
            mDecoderSpecIndex = 0;
            mResult = null;
        }

        private DecoderChainSpec(final DecoderChainSpec<T> src) {
            mDecoderSpecIndex = src.mDecoderSpecIndex + 1;
            mResult = src.mResult;
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

    public interface InputProcessor<T> {
        @Nonnull
        public T process(@Nonnull final InputStream input)
                throws IOException, UnsupportedFormatException;
    }

    public static class CopyProcessor implements InputProcessor<File> {
        @Override @Nonnull
        public File process(@Nonnull final InputStream input) throws IOException,
                UnsupportedFormatException {
            final File dst = File.createTempFile(PREFIX, SUFFIX);
            dst.deleteOnExit();
            try (final OutputStream output = new BufferedOutputStream(new FileOutputStream(dst))) {
                copy(input, output);
                output.flush();
                output.close();
                if (BinaryDictDecoderUtils.isBinaryDictionary(dst)
                        || CombinedInputOutput.isCombinedDictionary(dst.getAbsolutePath())) {
                    return dst;
                }
            }
            throw new UnsupportedFormatException("Input stream not at the expected format");
        }
    }

    public static class HeaderReaderProcessor implements InputProcessor<DictionaryHeader> {
        // Arbitrarily limit the header length to 32k. Sounds like it would never be larger
        // than this. Revisit this if needed later.
        private final int MAX_HEADER_LENGTH = 32 * 1024;
        @Override @Nonnull
        public DictionaryHeader process(final InputStream input) throws IOException,
                UnsupportedFormatException {
            // Do everything as curtly and ad-hoc as possible for performance.
            final byte[] tmpBuffer = new byte[12];
            if (tmpBuffer.length != input.read(tmpBuffer)) {
                throw new UnsupportedFormatException("File too short, not a dictionary");
            }
            // Ad-hoc check for the magic number. See FormatSpec.java as well as
            // byte_array_utils.h and BinaryDictEncoderUtils#writeDictionaryHeader().
            final int MAGIC_NUMBER_START_OFFSET = 0;
            final int VERSION_START_OFFSET = 4;
            final int HEADER_SIZE_OFFSET = 8;
            final int magicNumber = ((tmpBuffer[MAGIC_NUMBER_START_OFFSET] & 0xFF) << 24)
                    + ((tmpBuffer[MAGIC_NUMBER_START_OFFSET + 1] & 0xFF) << 16)
                    + ((tmpBuffer[MAGIC_NUMBER_START_OFFSET + 2] & 0xFF) << 8)
                    + (tmpBuffer[MAGIC_NUMBER_START_OFFSET + 3] & 0xFF);
            if (magicNumber != FormatSpec.MAGIC_NUMBER) {
                throw new UnsupportedFormatException("Wrong magic number");
            }
            final int version = ((tmpBuffer[VERSION_START_OFFSET] & 0xFF) << 8)
                    + (tmpBuffer[VERSION_START_OFFSET + 1] & 0xFF);
            if (version != FormatSpec.VERSION2 && version != FormatSpec.VERSION201
                    && version != FormatSpec.VERSION202) {
                throw new UnsupportedFormatException("Only versions 2, 201, 202 are supported");
            }
            final int totalHeaderSize = ((tmpBuffer[HEADER_SIZE_OFFSET] & 0xFF) << 24)
                    + ((tmpBuffer[HEADER_SIZE_OFFSET + 1] & 0xFF) << 16)
                    + ((tmpBuffer[HEADER_SIZE_OFFSET + 2] & 0xFF) << 8)
                    + (tmpBuffer[HEADER_SIZE_OFFSET + 3] & 0xFF);
            if (totalHeaderSize > MAX_HEADER_LENGTH) {
                throw new UnsupportedFormatException("Header too large");
            }
            final byte[] headerBuffer = new byte[totalHeaderSize - tmpBuffer.length];
            readStreamExhaustively(input, headerBuffer);
            final HashMap<String, String> attributes =
                    BinaryDictDecoderUtils.decodeHeaderAttributes(headerBuffer);
            return new DictionaryHeader(totalHeaderSize, new DictionaryOptions(attributes),
                    new FormatOptions(version, false /* hasTimestamp */));
        }
    }

    private static void readStreamExhaustively(final InputStream inputStream,
            final byte[] outBuffer) throws IOException, UnsupportedFormatException {
        int readBytes = 0;
        int readBytesLastCycle = -1;
        while (readBytes != outBuffer.length) {
            readBytesLastCycle = inputStream.read(outBuffer, readBytes,
                    outBuffer.length - readBytes);
            if (readBytesLastCycle == -1)
                throw new UnsupportedFormatException("File shorter than specified in the header"
                        + " (expected " + outBuffer.length + ", read " + readBytes + ")");
            readBytes += readBytesLastCycle;
        }
    }

    public static void copy(final InputStream input, final OutputStream output) throws IOException {
        final byte[] buffer = new byte[COPY_BUFFER_SIZE];
        for (int readBytes = input.read(buffer); readBytes >= 0; readBytes = input.read(buffer)) {
            output.write(buffer, 0, readBytes);
        }
    }

    /**
     * Process a dictionary, decrypting/uncompressing it on the fly as necessary.
     *
     * This will execute the given processor repeatedly with the possible alternatives
     * for dictionary format until the processor does not throw an exception.
     * If the processor succeeds for none of the possible formats, the method returns null.
     */
    @Nullable
    public static <T> DecoderChainSpec<T> decodeDictionaryForProcess(@Nonnull final File src,
            @Nonnull final InputProcessor<T> processor) {
        @Nonnull DecoderChainSpec spec = new DecoderChainSpec();
        while (null != spec) {
            try {
                final InputStream input = spec.getStream(src);
                spec.mResult = processor.process(input);
                try {
                    input.close();
                } catch (IOException e) {
                    // CipherInputStream doesn't like being closed without having read the
                    // entire stream, for some reason. But we don't want to because it's a waste
                    // of resources. We really, really don't care about this.
                    // However on close() CipherInputStream does throw this exception, wrapped
                    // in an IOException so we need to catch it.
                    if (!(e.getCause() instanceof javax.crypto.BadPaddingException)) {
                        throw e;
                    }
                }
                return spec;
            } catch (IOException | UnsupportedFormatException | ArrayIndexOutOfBoundsException e) {
                // If the format is not the right one for this file, the processor will throw one
                // of these exceptions. In our case, that means we should try the next spec,
                // since it may still be at another format we haven't tried yet.
                // TODO: stop using exceptions for this non-exceptional case.
            }
            spec = spec.next();
        }
        return null;
    }

    /**
     * Get a decoder chain spec with a raw dictionary file. This makes a new file on the
     * disk ready for any treatment the client wants.
     */
    @Nullable
    public static DecoderChainSpec<File> getRawDictionaryOrNull(@Nonnull final File src) {
        return decodeDictionaryForProcess(src, new CopyProcessor());
    }

    static FusionDictionary getDictionary(final String filename, final boolean report) {
        final File file = new File(filename);
        if (report) {
            System.out.println("Dictionary : " + file.getAbsolutePath());
            System.out.println("Size : " + file.length() + " bytes");
        }
        try {
            final DecoderChainSpec<File> decodedSpec = getRawDictionaryOrNull(file);
            if (null == decodedSpec) {
                throw new RuntimeException("Does not seem to be a dictionary file " + filename);
            }
            if (CombinedInputOutput.isCombinedDictionary(decodedSpec.mResult.getAbsolutePath())) {
                if (report) {
                    System.out.println("Format : Combined format");
                    System.out.println("Packaging : " + decodedSpec.describeChain());
                    System.out.println("Uncompressed size : " + decodedSpec.mResult.length());
                }
                try (final BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(decodedSpec.mResult), "UTF-8"))) {
                    return CombinedInputOutput.readDictionaryCombined(reader);
                }
            }
            final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(
                    decodedSpec.mResult, 0, decodedSpec.mResult.length(),
                    DictDecoder.USE_BYTEARRAY);
            if (report) {
                System.out.println("Format : Binary dictionary format");
                System.out.println("Packaging : " + decodedSpec.describeChain());
                System.out.println("Uncompressed size : " + decodedSpec.mResult.length());
            }
            return dictDecoder.readDictionaryBinary(false /* deleteDictIfBroken */);
        } catch (final IOException | UnsupportedFormatException e) {
            throw new RuntimeException("Can't read file " + filename, e);
        }
    }
}
