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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.DictionaryInfoUtils;

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
     * @param useFullEditDistance whether to use the full edit distance in suggestions
     * @return an initialized instance of DictionaryCollection
     */
    public static DictionaryCollection createMainDictionaryFromManager(final Context context,
            final Locale locale, final boolean useFullEditDistance) {
        if (null == locale) {
            Log.e(TAG, "No locale defined for dictionary");
            return new DictionaryCollection(Dictionary.TYPE_MAIN,
                    createReadOnlyBinaryDictionary(context, locale));
        }

        final LinkedList<Dictionary> dictList = CollectionUtils.newLinkedList();
        final ArrayList<AssetFileAddress> assetFileList =
                BinaryDictionaryGetter.getDictionaryFiles(locale, context);
        if (null != assetFileList) {
            for (final AssetFileAddress f : assetFileList) {
                final ReadOnlyBinaryDictionary readOnlyBinaryDictionary =
                        new ReadOnlyBinaryDictionary(f.mFilename, f.mOffset, f.mLength,
                                useFullEditDistance, locale, Dictionary.TYPE_MAIN);
                if (readOnlyBinaryDictionary.isValidDictionary()) {
                    dictList.add(readOnlyBinaryDictionary);
                }
            }
        }

        // If the list is empty, that means we should not use any dictionary (for example, the user
        // explicitly disabled the main dictionary), so the following is okay. dictList is never
        // null, but if for some reason it is, DictionaryCollection handles it gracefully.
        return new DictionaryCollection(Dictionary.TYPE_MAIN, dictList);
    }

    /**
     * Initializes a main dictionary collection from a dictionary pack, with default flags.
     *
     * This searches for a content provider providing a dictionary pack for the specified
     * locale. If none is found, it falls back to the built-in dictionary, if any.
     * @param context application context for reading resources
     * @param locale the locale for which to create the dictionary
     * @return an initialized instance of DictionaryCollection
     */
    public static DictionaryCollection createMainDictionaryFromManager(final Context context,
            final Locale locale) {
        return createMainDictionaryFromManager(context, locale, false /* useFullEditDistance */);
    }

    /**
     * Initializes a read-only binary dictionary from a raw resource file
     * @param context application context for reading resources
     * @param locale the locale to use for the resource
     * @return an initialized instance of ReadOnlyBinaryDictionary
     */
    protected static ReadOnlyBinaryDictionary createReadOnlyBinaryDictionary(final Context context,
            final Locale locale) {
        AssetFileDescriptor afd = null;
        try {
            final int resId = DictionaryInfoUtils.getMainDictionaryResourceIdIfAvailableForLocale(
                    context.getResources(), locale);
            if (0 == resId) return null;
            afd = context.getResources().openRawResourceFd(resId);
            if (afd == null) {
                Log.e(TAG, "Found the resource but it is compressed. resId=" + resId);
                return null;
            }
            final String sourceDir = context.getApplicationInfo().sourceDir;
            final File packagePath = new File(sourceDir);
            // TODO: Come up with a way to handle a directory.
            if (!packagePath.isFile()) {
                Log.e(TAG, "sourceDir is not a file: " + sourceDir);
                return null;
            }
            return new ReadOnlyBinaryDictionary(sourceDir, afd.getStartOffset(), afd.getLength(),
                    false /* useFullEditDistance */, locale, Dictionary.TYPE_MAIN);
        } catch (android.content.res.Resources.NotFoundException e) {
            Log.e(TAG, "Could not find the resource");
            return null;
        } finally {
            if (null != afd) {
                try {
                    afd.close();
                } catch (java.io.IOException e) {
                    /* IOException on close ? What am I supposed to do ? */
                }
            }
        }
    }

    /**
     * Create a dictionary from passed data. This is intended for unit tests only.
     * @param dictionaryList the list of files to read, with their offsets and lengths
     * @param useFullEditDistance whether to use the full edit distance in suggestions
     * @return the created dictionary, or null.
     */
    @UsedForTesting
    public static Dictionary createDictionaryForTest(final AssetFileAddress[] dictionaryList,
            final boolean useFullEditDistance, Locale locale) {
        final DictionaryCollection dictionaryCollection =
                new DictionaryCollection(Dictionary.TYPE_MAIN);
        for (final AssetFileAddress address : dictionaryList) {
            final ReadOnlyBinaryDictionary readOnlyBinaryDictionary = new ReadOnlyBinaryDictionary(
                    address.mFilename, address.mOffset, address.mLength, useFullEditDistance,
                    locale, Dictionary.TYPE_MAIN);
            dictionaryCollection.addDictionary(readOnlyBinaryDictionary);
        }
        return dictionaryCollection;
    }

    /**
     * Find out whether a dictionary is available for this locale.
     * @param context the context on which to check resources.
     * @param locale the locale to check for.
     * @return whether a (non-placeholder) dictionary is available or not.
     */
    public static boolean isDictionaryAvailable(Context context, Locale locale) {
        final Resources res = context.getResources();
        return 0 != DictionaryInfoUtils.getMainDictionaryResourceIdIfAvailableForLocale(
                res, locale);
    }
}
