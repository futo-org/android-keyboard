/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import com.android.inputmethod.latin.AssetFileAddress;
import com.android.inputmethod.latin.BinaryDictionaryGetter;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.makedict.BinaryDictIOUtils;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * This class encapsulates the logic for the Latin-IME side of dictionary information management.
 */
public class DictionaryInfoUtils {
    private static final String TAG = DictionaryInfoUtils.class.getSimpleName();
    private static final String RESOURCE_PACKAGE_NAME = R.class.getPackage().getName();
    private static final String DEFAULT_MAIN_DICT = "main";
    private static final String MAIN_DICT_PREFIX = "main_";
    // 6 digits - unicode is limited to 21 bits
    private static final int MAX_HEX_DIGITS_FOR_CODEPOINT = 6;

    public static class DictionaryInfo {
        private static final String LOCALE_COLUMN = "locale";
        private static final String WORDLISTID_COLUMN = "id";
        private static final String LOCAL_FILENAME_COLUMN = "filename";
        private static final String DESCRIPTION_COLUMN = "description";
        private static final String DATE_COLUMN = "date";
        private static final String FILESIZE_COLUMN = "filesize";
        private static final String VERSION_COLUMN = "version";
        public final String mId;
        public final Locale mLocale;
        public final String mDescription;
        public final AssetFileAddress mFileAddress;
        public final int mVersion;
        public DictionaryInfo(final String id, final Locale locale, final String description,
                final AssetFileAddress fileAddress, final int version) {
            mId = id;
            mLocale = locale;
            mDescription = description;
            mFileAddress = fileAddress;
            mVersion = version;
        }
        public ContentValues toContentValues() {
            final ContentValues values = new ContentValues();
            values.put(WORDLISTID_COLUMN, mId);
            values.put(LOCALE_COLUMN, mLocale.toString());
            values.put(DESCRIPTION_COLUMN, mDescription);
            values.put(LOCAL_FILENAME_COLUMN, mFileAddress.mFilename);
            values.put(DATE_COLUMN, TimeUnit.MILLISECONDS.toSeconds(
                    new File(mFileAddress.mFilename).lastModified()));
            values.put(FILESIZE_COLUMN, mFileAddress.mLength);
            values.put(VERSION_COLUMN, mVersion);
            return values;
        }
    }

