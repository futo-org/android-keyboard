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

package org.futo.inputmethod.latin.utils;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;

import org.futo.inputmethod.annotations.UsedForTesting;
import org.futo.inputmethod.latin.BinaryDictionaryGetter;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.define.DecoderSpecificConstants;
import org.futo.inputmethod.latin.makedict.DictionaryHeader;
import org.futo.inputmethod.latin.makedict.UnsupportedFormatException;
import org.futo.inputmethod.latin.settings.SpacingAndPunctuations;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class encapsulates the logic for the Latin-IME side of dictionary information management.
 */
public class DictionaryInfoUtils {
    private static final String TAG = DictionaryInfoUtils.class.getSimpleName();
    public static final String RESOURCE_PACKAGE_NAME = R.class.getPackage().getName();
    private static final String DEFAULT_MAIN_DICT = "main";
    private static final String MAIN_DICT_PREFIX = "main_";
    private static final String DECODER_DICT_SUFFIX = DecoderSpecificConstants.DECODER_DICT_SUFFIX;
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

        @Nonnull public final String mId;
        @Nonnull public final Locale mLocale;
        @Nullable public final String mDescription;
        @Nullable public final String mFilename;
        public final long mFilesize;
        public final long mModifiedTimeMillis;
        public final int mVersion;

        public DictionaryInfo(@Nonnull String id, @Nonnull Locale locale,
                @Nullable String description, @Nullable String filename,
                long filesize, long modifiedTimeMillis, int version) {
            mId = id;
            mLocale = locale;
            mDescription = description;
            mFilename = filename;
            mFilesize = filesize;
            mModifiedTimeMillis = modifiedTimeMillis;
            mVersion = version;
        }

        public ContentValues toContentValues() {
            final ContentValues values = new ContentValues();
            values.put(WORDLISTID_COLUMN, mId);
            values.put(LOCALE_COLUMN, mLocale.toString());
            values.put(DESCRIPTION_COLUMN, mDescription);
            values.put(LOCAL_FILENAME_COLUMN, mFilename != null ? mFilename : "");
            values.put(DATE_COLUMN, TimeUnit.MILLISECONDS.toSeconds(mModifiedTimeMillis));
            values.put(FILESIZE_COLUMN, mFilesize);
            values.put(VERSION_COLUMN, mVersion);
            return values;
        }

        @Override
        public String toString() {
            return "DictionaryInfo : Id = '" + mId
                    + "' : Locale=" + mLocale
                    + " : Version=" + mVersion;
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
     * Reverse escaping done by {@link #replaceFileNameDangerousCharacters(String)}.
     */
    @Nonnull
    public static String getWordListIdFromFileName(@Nonnull final String fname) {
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
    @Nullable
    public static String getCategoryFromFileName(@Nonnull final String fileName) {
        final String id = getWordListIdFromFileName(fileName);
        final String[] idArray = id.split(BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR);
        // An id is supposed to be in format category:locale, so splitting on the separator
        // should yield a 2-elements array
        if (2 != idArray.length) {
            return null;
        }
        return idArray[0];
    }

    public static boolean isMainWordListId(final String id) {
        final String[] idArray = id.split(BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR);
        // An id is supposed to be in format category:locale, so splitting on the separator
        // should yield a 2-elements array
        if (2 != idArray.length) {
            return false;
        }
        return BinaryDictionaryGetter.MAIN_DICTIONARY_CATEGORY.equals(idArray[0]);
    }

    /**
     * Returns the id associated with the main word list for a specified locale.
     *
     * Word lists stored in Android Keyboard's resources are referred to as the "main"
     * word lists. Since they can be updated like any other list, we need to assign a
     * unique ID to them. This ID is just the name of the language (locale-wise) they
     * are for, and this method returns this ID.
     */
    public static String getMainDictId(@Nonnull final Locale locale) {
        // This works because we don't include by default different dictionaries for
        // different countries. This actually needs to return the id that we would
        // like to use for word lists included in resources, and the following is okay.
        return BinaryDictionaryGetter.MAIN_DICTIONARY_CATEGORY +
                BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR + locale.toString().toLowerCase();
    }

    public static DictionaryHeader getDictionaryFileHeaderOrNull(final File file,
            final long offset, final long length) {
        try {
            final DictionaryHeader header =
                    BinaryDictionaryUtils.getHeaderWithOffsetAndLength(file, offset, length);
            return header;
        } catch (UnsupportedFormatException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    @UsedForTesting
    public static boolean looksValidForDictionaryInsertion(final CharSequence text,
            final SpacingAndPunctuations spacingAndPunctuations) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        final int length = text.length();
        if (length > DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH) {
            return false;
        }
        int i = 0;
        int digitCount = 0;
        while (i < length) {
            final int codePoint = Character.codePointAt(text, i);
            final int charCount = Character.charCount(codePoint);
            i += charCount;
            if (Character.isDigit(codePoint)) {
                // Count digits: see below
                digitCount += charCount;
                continue;
            }
            if (!spacingAndPunctuations.isWordCodePoint(codePoint)) {
                return false;
            }
        }
        // We reject strings entirely comprised of digits to avoid using PIN codes or credit
        // card numbers. It would come in handy for word prediction though; a good example is
        // when writing one's address where the street number is usually quite discriminative,
        // as well as the postal code.
        return digitCount < length;
    }
}
