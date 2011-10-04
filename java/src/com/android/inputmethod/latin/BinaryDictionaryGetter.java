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
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Helper class to get the address of a mmap'able dictionary file.
 */
class BinaryDictionaryGetter {

    /**
     * Used for Log actions from this class
     */
    private static final String TAG = BinaryDictionaryGetter.class.getSimpleName();

    /**
     * Used to return empty lists
     */
    private static final File[] EMPTY_FILE_ARRAY = new File[0];

    /**
     * Name of the common preferences name to know which word list are on and which are off.
     */
    private static final String COMMON_PREFERENCES_NAME = "LatinImeDictPrefs";

    // Prevents this from being instantiated
    private BinaryDictionaryGetter() {}

    /**
     * Returns whether we may want to use this character as part of a file name.
     *
     * This basically only accepts ascii letters and numbers, and rejects everything else.
     */
    private static boolean isFileNameCharacter(int codePoint) {
        if (codePoint >= 0x30 && codePoint <= 0x39) return true; // Digit
        if (codePoint >= 0x41 && codePoint <= 0x5A) return true; // Uppercase
        if (codePoint >= 0x61 && codePoint <= 0x7A) return true; // Lowercase
        return codePoint == '_'; // Underscore
    }

    /**
     * Escapes a string for any characters that may be suspicious for a file or directory name.
     *
     * Concretely this does a sort of URL-encoding except it will encode everything that's not
     * alphanumeric or underscore. (true URL-encoding leaves alone characters like '*', which
     * we cannot allow here)
     */
    // TODO: create a unit test for this method
    private static String replaceFileNameDangerousCharacters(final String name) {
        // This assumes '%' is fully available as a non-separator, normal
        // character in a file name. This is probably true for all file systems.
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); ++i) {
            final int codePoint = name.codePointAt(i);
            if (isFileNameCharacter(codePoint)) {
                sb.appendCodePoint(codePoint);
            } else {
                // 6 digits - unicode is limited to 21 bits
                sb.append(String.format((Locale)null, "%%%1$06x", codePoint));
            }
        }
        return sb.toString();
    }

    /**
     * Reverse escaping done by replaceFileNameDangerousCharacters.
     */
    private static String getWordListIdFromFileName(final String fname) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fname.length(); ++i) {
            final int codePoint = fname.codePointAt(i);
            if ('%' != codePoint) {
                sb.appendCodePoint(codePoint);
            } else {
                final int encodedCodePoint = Integer.parseInt(fname.substring(i + 1, i + 7), 16);
                i += 6;
                sb.appendCodePoint(encodedCodePoint);
            }
        }
        return sb.toString();
    }

    /**
     * Helper method to get the top level cache directory.
     */
    private static String getWordListCacheDirectory(final Context context) {
        return context.getFilesDir() + File.separator + "dicts";
    }

    /**
     * Find out the cache directory associated with a specific locale.
     */
    private static String getCacheDirectoryForLocale(final String locale, final Context context) {
        final String relativeDirectoryName = replaceFileNameDangerousCharacters(locale);
        final String absoluteDirectoryName = getWordListCacheDirectory(context) + File.separator
                + relativeDirectoryName;
        final File directory = new File(absoluteDirectoryName);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e(TAG, "Could not create the directory for locale" + locale);
            }
        }
        return absoluteDirectoryName;
    }

    /**
     * Generates a file name for the id and locale passed as an argument.
     *
     * In the current implementation the file name returned will always be unique for
     * any id/locale pair, but please do not expect that the id can be the same for
     * different dictionaries with different locales. An id should be unique for any
     * dictionary.
     * The file name is pretty much an URL-encoded version of the id inside a directory
     * named like the locale, except it will also escape characters that look dangerous
     * to some file systems.
     * @param id the id of the dictionary for which to get a file name
     * @param locale the locale for which to get the file name as a string
     * @param context the context to use for getting the directory
     * @return the name of the file to be created
     */
    public static String getCacheFileName(String id, String locale, Context context) {
        final String fileName = replaceFileNameDangerousCharacters(id);
        return getCacheDirectoryForLocale(locale, context) + File.separator + fileName;
    }

    /**
     * Returns a file address from a resource, or null if it cannot be opened.
     */
    private static AssetFileAddress loadFallbackResource(final Context context,
            final int fallbackResId, final Locale locale) {
        final Resources res = context.getResources();
        final Locale savedLocale = LocaleUtils.setSystemLocale(res, locale);
        final AssetFileDescriptor afd = res.openRawResourceFd(fallbackResId);
        LocaleUtils.setSystemLocale(res, savedLocale);

        if (afd == null) {
            Log.e(TAG, "Found the resource but cannot read it. Is it compressed? resId="
                    + fallbackResId);
            return null;
        }
        return AssetFileAddress.makeFromFileNameAndOffset(
                context.getApplicationInfo().sourceDir, afd.getStartOffset(), afd.getLength());
    }

    static private class DictPackSettings {
        final SharedPreferences mDictPreferences;
        public DictPackSettings(final Context context) {
            Context dictPackContext = null;
            try {
                final String dictPackName =
                        context.getString(R.string.dictionary_pack_package_name);
                dictPackContext = context.createPackageContext(dictPackName, 0);
            } catch (NameNotFoundException e) {
                // The dictionary pack is not installed...
                // TODO: fallback on the built-in dict, see the TODO above
                Log.e(TAG, "Could not find a dictionary pack");
            }
            mDictPreferences = null == dictPackContext ? null
                    : dictPackContext.getSharedPreferences(COMMON_PREFERENCES_NAME,
                            Context.MODE_WORLD_READABLE | Context.MODE_MULTI_PROCESS);
        }
        public boolean isWordListActive(final String dictId) {
            if (null == mDictPreferences) {
                // If we don't have preferences it basically means we can't find the dictionary
                // pack - either it's not installed, or it's disabled, or there is some strange
                // bug. Either way, a word list with no settings should be on by default: default
                // dictionaries in LatinIME are on if there is no settings at all, and if for some
                // reason some dictionaries have been installed BUT the dictionary pack can't be
                // found anymore it's safer to actually supply installed dictionaries.
                return true;
            } else {
                // The default is true here for the same reasons as above. We got the dictionary
                // pack but if we don't have any settings for it it means the user has never been
                // to the settings yet. So by default, the main dictionaries should be on.
                return mDictPreferences.getBoolean(dictId, true);
            }
        }
    }

    /**
     * Helper method to the list of cache directories, one for each distinct locale.
     */
    private static File[] getCachedDirectoryList(final Context context) {
        return new File(getWordListCacheDirectory(context)).listFiles();
    }

    /**
     * Returns the list of cached files for a specific locale.
     *
     * @param locale the locale to find the dictionary files for, as a string.
     * @param context the context on which to open the files upon.
     * @return an array of binary dictionary files, which may be empty but may not be null.
     */
    private static File[] getCachedWordLists(final String locale,
            final Context context) {
        final File[] directoryList = getCachedDirectoryList(context);
        if (null == directoryList) return EMPTY_FILE_ARRAY;
        final ArrayList<File> cacheFiles = new ArrayList<File>();
        for (File directory : directoryList) {
            if (!directory.isDirectory()) continue;
            final String dirLocale = getWordListIdFromFileName(directory.getName());
            if (LocaleUtils.isMatch(LocaleUtils.getMatchLevel(dirLocale, locale))) {
                final File[] wordLists = directory.listFiles();
                if (null != wordLists) {
                    for (File wordList : wordLists) {
                        cacheFiles.add(wordList);
                    }
                }
            }
        }
        if (cacheFiles.isEmpty()) return EMPTY_FILE_ARRAY;
        return cacheFiles.toArray(EMPTY_FILE_ARRAY);
    }

    /**
     * Returns the id associated with the main word list for a specified locale.
     *
     * Word lists stored in Android Keyboard's resources are referred to as the "main"
     * word lists. Since they can be updated like any other list, we need to assign a
     * unique ID to them. This ID is just the name of the language (locale-wise) they
     * are for, and this method returns this ID.
     */
    private static String getMainDictId(final Locale locale) {
        // This works because we don't include by default different dictionaries for
        // different countries. This actually needs to return the id that we would
        // like to use for word lists included in resources, and the following is okay.
        return locale.getLanguage().toString();
    }

    /**
     * Returns a list of file addresses for a given locale, trying relevant methods in order.
     *
     * Tries to get binary dictionaries from various sources, in order:
     * - Uses a content provider to get a public dictionary set, as per the protocol described
     *   in BinaryDictionaryFileDumper.
     * If that fails:
     * - Gets a file name from the fallback resource passed as an argument.
     * If that fails:
     * - Returns null.
     * @return The address of a valid file, or null.
     */
    public static List<AssetFileAddress> getDictionaryFiles(final Locale locale,
            final Context context, final int fallbackResId) {

        // cacheWordListsFromContentProvider returns the list of files it copied to local
        // storage, but we don't really care about what was copied NOW: what we want is the
        // list of everything we ever cached, so we ignore the return value.
        BinaryDictionaryFileDumper.cacheWordListsFromContentProvider(locale, context);
        final File[] cachedWordLists = getCachedWordLists(locale.toString(), context);

        final String mainDictId = getMainDictId(locale);

        final DictPackSettings dictPackSettings = new DictPackSettings(context);

        boolean foundMainDict = false;
        final ArrayList<AssetFileAddress> fileList = new ArrayList<AssetFileAddress>();
        // cachedWordLists may not be null, see doc for getCachedDictionaryList
        for (final File f : cachedWordLists) {
            final String wordListId = getWordListIdFromFileName(f.getName());
            if (wordListId.equals(mainDictId)) {
                foundMainDict = true;
            }
            if (!dictPackSettings.isWordListActive(wordListId)) continue;
            if (f.canRead()) {
                fileList.add(AssetFileAddress.makeFromFileName(f.getPath()));
            } else {
                Log.e(TAG, "Found a cached dictionary file but cannot read it");
            }
        }

        if (!foundMainDict && dictPackSettings.isWordListActive(mainDictId)) {
            final AssetFileAddress fallbackAsset = loadFallbackResource(context, fallbackResId,
                    locale);
            if (null != fallbackAsset) {
                fileList.add(fallbackAsset);
            }
        }

        return fileList;
    }
}
