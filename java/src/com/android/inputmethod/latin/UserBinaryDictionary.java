/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.provider.UserDictionary.Words;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.compat.UserDictionaryCompatUtils;
import com.android.inputmethod.latin.utils.LocaleUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * An expandable dictionary that stores the words in the user dictionary provider into a binary
 * dictionary file to use it from native code.
 */
public class UserBinaryDictionary extends ExpandableBinaryDictionary {
    private static final String TAG = ExpandableBinaryDictionary.class.getSimpleName();

    // The user dictionary provider uses an empty string to mean "all languages".
    private static final String USER_DICTIONARY_ALL_LANGUAGES = "";
    private static final int HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY = 250;
    private static final int LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY = 160;
    // Shortcut frequency is 0~15, with 15 = whitelist. We don't want user dictionary entries
    // to auto-correct, so we set this to the highest frequency that won't, i.e. 14.
    private static final int USER_DICT_SHORTCUT_FREQUENCY = 14;

    // TODO: use Words.SHORTCUT when we target JellyBean or above
    final static String SHORTCUT = "shortcut";
    private static final String[] PROJECTION_QUERY;
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            PROJECTION_QUERY = new String[] {
                Words.WORD,
                SHORTCUT,
                Words.FREQUENCY,
            };
        } else {
            PROJECTION_QUERY = new String[] {
                Words.WORD,
                Words.FREQUENCY,
            };
        }
    }

    private static final String NAME = "userunigram";

    private ContentObserver mObserver;
    final private String mLocale;
    final private boolean mAlsoUseMoreRestrictiveLocales;

    public UserBinaryDictionary(final Context context, final String locale) {
        this(context, locale, false);
    }

    public UserBinaryDictionary(final Context context, final String locale,
            final boolean alsoUseMoreRestrictiveLocales) {
        super(context, getFilenameWithLocale(NAME, locale), Dictionary.TYPE_USER,
                false /* isUpdatable */);
        if (null == locale) throw new NullPointerException(); // Catch the error earlier
        if (SubtypeLocaleUtils.NO_LANGUAGE.equals(locale)) {
            // If we don't have a locale, insert into the "all locales" user dictionary.
            mLocale = USER_DICTIONARY_ALL_LANGUAGES;
        } else {
            mLocale = locale;
        }
        mAlsoUseMoreRestrictiveLocales = alsoUseMoreRestrictiveLocales;
        // Perform a managed query. The Activity will handle closing and re-querying the cursor
        // when needed.
        ContentResolver cres = context.getContentResolver();

        mObserver = new ContentObserver(null) {
            @Override
            public void onChange(final boolean self) {
                // This hook is deprecated as of API level 16 (Build.VERSION_CODES.JELLY_BEAN),
                // but should still be supported for cases where the IME is running on an older
                // version of the platform.
                onChange(self, null);
            }
            // The following hook is only available as of API level 16
            // (Build.VERSION_CODES.JELLY_BEAN), and as such it will only work on JellyBean+
            // devices. On older versions of the platform, the hook above will be called instead.
            @Override
            public void onChange(final boolean self, final Uri uri) {
                setRequiresReload(true);
            }
        };
        cres.registerContentObserver(Words.CONTENT_URI, true, mObserver);

        loadDictionary();
    }

    @Override
    public synchronized void close() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.close();
    }

    @Override
    public void loadDictionaryAsync() {
        // Split the locale. For example "en" => ["en"], "de_DE" => ["de", "DE"],
        // "en_US_foo_bar_qux" => ["en", "US", "foo_bar_qux"] because of the limit of 3.
        // This is correct for locale processing.
        // For this example, we'll look at the "en_US_POSIX" case.
        final String[] localeElements =
                TextUtils.isEmpty(mLocale) ? new String[] {} : mLocale.split("_", 3);
        final int length = localeElements.length;

        final StringBuilder request = new StringBuilder("(locale is NULL)");
        String localeSoFar = "";
        // At start, localeElements = ["en", "US", "POSIX"] ; localeSoFar = "" ;
        // and request = "(locale is NULL)"
        for (int i = 0; i < length; ++i) {
            // i | localeSoFar    | localeElements
            // 0 | ""             | ["en", "US", "POSIX"]
            // 1 | "en_"          | ["en", "US", "POSIX"]
            // 2 | "en_US_"       | ["en", "en_US", "POSIX"]
            localeElements[i] = localeSoFar + localeElements[i];
            localeSoFar = localeElements[i] + "_";
            // i | request
            // 0 | "(locale is NULL)"
            // 1 | "(locale is NULL) or (locale=?)"
            // 2 | "(locale is NULL) or (locale=?) or (locale=?)"
            request.append(" or (locale=?)");
        }
        // At the end, localeElements = ["en", "en_US", "en_US_POSIX"]; localeSoFar = en_US_POSIX_"
        // and request = "(locale is NULL) or (locale=?) or (locale=?) or (locale=?)"

        final String[] requestArguments;
        // If length == 3, we already have all the arguments we need (common prefix is meaningless
        // inside variants
        if (mAlsoUseMoreRestrictiveLocales && length < 3) {
            request.append(" or (locale like ?)");
            // The following creates an array with one more (null) position
            final String[] localeElementsWithMoreRestrictiveLocalesIncluded =
                    Arrays.copyOf(localeElements, length + 1);
            localeElementsWithMoreRestrictiveLocalesIncluded[length] =
                    localeElements[length - 1] + "_%";
            requestArguments = localeElementsWithMoreRestrictiveLocalesIncluded;
            // If for example localeElements = ["en"]
            // then requestArguments = ["en", "en_%"]
            // and request = (locale is NULL) or (locale=?) or (locale like ?)
            // If localeElements = ["en", "en_US"]
            // then requestArguments = ["en", "en_US", "en_US_%"]
        } else {
            requestArguments = localeElements;
        }
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(
                Words.CONTENT_URI, PROJECTION_QUERY, request.toString(), requestArguments, null);
            addWords(cursor);
        } catch (final SQLiteException e) {
            Log.e(TAG, "SQLiteException in the remote User dictionary process.", e);
        } finally {
            try {
                if (null != cursor) cursor.close();
            } catch (final SQLiteException e) {
                Log.e(TAG, "SQLiteException in the remote User dictionary process.", e);
            }
        }
    }

    public boolean isEnabled() {
        final ContentResolver cr = mContext.getContentResolver();
        final ContentProviderClient client = cr.acquireContentProviderClient(Words.CONTENT_URI);
        if (client != null) {
            client.release();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a word to the user dictionary and makes it persistent.
     *
     * @param word the word to add. If the word is capitalized, then the dictionary will
     * recognize it as a capitalized word when searched.
     */
    public synchronized void addWordToUserDictionary(final String word) {
        // Update the user dictionary provider
        final Locale locale;
        if (USER_DICTIONARY_ALL_LANGUAGES == mLocale) {
            locale = null;
        } else {
            locale = LocaleUtils.constructLocaleFromString(mLocale);
        }
        UserDictionaryCompatUtils.addWord(mContext, word,
                HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY, null, locale);
    }

    private int scaleFrequencyFromDefaultToLatinIme(final int defaultFrequency) {
        // The default frequency for the user dictionary is 250 for historical reasons.
        // Latin IME considers a good value for the default user dictionary frequency
        // is about 160 considering the scale we use. So we are scaling down the values.
        if (defaultFrequency > Integer.MAX_VALUE / LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY) {
            return (defaultFrequency / HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY)
                    * LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY;
        } else {
            return (defaultFrequency * LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY)
                    / HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY;
        }
    }

    private void addWords(final Cursor cursor) {
        final boolean hasShortcutColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
        if (cursor == null) return;
        if (cursor.moveToFirst()) {
            final int indexWord = cursor.getColumnIndex(Words.WORD);
            final int indexShortcut = hasShortcutColumn ? cursor.getColumnIndex(SHORTCUT) : 0;
            final int indexFrequency = cursor.getColumnIndex(Words.FREQUENCY);
            while (!cursor.isAfterLast()) {
                final String word = cursor.getString(indexWord);
                final String shortcut = hasShortcutColumn ? cursor.getString(indexShortcut) : null;
                final int frequency = cursor.getInt(indexFrequency);
                final int adjustedFrequency = scaleFrequencyFromDefaultToLatinIme(frequency);
                // Safeguard against adding really long words.
                if (word.length() < MAX_WORD_LENGTH) {
                    super.addWord(word, null, adjustedFrequency, 0 /* shortcutFreq */,
                            false /* isNotAWord */);
                }
                if (null != shortcut && shortcut.length() < MAX_WORD_LENGTH) {
                    super.addWord(shortcut, word, adjustedFrequency, USER_DICT_SHORTCUT_FREQUENCY,
                            true /* isNotAWord */);
                }
                cursor.moveToNext();
            }
        }
    }

    @Override
    protected boolean hasContentChanged() {
        return true;
    }

    @Override
    protected boolean needsToReloadBeforeWriting() {
        return true;
    }
}
