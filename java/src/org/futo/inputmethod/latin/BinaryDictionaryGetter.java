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
import android.content.SharedPreferences;
import android.util.Log;

import org.futo.inputmethod.latin.common.LocaleUtils;
import org.futo.inputmethod.latin.define.DecoderSpecificConstants;
import org.futo.inputmethod.latin.makedict.DictionaryHeader;
import org.futo.inputmethod.latin.makedict.UnsupportedFormatException;
import org.futo.inputmethod.latin.utils.BinaryDictionaryUtils;
import org.futo.inputmethod.latin.utils.Dictionaries;
import org.futo.inputmethod.latin.utils.DictionaryInfoUtils;

import java.io.File;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Helper class to get the address of a mmap'able dictionary file.
 */
final public class BinaryDictionaryGetter {

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

    private static final boolean SHOULD_USE_DICT_VERSION =
            DecoderSpecificConstants.SHOULD_USE_DICT_VERSION;

    // Name of the category for the main dictionary
    public static final String MAIN_DICTIONARY_CATEGORY = "main";
    public static final String ID_CATEGORY_SEPARATOR = ":";

    // The key considered to read the version attribute in a dictionary file.
    private static String VERSION_KEY = "version";

    // Prevents this from being instantiated
    private BinaryDictionaryGetter() {}

    private static final class DictPackSettings {
        final SharedPreferences mDictPreferences;
        public DictPackSettings(final Context context) {
            mDictPreferences = null == context ? null
                    : context.getSharedPreferences(COMMON_PREFERENCES_NAME,
                            Context.MODE_MULTI_PROCESS);
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
            }
            // The default is true here for the same reasons as above. We got the dictionary
            // pack but if we don't have any settings for it it means the user has never been
            // to the settings yet. So by default, the main dictionaries should be on.
            return mDictPreferences.getBoolean(dictId, true);
        }
    }

    /**
     * Utility class for the {@link #getCachedWordLists} method
     */
    private static final class FileAndMatchLevel {
        final File mFile;
        final int mMatchLevel;
        public FileAndMatchLevel(final File file, final int matchLevel) {
            mFile = file;
            mMatchLevel = matchLevel;
        }
    }

    /**
     * Returns the list of cached files for a specific locale, one for each category.
     *
     * This will return exactly one file for each word list category that matches
     * the passed locale. If several files match the locale for any given category,
     * this returns the file with the closest match to the locale. For example, if
     * the passed word list is en_US, and for a category we have an en and an en_US
     * word list available, we'll return only the en_US one.
     * Thus, the list will contain as many files as there are categories.
     *
     * @param locale the locale to find the dictionary files for, as a string.
     * @param context the context on which to open the files upon.
     * @return an array of binary dictionary files, which may be empty but may not be null.
     */
    public static File[] getCachedWordLists(final String locale, final Context context) {
        final File[] directoryList = DictionaryInfoUtils.getCachedDirectoryList(context);
        if (null == directoryList) return EMPTY_FILE_ARRAY;
        final HashMap<String, FileAndMatchLevel> cacheFiles = new HashMap<>();
        for (File directory : directoryList) {
            if (!directory.isDirectory()) continue;
            final String dirLocale =
                    DictionaryInfoUtils.getWordListIdFromFileName(directory.getName());
            final int matchLevel = LocaleUtils.getMatchLevel(dirLocale, locale);
            if (LocaleUtils.isMatch(matchLevel)) {
                final File[] wordLists = directory.listFiles();
                if (null != wordLists) {
                    for (File wordList : wordLists) {
                        final String category =
                                DictionaryInfoUtils.getCategoryFromFileName(wordList.getName());
                        final FileAndMatchLevel currentBestMatch = cacheFiles.get(category);
                        if (null == currentBestMatch || currentBestMatch.mMatchLevel < matchLevel) {
                            cacheFiles.put(category, new FileAndMatchLevel(wordList, matchLevel));
                        }
                    }
                }
            }
        }
        if (cacheFiles.isEmpty()) return EMPTY_FILE_ARRAY;
        final File[] result = new File[cacheFiles.size()];
        int index = 0;
        for (final FileAndMatchLevel entry : cacheFiles.values()) {
            result[index++] = entry.mFile;
        }
        return result;
    }

    // ## HACK ## we prevent usage of a dictionary before version 18. The reason for this is, since
    // those do not include allowlist entries, the new code with an old version of the dictionary
    // would lose allowlist functionality.
    private static boolean hackCanUseDictionaryFile(final File file) {
        if (!SHOULD_USE_DICT_VERSION) {
            return true;
        }

        try {
            // Read the version of the file
            final DictionaryHeader header = BinaryDictionaryUtils.getHeader(file);
            final String version = header.mDictionaryOptions.mAttributes.get(VERSION_KEY);
            if (null == version) {
                // No version in the options : the format is unexpected
                return false;
            }
            // Version 18 is the first one to include the allowlist. 
            // Obviously this is a big ## HACK ##
            return Integer.parseInt(version) >= 18;
        } catch (java.io.FileNotFoundException e) {
            return false;
        } catch (java.io.IOException e) {
            return false;
        } catch (NumberFormatException e) {
            return false;
        } catch (BufferUnderflowException e) {
            return false;
        } catch (UnsupportedFormatException e) {
            return false;
        }
    }

    /**
     * Returns a list of file addresses for a given locale, trying relevant methods in order.
     *
     * Tries to get binary dictionaries from various sources, in order:
     * - Uses a content provider to get a public dictionary set, as per the protocol described
     *   in BinaryDictionaryFileDumper.
     * If that fails:
     * - Gets a file name from the built-in dictionary for this locale, if any.
     * If that fails:
     * - Returns null.
     * @return The list of addresses of valid dictionary files, or null.
     */
    public static ArrayList<AssetFileAddress> getDictionaryFiles(final Locale locale,
            final Context context, boolean notifyDictionaryPackForUpdates, boolean fallback) {
        final File[] cachedWordLists = getCachedWordLists(locale.toString(), context);
        final String mainDictId = DictionaryInfoUtils.getMainDictId(locale);
        final DictPackSettings dictPackSettings = new DictPackSettings(context);

        boolean foundMainDict = false;
        final ArrayList<AssetFileAddress> fileList = new ArrayList<>();
        // cachedWordLists may not be null, see doc for getCachedDictionaryList
        for (final File f : cachedWordLists) {
            final String wordListId = DictionaryInfoUtils.getWordListIdFromFileName(f.getName());
            final boolean canUse = f.canRead() && hackCanUseDictionaryFile(f);
            if (canUse && DictionaryInfoUtils.isMainWordListId(wordListId)) {
                foundMainDict = true;
            }
            if (!dictPackSettings.isWordListActive(wordListId)) continue;
            if (canUse) {
                final AssetFileAddress afa = AssetFileAddress.makeFromFileName(f.getPath());
                if (null != afa) fileList.add(afa);
            } else {
                Log.e(TAG, "Found a cached dictionary file for " + locale.toString()
                        + " but cannot read or use it");
            }
        }

        if (!foundMainDict && dictPackSettings.isWordListActive(mainDictId)) {
            AssetFileAddress asset = Dictionaries.INSTANCE.getDictionaryIfExists(context, locale, Dictionaries.DictionaryKind.BinaryDictionary);
            if(asset == null && fallback) {
                asset = Dictionaries.INSTANCE.getFallbackDictionary(context);
            }

            if (null != asset) {
                fileList.add(asset);
            }
        }

        return fileList;
    }
}
