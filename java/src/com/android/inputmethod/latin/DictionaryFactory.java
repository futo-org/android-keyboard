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
    private static String TAG = DictionaryFactory.class.getSimpleName();

    /**
     * Initializes a dictionary from a dictionary pack, with explicit flags.
     *
     * This searches for a content provider providing a dictionary pack for the specified
     * locale. If none is found, it falls back to the built-in dictionary - if any.
     * @param context application context for reading resources
     * @param locale the locale for which to create the dictionary
     * @param useFullEditDistance whether to use the full edit distance in suggestions
     * @return an initialized instance of DictionaryCollection
     */
    public static DictionaryCollection createDictionaryFromManager(final Context context,
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
     * Initializes a dictionary from a dictionary pack, with default flags.
     *
     * This searches for a content provider providing a dictionary pack for the specified
     * locale. If none is found, it falls back to the built-in dictionary, if any.
     * @param context application context for reading resources
     * @param locale the locale for which to create the dictionary
     * @return an initialized instance of DictionaryCollection
     */
    public static DictionaryCollection createDictionaryFromManager(final Context context,
            final Locale locale) {
        return createDictionaryFromManager(context, locale, false /* useFullEditDistance */);
    }

    /**
     * Initializes a dictionary from a raw resource file
     * @param context application context for reading resources
     * @param resId the resource containing the raw binary dictionary
     * @param locale the locale to use for the resource
     * @return an initialized instance of BinaryDictionary
     */
    protected static BinaryDictionary createBinaryDictionary(final Context context,
            final Locale locale) {
        AssetFileDescriptor afd = null;
        try {
            final int resId = getMainDictionaryResourceId(context.getResources(), locale);
            afd = context.getResources().openRawResourceFd(resId);
            if (afd == null) {
                Log.e(TAG, "Found the resource but it is compressed. resId=" + resId);
                return null;
            }
            if (!isFullDictionary(afd)) return null;
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
        final int resourceId = getMainDictionaryResourceId(res, locale);
        final AssetFileDescriptor afd = res.openRawResourceFd(resourceId);
        final boolean hasDictionary = isFullDictionary(afd);
        try {
            if (null != afd) afd.close();
        } catch (java.io.IOException e) {
            /* Um, what can we do here exactly? */
        }
        return hasDictionary;
    }

    // TODO: Do not use the size of the dictionary as an unique dictionary ID.
    public static Long getDictionaryId(final Context context, final Locale locale) {
        final Resources res = context.getResources();
        final int resourceId = getMainDictionaryResourceId(res, locale);
        final AssetFileDescriptor afd = res.openRawResourceFd(resourceId);
        final Long size = (afd != null && afd.getLength() > PLACEHOLDER_LENGTH)
                ? afd.getLength()
                : null;
        try {
            if (null != afd) afd.close();
        } catch (java.io.IOException e) {
        }
        return size;
    }

    // TODO: Find the Right Way to find out whether the resource is a placeholder or not.
    // Suggestion : strip the locale, open the placeholder file and store its offset.
    // Upon opening the file, if it's the same offset, then it's the placeholder.
    private static final long PLACEHOLDER_LENGTH = 34;
    /**
     * Finds out whether the data pointed out by an AssetFileDescriptor is a full
     * dictionary (as opposed to null, or to a place holder).
     * @param afd the file descriptor to test, or null
     * @return true if the dictionary is a real full dictionary, false if it's null or a placeholder
     */
    protected static boolean isFullDictionary(final AssetFileDescriptor afd) {
        return (afd != null && afd.getLength() > PLACEHOLDER_LENGTH);
    }

    private static final String DEFAULT_MAIN_DICT = "main";
    private static final String MAIN_DICT_PREFIX = "main_";

    /**
     * Returns a main dictionary resource id
     * @param locale dictionary locale
     * @return main dictionary resource id
     */
    public static int getMainDictionaryResourceId(Resources res, Locale locale) {
        final String packageName = LatinIME.class.getPackage().getName();
        int resId;

        // Try to find main_language_country dictionary.
        if (!locale.getCountry().isEmpty()) {
            final String dictLanguageCountry = MAIN_DICT_PREFIX + locale.toString().toLowerCase();
            if ((resId = res.getIdentifier(dictLanguageCountry, "raw", packageName)) != 0) {
                return resId;
            }
        }

        // Try to find main_language dictionary.
        final String dictLanguage = MAIN_DICT_PREFIX + locale.getLanguage();
        if ((resId = res.getIdentifier(dictLanguage, "raw", packageName)) != 0) {
            return resId;
        }

        return res.getIdentifier(DEFAULT_MAIN_DICT, "raw", packageName);
    }
}
