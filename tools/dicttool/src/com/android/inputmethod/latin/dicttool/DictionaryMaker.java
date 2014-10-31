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

import com.android.inputmethod.latin.makedict.BinaryDictDecoderUtils;
import com.android.inputmethod.latin.makedict.BinaryDictIOUtils;
import com.android.inputmethod.latin.makedict.DictDecoder;
import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.MakedictLog;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.Ver2DictEncoder;
import com.android.inputmethod.latin.makedict.Ver4DictEncoder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * Main class/method for DictionaryMaker.
 */
public class DictionaryMaker {

    static class Arguments {
        private static final String OPTION_VERSION_2 = "-2";
        private static final String OPTION_VERSION_4 = "-4";
        private static final String OPTION_INPUT_SOURCE = "-s";
        private static final String OPTION_OUTPUT_BINARY = "-d";
        private static final String OPTION_OUTPUT_COMBINED = "-o";
        private static final String OPTION_HELP = "-h";
        private static final String OPTION_CODE_POINT_TABLE = "-t";
        private static final String OPTION_CODE_POINT_TABLE_OFF = "off";
        private static final String OPTION_CODE_POINT_TABLE_ON = "on";
        public final String mInputBinary;
        public final String mInputCombined;
        public final String mOutputBinary;
        public final String mOutputCombined;
        public final int mOutputBinaryFormatVersion;
        public final int mCodePointTableMode;

        private void checkIntegrity() throws IOException {
            checkHasExactlyOneInput();
            checkHasAtLeastOneOutput();
            checkNotSameFile(mInputBinary, mOutputBinary);
            checkNotSameFile(mInputCombined, mOutputBinary);
            checkNotSameFile(mOutputBinary, mOutputCombined);
        }

        private void checkHasExactlyOneInput() {
            if (null == mInputBinary && null == mInputCombined) {
                throw new RuntimeException("No input file specified");
            } else if (null != mInputBinary && null != mInputCombined) {
                throw new RuntimeException("Several input files specified");
            }
        }

        private void checkHasAtLeastOneOutput() {
            if (null == mOutputBinary && null == mOutputCombined) {
                throw new RuntimeException("No output specified");
            }
        }

        /**
         * Utility method that throws an exception if path1 and path2 point to the same file.
         */
        private static void checkNotSameFile(final String path1, final String path2)
                throws IOException {
            if (null == path1 || null == path2) return;
            if (new File(path1).getCanonicalPath().equals(new File(path2).getCanonicalPath())) {
                throw new RuntimeException(path1 + " and " + path2 + " are the same file: "
                        + " refusing to process.");
            }
        }

        private static void displayHelp() {
            MakedictLog.i(getHelp());
        }

        public static String getHelp() {
            return "Usage: makedict "
                    + "| [-s <combined format input]"
                    + "| [-s <binary input>] [-d <binary output>]"
                    + " [-o <combined output>] [-t <code point table switch: on/off/auto>]"
                    + "[-2] [-3] [-4]\n"
                    + "\n"
                    + "  Converts a source dictionary file to one or several outputs.\n"
                    + "  Source can be a binary dictionary file or a combined format file.\n"
                    + "  Binary version 2 (Jelly Bean), 3, 4, and\n"
                    + "  combined format outputs are supported.";
        }

        public Arguments(String[] argsArray) throws IOException {
            final LinkedList<String> args = new LinkedList<>(Arrays.asList(argsArray));
            if (args.isEmpty()) {
                displayHelp();
            }
            String inputBinary = null;
            String inputCombined = null;
            String outputBinary = null;
            String outputCombined = null;
            int outputBinaryFormatVersion = FormatSpec.VERSION202; // the default version is 202.
            // Don't use code point table by default.
            int codePointTableMode = Ver2DictEncoder.CODE_POINT_TABLE_OFF;

            while (!args.isEmpty()) {
                final String arg = args.get(0);
                args.remove(0);
                if (arg.charAt(0) == '-') {
                    if (OPTION_VERSION_2.equals(arg)) {
                        // Do nothing, this is the default
                    } else if (OPTION_VERSION_4.equals(arg)) {
                        outputBinaryFormatVersion = FormatSpec.VERSION4;
                    } else if (OPTION_HELP.equals(arg)) {
                        displayHelp();
                    } else {
                        // All these options need an argument
                        if (args.isEmpty()) {
                            throw new IllegalArgumentException("Option " + arg + " is unknown or "
                                    + "requires an argument");
                        }
                        String argValue = args.get(0);
                        args.remove(0);
                        if (OPTION_INPUT_SOURCE.equals(arg)) {
                            if (CombinedInputOutput.isCombinedDictionary(argValue)) {
                                inputCombined = argValue;
                            } else if (BinaryDictDecoderUtils.isBinaryDictionary(argValue)) {
                                inputBinary = argValue;
                            } else {
                                throw new IllegalArgumentException(
                                        "Unknown format for file " + argValue);
                            }
                        } else if (OPTION_OUTPUT_BINARY.equals(arg)) {
                            outputBinary = argValue;
                        } else if (OPTION_OUTPUT_COMBINED.equals(arg)) {
                            outputCombined = argValue;
                        } else if (OPTION_CODE_POINT_TABLE.equals(arg)) {
                            if (OPTION_CODE_POINT_TABLE_OFF.equals(argValue)) {
                                codePointTableMode = Ver2DictEncoder.CODE_POINT_TABLE_OFF;
                            } else if (OPTION_CODE_POINT_TABLE_ON.equals(argValue)) {
                                codePointTableMode = Ver2DictEncoder.CODE_POINT_TABLE_ON;
                            } else {
                                throw new IllegalArgumentException(
                                        "Unknown argument to -t option : " + argValue);
                            }
                        } else {
                            throw new IllegalArgumentException("Unknown option : " + arg);
                        }
                    }
                } else {
                    if (null == inputBinary) {
                        if (BinaryDictDecoderUtils.isBinaryDictionary(arg)) {
                            inputBinary = arg;
                        } else if (CombinedInputOutput.isCombinedDictionary(arg)) {
                            inputCombined = arg;
                        } else {
                            throw new IllegalArgumentException("Unknown format for file " + arg);
                        }
                    } else if (null == outputBinary) {
                        outputBinary = arg;
                    } else {
                        throw new IllegalArgumentException("Several output binary files specified");
                    }
                }
            }

            mInputBinary = inputBinary;
            mInputCombined = inputCombined;
            mOutputBinary = outputBinary;
            mOutputCombined = outputCombined;
            mOutputBinaryFormatVersion = outputBinaryFormatVersion;
            mCodePointTableMode = codePointTableMode;
            checkIntegrity();
        }
    }

