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
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary.Words;
import android.text.TextUtils;

import java.util.Arrays;

/**
 * An expandable dictionary that stores the words in the user unigram dictionary.
 *
 * Largely a copy of UserDictionary, will replace that class in the future.
 */
public class UserBinaryDictionary extends ExpandableBinaryDictionary {

    // The user dictionary provider uses an empty string to mean "all languages".
    private static final String USER_DICTIONARY_ALL_LANGUAGES = "";

    // TODO: use Words.SHORTCUT when we target JellyBean or above
    final static String SHORTCUT = "shortcut";
    private static final String[] PROJECTION_QUERY;
    static {
        // 16 is JellyBean, but we want this to compile against ICS.
        if (android.os.Build.VERSION.SDK_INT >= 16) {
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

    // This is not exported by the framework so we pretty much have to write it here verbatim
    private static final String ACTION_USER_DICTIONARY_INSERT =
            "com.android.settings.USER_DICTIONARY_INSERT";

    private ContentObserver mObserver;
    final private String mLocale;
    final private boolean mAlsoUseMoreRestrictiveLocales;

    public UserBinaryDictionary(final Context context, final String locale) {
        this(context, locale, false);
    }

    public UserBinaryDictionary(final Context context, final String locale,
            final boolean alsoUseMoreRestrictiveLocales) {
        super(context, getFilenameWithLocale(NAME, locale), Dictionary.TYPE_USER);
        if (null == locale) throw new NullPointerException(); // Catch the error earlier
        if (SubtypeLocale.NO_LANGUAGE.equals(locale)) {
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
                // This hook is deprecated as of API level 16, but should still be supported for
                // cases where the IME is running on an older version of the platform.
                onChange(self, null);
            }
            // The following hook is only available as of API level 16, and as such it will only
            // work on JellyBean+ devices. On older versions of the platform, the hook
            // above will be called instead.
            @Override
            public void onChange(final boolean self, final Uri uri) {
                setRequiresReload(true);
                // We want to report back to Latin IME in case the user just entered the word.
                // If the user changed the word in the dialog box, then we want to replace
                // what was entered in the text field.
                if (null == uri || !(context instanceof LatinIME)) return;
                final long changedRowId = ContentUris.parseId(uri);
                if (-1 == changedRowId) return; // Unknown content... Not sure why we're here
                final String changedWord = getChangedWordForUri(uri);
                ((LatinIME)context).onWordAddedToUserDictionary(changedWord);
            }
        };
        cres.registerContentObserver(Words.CONTENT_URI, true, mObserver);

        loadDictionary();
    }

    private String getChangedWordForUri(final Uri uri) {
        final Cursor cursor = mContext.getContentResolver().query(uri,
                PROJECTION_QUERY, null, null, null);
        if (cursor == null) return null;
        try {
            if (!cursor.moveToFirst()) return null;
            final int indexWord = cursor.getColumnIndex(Words.WORD);
            return cursor.getString(indexWord);
        } finally {
            cursor.close();
        }
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
        final Cursor cursor = mContext.getContentResolver().query(
                Words.CONTENT_URI, PROJECTION_QUERY, request.toString(), requestArguments, null);
        try {
            addWords(cursor);
        } finally {
            if (null != cursor) cursor.close();
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
     * This will call upon the system interface to do the actual work through the intent readied by
     * the system to this effect.
     *
     * @param word the word to add. If the word is capitalized, then the dictionary will
     * recognize it as a capitalized word when searched.
     * @param frequency the frequency of occurrence of the word. A frequency of 255 is considered
     * the highest.
     * @TODO use a higher or float range for frequency
     */
    public synchronized void addWordToUserDictionary(final String word, final int frequency) {
        // TODO: do something for the UI. With the following, any sufficiently long word will
        // look like it will go to the user dictionary but it won't.
        // Safeguard against adding long words. Can cause stack overflow.
        if (word.length() >= MAX_WORD_LENGTH) return;

        // TODO: Add an argument to the intent to specify the frequency.
        Intent intent = new Intent(ACTION_USER_DICTIONARY_INSERT);
        intent.putExtra(Words.WORD, word);
        intent.putExtra(Words.LOCALE, mLocale);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void addWords(Cursor cursor) {
        // 16 is JellyBean, but we want this to compile against ICS.
        final boolean hasShortcutColumn = android.os.Build.VERSION.SDK_INT >= 16;
        clearFusionDictionary();
        if (cursor == null) return;
        if (cursor.moveToFirst()) {
            final int indexWord = cursor.getColumnIndex(Words.WORD);
            final int indexShortcut = hasShortcutColumn ? cursor.getColumnIndex(SHORTCUT) : 0;
            final int indexFrequency = cursor.getColumnIndex(Words.FREQUENCY);
            while (!cursor.isAfterLast()) {
                final String word = cursor.getString(indexWord);
                final String shortcut = hasShortcutColumn ? cursor.getString(indexShortcut) : null;
                final int frequency = cursor.getInt(indexFrequency);
                // Safeguard against adding really long words.
                if (word.length() < MAX_WORD_LENGTH) {
                    super.addWord(word, null, frequency);
                }
                if (null != shortcut && shortcut.length() < MAX_WORD_LENGTH) {
                    super.addWord(shortcut, word, frequency);
                }
                cursor.moveToNext();
            }
        }
    }

    @Override
    protected boolean hasContentChanged() {
        return true;
    }
}
