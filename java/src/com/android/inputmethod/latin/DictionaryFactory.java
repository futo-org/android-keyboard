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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Factory for dictionary instances.
 */
public class DictionaryFactory {
    private static final String TAG = DictionaryFactory.class.getSimpleName();
    // This class must be located in the same package as LatinIME.java.
    private static final String RESOURCE_PACKAGE_NAME =
            DictionaryFactory.class.getPackage().getName();

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
            return new DictionaryCollection(createBinaryDictionary(context, locale));
        }

        final LinkedList<Dictionary> dictList = new LinkedList<Dictionary>();
        final ArrayList<AssetFileAddress> assetFileList =
                BinaryDictionaryGetter.getDictionaryFiles(locale, context);
        if (null != assetFileList) {
            for (final AssetFileAddress f : assetFileList) {
                final BinaryDictionary binaryDictionary =
                        new BinaryDictionary(context, f.mFilename, f.mOffset, f.mLength,
                                useFullEditDistance, locale);
                if (binaryDictionary.isValidDictionary()) {
                    dictList.add(binaryDictionary);
                }
            }
        }

        // If the list is empty, that means we should not use any dictionary (for example, the user
        // explicitly disabled the main dictionary), so the following is okay. dictList is never
        // null, but if for some reason it is, DictionaryCollection handles it gracefully.
        return new DictionaryCollection(dictList);
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
     * Initializes a dictionary from a raw resource file
     * @param context application context for reading resources
     * @param locale the locale to use for the resource
     * @return an initialized instance of BinaryDictionary
     */
    protected static BinaryDictionary createBinaryDictionary(final Context context,
            final Locale locale) {
        AssetFileDescriptor afd = null;
        try {
            final int resId =
                    getMainDictionaryResourceIdIfAvailableForLocale(context.getResources(), locale);
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
            return new BinaryDictionary(context, sourceDir, afd.getStartOffset(), afd.getLength(),
                    false /* useFullEditDistance */, locale);
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
     * @param context the test context to create this data from.
     * @param dictionary the file to read
     * @param startOffset the offset in the file where the data starts
     * @param length the length of the data
     * @param useFullEditDistance whether to use the full edit distance in suggestions
     * @return the created dictionary, or null.
     */
    public static Dictionary createDictionaryForTest(Context context, File dictionary,
            long startOffset, long length, final boolean useFullEditDistance, Locale locale) {
        if (dictionary.isFile()) {
            return new BinaryDictionary(context, dictionary.getAbsolutePath(), startOffset, length,
                    useFullEditDistance, locale);
        } else {
            Log.e(TAG, "Could not find the file. path=" + dictionary.getAbsolutePath());
            return null;
        }
    }

    /**
     * Find out whether a dictionary is available for this locale.
     * @param context the context on which to check resources.
     * @param locale the locale to check for.
     * @return whether a (non-placeholder) dictionary is available or not.
     */
    public static boolean isDictionaryAvailable(Context context, Locale locale) {
        final Resources res = context.getResources();
        return 0 != getMainDictionaryResourceIdIfAvailableForLocale(res, locale);
    }

    private static final String DEFAULT_MAIN_DICT = "main";
    private static final String MAIN_DICT_PREFIX = "main_";

    /**
     * Helper method to return a dictionary res id for a locale, or 0 if none.
     * @param locale dictionary locale
     * @return main dictionary resource id
     */
    private static int getMainDictionaryResourceIdIfAvailableForLocale(final Resources res,
            final Locale locale) {
        int resId;
        // Try to find main_language_country dictionary.
        if (!locale.getCountry().isEmpty()) {
            final String dictLanguageCountry = MAIN_DICT_PREFIX + locale.toString().toLowerCase();
            if ((resId = res.getIdentifier(
                    dictLanguageCountry, "raw", RESOURCE_PACKAGE_NAME)) != 0) {
                return resId;
            }
        }

        // Try to find main_language dictionary.
        final String dictLanguage = MAIN_DICT_PREFIX + locale.getLanguage();
        if ((resId = res.getIdentifier(dictLanguage, "raw", RESOURCE_PACKAGE_NAME)) != 0) {
            return resId;
        }

        // Not found, return 0
        return 0;
    }

    /**
     * Returns a main dictionary resource id
     * @param locale dictionary locale
     * @return main dictionary resource id
     */
    public static int getMainDictionaryResourceId(final Resources res, final Locale locale) {
        int resourceId = getMainDictionaryResourceIdIfAvailableForLocale(res, locale);
        if (0 != resourceId) return resourceId;
        return res.getIdentifier(DEFAULT_MAIN_DICT, "raw", RESOURCE_PACKAGE_NAME);
    }
}