    public static void main(String[] args)
            throws FileNotFoundException, IOException, UnsupportedFormatException {
        final Arguments parsedArgs = new Arguments(args);
        FusionDictionary dictionary = readInputFromParsedArgs(parsedArgs);
        writeOutputToParsedArgs(parsedArgs, dictionary);
    }

    /**
     * Invoke the right input method according to args.
     *
     * @param args the parsed command line arguments.
     * @return the read dictionary.
     */
    private static FusionDictionary readInputFromParsedArgs(final Arguments args)
            throws IOException, UnsupportedFormatException, FileNotFoundException {
        if (null != args.mInputBinary) {
            return readBinaryFile(args.mInputBinary);
        } else if (null != args.mInputCombined) {
            return readCombinedFile(args.mInputCombined);
        } else {
            throw new RuntimeException("No input file specified");
        }
    }

    /**
     * Read a dictionary from the name of a binary file.
     *
     * @param binaryFilename the name of the file in the binary dictionary format.
     * @return the read dictionary.
     * @throws FileNotFoundException if the file can't be found
     * @throws IOException if the input file can't be read
     * @throws UnsupportedFormatException if the binary file is not in the expected format
     */
    private static FusionDictionary readBinaryFile(final String binaryFilename)
            throws FileNotFoundException, IOException, UnsupportedFormatException {
        final File file = new File(binaryFilename);
        final DictDecoder dictDecoder = BinaryDictIOUtils.getDictDecoder(file, 0, file.length());
        return dictDecoder.readDictionaryBinary(false /* deleteDictIfBroken */);
    }

    /**
     * Read a dictionary from the name of a combined file.
     *
     * @param combinedFilename the name of the file in the combined format.
     * @return the read dictionary.
     * @throws FileNotFoundException if the file can't be found
     * @throws IOException if the input file can't be read
     */
    private static FusionDictionary readCombinedFile(final String combinedFilename)
        throws FileNotFoundException, IOException {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(combinedFilename), "UTF-8"))
        ) {
            return CombinedInputOutput.readDictionaryCombined(reader);
        }
    }

    /**
     * Invoke the right output method according to args.
     *
     * This will write the passed dictionary to the file(s) passed in the command line arguments.
     * @param args the parsed arguments.
     * @param dict the file to output.
     * @throws FileNotFoundException if one of the output files can't be created.
     * @throws IOException if one of the output files can't be written to.
     */
    private static void writeOutputToParsedArgs(final Arguments args, final FusionDictionary dict)
            throws FileNotFoundException, IOException, UnsupportedFormatException,
            IllegalArgumentException {
        if (null != args.mOutputBinary) {
            writeBinaryDictionary(args.mOutputBinary, dict, args.mOutputBinaryFormatVersion,
                    args.mCodePointTableMode);
        }
        if (null != args.mOutputCombined) {
            writeCombinedDictionary(args.mOutputCombined, dict);
        }
    }

    /**
     * Write the dictionary in binary format to the specified filename.
     *
     * @param outputFilename the name of the file to write to.
     * @param dict the dictionary to write.
     * @param version the binary format version to use.
     * @param codePointTableMode the value to decide how we treat the code point table.
     * @throws FileNotFoundException if the output file can't be created.
     * @throws IOException if the output file can't be written to.
     */
    private static void writeBinaryDictionary(final String outputFilename,
            final FusionDictionary dict, final int version, final int codePointTableMode)
            throws FileNotFoundException, IOException, UnsupportedFormatException {
        final File outputFile = new File(outputFilename);
        final FormatSpec.FormatOptions formatOptions = new FormatSpec.FormatOptions(version);
        final DictEncoder dictEncoder;
        if (version == FormatSpec.VERSION4) {
            // VERSION4 doesn't use the code point table.
            dictEncoder = new Ver4DictEncoder(outputFile);
        } else {
            dictEncoder = new Ver2DictEncoder(outputFile, codePointTableMode);
        }
        dictEncoder.writeDictionary(dict, formatOptions);
    }

    /**
     * Write the dictionary in the combined format to the specified filename.
     *
     * @param outputFilename the name of the file to write to.
     * @param dict the dictionary to write.
     * @throws FileNotFoundException if the output file can't be created.
     * @throws IOException if the output file can't be written to.
     */
    private static void writeCombinedDictionary(final String outputFilename,
            final FusionDictionary dict) throws FileNotFoundException, IOException {
        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilename))) {
            CombinedInputOutput.writeDictionaryCombined(writer, dict);
        }
    }
}
