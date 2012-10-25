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

import com.android.inputmethod.latin.makedict.FusionDictionary;

import java.util.Arrays;

public class Diff extends Dicttool.Command {
    public static final String COMMAND = "diff";

    public Diff() {
    }

    @Override
    public String getHelp() {
        return COMMAND + " [-p] <dict> <dict> : shows differences between two dictionaries.\n"
                + "  If -p (porcelain) option is given, produce output suitable for a script";
    }

    @Override
    public void run() {
        if (mArgs.length < 2) {
            throw new RuntimeException("Not enough arguments for command " + COMMAND);
        }
        final boolean porcelain;
        if ("-p".equals(mArgs[0])) {
            porcelain = true;
            mArgs = Arrays.copyOfRange(mArgs, 1, mArgs.length);
            if (mArgs.length != 2) { // There should be only 2 arguments left
                throw new RuntimeException("Wrong number of arguments for command " + COMMAND);
            }
        } else {
            porcelain = false;
        }
        final FusionDictionary dict0 =
                BinaryDictOffdeviceUtils.getDictionary(mArgs[0], false /* report */);
        if (null == dict0) throw new RuntimeException("Can't read dictionary " + mArgs[0]);
        final FusionDictionary dict1 =
                BinaryDictOffdeviceUtils.getDictionary(mArgs[1], false /* report */);
        if (null == dict1) throw new RuntimeException("Can't read dictionary " + mArgs[1]);
        if (!porcelain) {
            System.out.println("Header :");
            diffHeaders(dict0, dict1);
            if (languageDiffers(dict0, dict1)) {
                // diffHeaders returns whether the language is different. If it is, we should bail
                // to avoid flooding the output with thousands of useless word-level diffs.
                return;
            }
            System.out.println("Body :");
        }
        // TODO: implement the word-level diff
    }

    private static boolean languageDiffers(final FusionDictionary dict0,
            final FusionDictionary dict1) {
        // If either of the dictionaries have no locale, assume it's okay
        // We only check for the language here. The rationale is that one may meaningfully diff
        // a en_US with a en_GB dictionary, but someone who diffs a de dict with a pt_BR dict
        // is almost certainly only interested in header-level diff, and the word diff would be very
        // large, meaningless, and annoying.
        if (null == dict0.mOptions.mAttributes.get("locale")) return true;
        if (null == dict1.mOptions.mAttributes.get("locale")) return true;
        final String dict0Lang = dict0.mOptions.mAttributes.get("locale").split("_", 3)[0];
        final String dict1Lang = dict1.mOptions.mAttributes.get("locale").split("_", 3)[0];
        return !dict0Lang.equals(dict1Lang);
    }

    private static void diffHeaders(final FusionDictionary dict0, final FusionDictionary dict1) {
        if (dict0.mOptions.mFrenchLigatureProcessing != dict1.mOptions.mFrenchLigatureProcessing) {
            System.out.println("  French ligature processing : "
                    + dict0.mOptions.mFrenchLigatureProcessing + " <=> "
                    + dict1.mOptions.mFrenchLigatureProcessing);
        }
        else if (dict0.mOptions.mGermanUmlautProcessing != dict1.mOptions.mGermanUmlautProcessing) {
            System.out.println("  German umlaut processing : "
                    + dict0.mOptions.mGermanUmlautProcessing + " <=> "
                    + dict1.mOptions.mGermanUmlautProcessing);
        }
        for (final String optionKey : dict0.mOptions.mAttributes.keySet()) {
            if (!dict0.mOptions.mAttributes.get(optionKey).equals(
                    dict1.mOptions.mAttributes.get(optionKey))) {
                System.out.println("  " + optionKey + " : "
                        + dict0.mOptions.mAttributes.get(optionKey) + " <=> "
                        + dict1.mOptions.mAttributes.get(optionKey));
            }
            dict1.mOptions.mAttributes.remove(optionKey);
        }
        for (final String optionKey : dict1.mOptions.mAttributes.keySet()) {
            System.out.println("  " + optionKey + " : null <=> "
                    + dict1.mOptions.mAttributes.get(optionKey));
        }
    }
}
