/**
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;

import java.io.File;
import java.util.Arrays;

public class Header extends Dicttool.Command {
    public static final String COMMAND = "header";

    public Header() {
    }

    @Override
    public String getHelp() {
        return COMMAND + " <filename>: prints the header contents of a dictionary file";
    }

    @Override
    public void run() throws UnsupportedFormatException {
        final boolean plumbing;
        if (mArgs.length > 0 && "-p".equals(mArgs[0])) {
            plumbing = true;
            mArgs = Arrays.copyOfRange(mArgs, 1, mArgs.length);
        } else {
            plumbing = false;
        }
        if (mArgs.length < 1) {
            throw new RuntimeException("Not enough arguments for command " + COMMAND);
        }
        final String filename = mArgs[0];
        final File dictFile = new File(filename);
        final DecoderChainSpec<DictionaryHeader> spec =
                BinaryDictOffdeviceUtils.decodeDictionaryForProcess(dictFile,
                        new BinaryDictOffdeviceUtils.HeaderReaderProcessor());
        if (null == spec) {
            throw new UnsupportedFormatException(filename
                    + " doesn't seem to be a valid version 2 dictionary file");
        }

        final DictionaryHeader header = spec.mResult;
        System.out.println("Dictionary : " + dictFile.getAbsolutePath());
        System.out.println("Size : " + dictFile.length() + " bytes");
        System.out.println("Format : Binary dictionary format");
        System.out.println("Format version : " + header.mFormatOptions.mVersion);
        System.out.println("Packaging : " + spec.describeChain());
        System.out.println("Header attributes :");
        System.out.print(header.mDictionaryOptions.toString(2 /* indentCount */, plumbing));
    }
}
