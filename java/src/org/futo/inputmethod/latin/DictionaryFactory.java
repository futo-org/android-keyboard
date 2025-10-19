/*
 * Copyright (C) 2011 The Android Open Source Project
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

package org.futo.inputmethod.latin;

import android.content.Context;
import android.util.Log;

import org.futo.inputmethod.latin.uix.ResourceHelper;
import org.futo.inputmethod.latin.utils.Dictionaries;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Factory for dictionary instances.
 */
public final class DictionaryFactory {
    private static final String TAG = DictionaryFactory.class.getSimpleName();

    /**
     * Initializes a main dictionary collection from a dictionary pack, with explicit flags.
     *
     * This searches for a content provider providing a dictionary pack for the specified
     * locale. If none is found, it falls back to the built-in dictionary - if any.
     * @param context application context for reading resources
     * @param locale the locale for which to create the dictionary
     * @return an initialized instance of DictionaryCollection
     */
    public static DictionaryCollection createMainDictionaryFromManager(final Context context,
            final Locale locale) {
        if (null == locale) {
            Log.e(TAG, "No locale defined for dictionary");
            return new DictionaryCollection(Dictionary.TYPE_MAIN, locale);
        }

        final LinkedList<Dictionary> dictList = new LinkedList<>();

        ReadOnlyBinaryDictionary customDict =
                ResourceHelper.INSTANCE.tryOpeningCustomMainDictionaryForLocale(context, locale);
        if(customDict != null) {
            dictList.add(customDict);
        } else {
            final ArrayList<AssetFileAddress> assetFileList =
                    BinaryDictionaryGetter.getDictionaryFiles(locale, context, true, true);
            if (null != assetFileList) {
                for (final AssetFileAddress f : assetFileList) {
                    final ReadOnlyBinaryDictionary readOnlyBinaryDictionary =
                            new ReadOnlyBinaryDictionary(f.mFilename, f.mOffset, f.mLength,
                                    false /* useFullEditDistance */, locale, Dictionary.TYPE_MAIN);
                    if (readOnlyBinaryDictionary.isValidDictionary()) {
                        dictList.add(readOnlyBinaryDictionary);
                    } else {
                        readOnlyBinaryDictionary.close();
                        // Prevent this dictionary to do any further harm.
                        killDictionary(context, f);
                    }
                }
            }
        }

        // If the list is empty, that means we should not use any dictionary (for example, the user
        // explicitly disabled the main dictionary), so the following is okay. dictList is never
        // null, but if for some reason it is, DictionaryCollection handles it gracefully.
        return new DictionaryCollection(Dictionary.TYPE_MAIN, locale, dictList);
    }

    /**
     * Kills a dictionary so that it is never used again, if possible.
     * @param context The context to contact the dictionary provider, if possible.
     * @param f A file address to the dictionary to kill.
     */
    public static void killDictionary(final Context context, final AssetFileAddress f) {
        if (f.pointsToPhysicalFile()) {
            f.deleteUnderlyingFile();
        }
    }

    /**
     * Initializes a read-only binary dictionary from a raw resource file
     * @param context application context for reading resources
     * @param locale the locale to use for the resource
     * @return an initialized instance of ReadOnlyBinaryDictionary
     */
    private static ReadOnlyBinaryDictionary createReadOnlyBinaryDictionary(final Context context,
            final Locale locale) {
        AssetFileAddress afa = Dictionaries.INSTANCE.getDictionaryIfExists(context, locale, Dictionaries.DictionaryKind.BinaryDictionary);
        if (afa == null) return null;

        final String sourceDir = afa.mFilename;
        final File packagePath = new File(sourceDir);

        if (!packagePath.isFile()) {
            Log.e(TAG, "sourceDir is not a file: " + sourceDir);
            return null;
        }
        return new ReadOnlyBinaryDictionary(sourceDir, afa.mOffset, afa.mLength,
                false /* useFullEditDistance */, locale, Dictionary.TYPE_MAIN);
    }
}