    private DictionaryInfoUtils() {
        // Private constructor to forbid instantation of this helper class.
    }

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
    public static String replaceFileNameDangerousCharacters(final String name) {
        // This assumes '%' is fully available as a non-separator, normal
        // character in a file name. This is probably true for all file systems.
        final StringBuilder sb = new StringBuilder();
        final int nameLength = name.length();
        for (int i = 0; i < nameLength; i = name.offsetByCodePoints(i, 1)) {
            final int codePoint = name.codePointAt(i);
            if (DictionaryInfoUtils.isFileNameCharacter(codePoint)) {
                sb.appendCodePoint(codePoint);
            } else {
                sb.append(String.format((Locale)null, "%%%1$0" + MAX_HEX_DIGITS_FOR_CODEPOINT + "x",
                        codePoint));
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
     * Helper method to get the top level temp directory.
     */
    public static String getWordListTempDirectory(final Context context) {
        return context.getFilesDir() + File.separator + "tmp";
    }

    /**
     * Reverse escaping done by replaceFileNameDangerousCharacters.
     */
    public static String getWordListIdFromFileName(final String fname) {
        final StringBuilder sb = new StringBuilder();
        final int fnameLength = fname.length();
        for (int i = 0; i < fnameLength; i = fname.offsetByCodePoints(i, 1)) {
            final int codePoint = fname.codePointAt(i);
            if ('%' != codePoint) {
                sb.appendCodePoint(codePoint);
            } else {
                // + 1 to pass the % sign
                final int encodedCodePoint = Integer.parseInt(
                        fname.substring(i + 1, i + 1 + MAX_HEX_DIGITS_FOR_CODEPOINT), 16);
                i += MAX_HEX_DIGITS_FOR_CODEPOINT;
                sb.appendCodePoint(encodedCodePoint);
            }
        }
        return sb.toString();
    }

    /**
     * Helper method to the list of cache directories, one for each distinct locale.
     */
    public static File[] getCachedDirectoryList(final Context context) {
        return new File(DictionaryInfoUtils.getWordListCacheDirectory(context)).listFiles();
    }

    /**
     * Returns the category for a given file name.
     *
     * This parses the file name, extracts the category, and returns it. See
     * {@link #getMainDictId(Locale)} and {@link #isMainWordListId(String)}.
     * @return The category as a string or null if it can't be found in the file name.
     */
    public static String getCategoryFromFileName(final String fileName) {
        final String id = getWordListIdFromFileName(fileName);
        final String[] idArray = id.split(BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR);
        // An id is supposed to be in format category:locale, so splitting on the separator
        // should yield a 2-elements array
        if (2 != idArray.length) return null;
        return idArray[0];
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

    public static boolean isMainWordListId(final String id) {
        final String[] idArray = id.split(BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR);
        // An id is supposed to be in format category:locale, so splitting on the separator
        // should yield a 2-elements array
        if (2 != idArray.length) return false;
        return BinaryDictionaryGetter.MAIN_DICTIONARY_CATEGORY.equals(idArray[0]);
    }

    /**
     * Helper method to return a dictionary res id for a locale, or 0 if none.
     * @param locale dictionary locale
     * @return main dictionary resource id
     */
    public static int getMainDictionaryResourceIdIfAvailableForLocale(final Resources res,
            final Locale locale) {
        int resId;
        // Try to find main_language_country dictionary.
        if (!locale.getCountry().isEmpty()) {
            final String dictLanguageCountry =
                    MAIN_DICT_PREFIX + locale.toString().toLowerCase(Locale.ROOT);
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

    /**
     * Returns the id associated with the main word list for a specified locale.
     *
     * Word lists stored in Android Keyboard's resources are referred to as the "main"
     * word lists. Since they can be updated like any other list, we need to assign a
     * unique ID to them. This ID is just the name of the language (locale-wise) they
     * are for, and this method returns this ID.
     */
    public static String getMainDictId(final Locale locale) {
        // This works because we don't include by default different dictionaries for
        // different countries. This actually needs to return the id that we would
        // like to use for word lists included in resources, and the following is okay.
        return BinaryDictionaryGetter.MAIN_DICTIONARY_CATEGORY +
                BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR + locale.getLanguage().toString();
    }

    public static FileHeader getDictionaryFileHeaderOrNull(final File file) {
        return BinaryDictIOUtils.getDictionaryFileHeaderOrNull(file, 0, file.length());
    }

    private static DictionaryInfo createDictionaryInfoFromFileAddress(
            final AssetFileAddress fileAddress) {
        final FileHeader header = BinaryDictIOUtils.getDictionaryFileHeaderOrNull(
                new File(fileAddress.mFilename), fileAddress.mOffset, fileAddress.mLength);
        final String id = header.getId();
        final Locale locale = LocaleUtils.constructLocaleFromString(header.getLocaleString());
        final String description = header.getDescription();
        final String version = header.getVersion();
        return new DictionaryInfo(id, locale, description, fileAddress, Integer.parseInt(version));
    }

    private static void addOrUpdateDictInfo(final ArrayList<DictionaryInfo> dictList,
            final DictionaryInfo newElement) {
        final Iterator<DictionaryInfo> iter = dictList.iterator();
        while (iter.hasNext()) {
            final DictionaryInfo thisDictInfo = iter.next();
            if (thisDictInfo.mLocale.equals(newElement.mLocale)) {
                if (newElement.mVersion <= thisDictInfo.mVersion) {
                    return;
                }
                iter.remove();
            }
        }
        dictList.add(newElement);
    }

    public static ArrayList<DictionaryInfo> getCurrentDictionaryFileNameAndVersionInfo(
            final Context context) {
        final ArrayList<DictionaryInfo> dictList = CollectionUtils.newArrayList();

        // Retrieve downloaded dictionaries
        final File[] directoryList = getCachedDirectoryList(context);
        if (null != directoryList) {
            for (final File directory : directoryList) {
                final String localeString = getWordListIdFromFileName(directory.getName());
                File[] dicts = BinaryDictionaryGetter.getCachedWordLists(localeString, context);
                for (final File dict : dicts) {
                    final String wordListId = getWordListIdFromFileName(dict.getName());
                    if (!DictionaryInfoUtils.isMainWordListId(wordListId)) continue;
                    final Locale locale = LocaleUtils.constructLocaleFromString(localeString);
                    final AssetFileAddress fileAddress = AssetFileAddress.makeFromFile(dict);
                    final DictionaryInfo dictionaryInfo =
                            createDictionaryInfoFromFileAddress(fileAddress);
                    // Protect against cases of a less-specific dictionary being found, like an
                    // en dictionary being used for an en_US locale. In this case, the en dictionary
                    // should be used for en_US but discounted for listing purposes.
                    if (!dictionaryInfo.mLocale.equals(locale)) continue;
                    addOrUpdateDictInfo(dictList, dictionaryInfo);
                }
            }
        }

        // Retrieve files from assets
        final Resources resources = context.getResources();
        final AssetManager assets = resources.getAssets();
        for (final String localeString : assets.getLocales()) {
            final Locale locale = LocaleUtils.constructLocaleFromString(localeString);
            final int resourceId =
                    DictionaryInfoUtils.getMainDictionaryResourceIdIfAvailableForLocale(
                            context.getResources(), locale);
            if (0 == resourceId) continue;
            final AssetFileAddress fileAddress =
                    BinaryDictionaryGetter.loadFallbackResource(context, resourceId);
            final DictionaryInfo dictionaryInfo = createDictionaryInfoFromFileAddress(fileAddress);
            // Protect against cases of a less-specific dictionary being found, like an
            // en dictionary being used for an en_US locale. In this case, the en dictionary
            // should be used for en_US but discounted for listing purposes.
            if (!dictionaryInfo.mLocale.equals(locale)) continue;
            addOrUpdateDictInfo(dictList, dictionaryInfo);
        }

        return dictList;
    }
}
