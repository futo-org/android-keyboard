/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.inputmethod.latin.spellcheck;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.utils.ExecutorUtils;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * UserDictionaryLookup provides the ability to lookup into the system-wide "Personal dictionary".
 *
 * Note, that the initial dictionary loading happens asynchronously so it is possible (hopefully
 * rarely) that isValidWord is called before the initial load has started.
 *
 * The caller should explicitly call close() when the object is no longer needed, in order to
 * release any resources and references to this object.  A service should create this object in
 * onCreate and close() it in onDestroy.
 */
public class UserDictionaryLookup implements Closeable {
    private static final String TAG = UserDictionaryLookup.class.getSimpleName();

    /**
     * This guards the execution of any Log.d() logging, so that if false, they are not even
     */
    private static final boolean DEBUG = false;

    /**
     * To avoid loading too many dictionary entries in memory, we cap them at this number.  If
     * that number is exceeded, the lowest-frequency items will be dropped.  Note, there is no
     * explicit cap on the number of locales in every entry.
     */
    private static final int MAX_NUM_ENTRIES = 1000;

    /**
     * The delay (in milliseconds) to impose on reloads.  Previously scheduled reloads will be
     * cancelled if a new reload is scheduled before the delay expires.  Thus, only the last
     * reload in the series of frequent reloads will execute.
     *
     * Note, this value should be low enough to allow the "Add to dictionary" feature in the
     * TextView correction (red underline) drop-down menu to work properly in the following case:
     *
     *   1. User types OOV (out-of-vocabulary) word.
     *   2. The OOV is red-underlined.
     *   3. User selects "Add to dictionary".  The red underline disappears while the OOV is
     *      in a composing span.
     *   4. The user taps space.  The red underline should NOT reappear.  If this value is very
     *      high and the user performs the space tap fast enough, the red underline may reappear.
     */
    @UsedForTesting
    static final int RELOAD_DELAY_MS = 200;

    private final ContentResolver mResolver;

    /**
     * Runnable that calls loadUserDictionary().
     */
    private class UserDictionaryLoader implements Runnable {
        @Override
        public void run() {
            if (DEBUG) {
                Log.d(TAG, "Executing (re)load");
            }
            loadUserDictionary();
        }
    }
    private final UserDictionaryLoader mLoader = new UserDictionaryLoader();

    /**
     *  Content observer for UserDictionary changes.  It has the following properties:
     *    1. It spawns off a UserDictionary reload in another thread, after some delay.
     *    2. It cancels previously scheduled reloads, and only executes the latest.
     *    3. It may be called multiple times quickly in succession (and is in fact called so
     *       when UserDictionary is edited through its settings UI, when sometimes multiple
     *       notifications are sent for the edited entry, but also for the entire UserDictionary).
     */
    private class UserDictionaryContentObserver extends ContentObserver {
        public UserDictionaryContentObserver() {
            super(null);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        // Support pre-API16 platforms.
        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (DEBUG) {
                Log.d(TAG, "Received content observer onChange notification for URI: " + uri);
            }
            // Cancel (but don't interrupt) any pending reloads (except the initial load).
            if (mReloadFuture != null && !mReloadFuture.isCancelled() &&
                    !mReloadFuture.isDone()) {
                // Note, that if already cancelled or done, this will do nothing.
                boolean isCancelled = mReloadFuture.cancel(false);
                if (DEBUG) {
                    if (isCancelled) {
                        Log.d(TAG, "Successfully canceled previous reload request");
                    } else {
                        Log.d(TAG, "Unable to cancel previous reload request");
                    }
                }
            }

            if (DEBUG) {
                Log.d(TAG, "Scheduling reload in " + RELOAD_DELAY_MS + " ms");
            }

            // Schedule a new reload after RELOAD_DELAY_MS.
            mReloadFuture = ExecutorUtils.getBackgroundExecutor().schedule(
                    mLoader, RELOAD_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }
    private final ContentObserver mObserver = new UserDictionaryContentObserver();

    /**
     * Indicates that a load is in progress, so no need for another.
     */
    private AtomicBoolean mIsLoading = new AtomicBoolean(false);

    /**
     * Indicates that this lookup object has been close()d.
     */
    private AtomicBoolean mIsClosed = new AtomicBoolean(false);

    /**
     * We store a map from a dictionary word to the set of locales it belongs
     * in. We then iterate over the set of locales to find a match using
     * LocaleUtils.
     */
    private volatile HashMap<String, ArrayList<Locale>> mDictWords;

    /**
     *  The last-scheduled reload future.  Saved in order to cancel a pending reload if a new one
     * is coming.
     */
    private volatile ScheduledFuture<?> mReloadFuture;

    /**
     * @param context the context from which to obtain content resolver
     */
    public UserDictionaryLookup(Context context) {
        if (DEBUG) {
            Log.d(TAG, "UserDictionaryLookup constructor with context: " + context);
        }

        // Obtain a content resolver.
        mResolver = context.getContentResolver();

        // Schedule the initial load to run immediately.  It's possible that the first call to
        // isValidWord occurs before the dictionary has actually loaded, so it should not
        // assume that the dictionary has been loaded.
        ExecutorUtils.getBackgroundExecutor().execute(mLoader);

        // Register the observer to be notified on changes to the UserDictionary and all individual
        // items.
        //
        // If the user is interacting with the UserDictionary settings UI, or with the
        // "Add to dictionary" drop-down option, duplicate notifications will be sent for the same
        // edit: if a new entry is added, there is a notification for the entry itself, and
        // separately for the entire dictionary. However, when used programmatically,
        // only notifications for the specific edits are sent. Thus, the observer is registered to
        // receive every possible notification, and instead has throttling logic to avoid doing too
        // many reloads.
        mResolver.registerContentObserver(
                UserDictionary.Words.CONTENT_URI, true /* notifyForDescendents */, mObserver);
    }

    /**
     * To be called by the garbage collector in the off chance that the service did not clean up
     * properly.  Do not rely on this getting called, and make sure close() is called explicitly.
     */
    @Override
    public void finalize() throws Throwable {
        try {
            if (DEBUG) {
                Log.d(TAG, "Finalize called, calling close()");
            }
            close();
        } finally {
            super.finalize();
        }
    }

    /**
     * Cleans up UserDictionaryLookup: shuts down any extra threads and unregisters the observer.
     *
     * It is safe, but not advised to call this multiple times, and isValidWord would continue to
     * work, but no data will be reloaded any longer.
     */
    @Override
    public void close() {
        if (DEBUG) {
            Log.d(TAG, "Close called (no pun intended), cleaning up executor and observer");
        }
        if (mIsClosed.compareAndSet(false, true)) {
            // Unregister the content observer.
            mResolver.unregisterContentObserver(mObserver);
        }
    }

    /**
     * Returns true if the initial load has been performed.
     *
     * @return true if the initial load is successful
     */
    @UsedForTesting
    boolean isLoaded() {
        return mDictWords != null;
    }

    /**
     * Determines if the given word is a valid word in the given locale based on the UserDictionary.
     * It tries hard to find a match: for example, casing is ignored and if the word is present in a
     * more general locale (e.g. en or all locales), and isValidWord is asking for a more specific
     * locale (e.g. en_US), it will be considered a match.
     *
     * @param word the word to match
     * @param locale the locale in which to match the word
     * @return true iff the word has been matched for this locale in the UserDictionary.
     */
    public boolean isValidWord(
            final String word, final Locale locale) {
        if (!isLoaded()) {
            // This is a corner case in the event the initial load of UserDictionary has not
            // been loaded. In that case, we assume the word is not a valid word in
            // UserDictionary.
            if (DEBUG) {
                Log.d(TAG, "isValidWord invoked, but initial load not complete");
            }
            return false;
        }

        // Atomically obtain the current copy of mDictWords;
        final HashMap<String, ArrayList<Locale>> dictWords = mDictWords;

        if (DEBUG) {
            Log.d(TAG, "isValidWord invoked for word [" + word +
                    "] in locale " + locale);
        }
        // Lowercase the word using the given locale. Note, that dictionary
        // words are lowercased using their locale, and theoretically the
        // lowercasing between two matching locales may differ. For simplicity
        // we ignore that possibility.
        final String lowercased = word.toLowerCase(locale);
        final ArrayList<Locale> dictLocales = dictWords.get(lowercased);
        if (null == dictLocales) {
            if (DEBUG) {
                Log.d(TAG, "isValidWord=false, since there is no entry for " +
                        "lowercased word [" + lowercased + "]");
            }
            return false;
        } else {
            if (DEBUG) {
                Log.d(TAG, "isValidWord found an entry for lowercased word [" + lowercased +
                        "]; examining locales");
            }
            // Iterate over the locales this word is in.
            for (final Locale dictLocale : dictLocales) {
                final int matchLevel = LocaleUtils.getMatchLevel(dictLocale.toString(),
                        locale.toString());
                if (DEBUG) {
                    Log.d(TAG, "matchLevel for dictLocale=" + dictLocale + ", locale=" +
                            locale + " is " + matchLevel);
                }
                if (LocaleUtils.isMatch(matchLevel)) {
                    if (DEBUG) {
                        Log.d(TAG, "isValidWord=true, since matchLevel " + matchLevel +
                                " is a match");
                    }
                    return true;
                }
                if (DEBUG) {
                    Log.d(TAG, "matchLevel " + matchLevel + " is not a match");
                }
            }
            if (DEBUG) {
                Log.d(TAG, "isValidWord=false, since none of the locales matched");
            }
            return false;
        }
    }

    /**
     * Loads the UserDictionary in the current thread.
     *
     * Only one reload can happen at a time. If already running, will exit quickly.
     */
    private void loadUserDictionary() {
        // Bail out if already in the process of loading.
        if (!mIsLoading.compareAndSet(false, true)) {
            if (DEBUG) {
                Log.d(TAG, "Already in the process of loading UserDictionary, skipping");
            }
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Loading UserDictionary");
        }
        HashMap<String, ArrayList<Locale>> dictWords = new HashMap<>();
        // Load the UserDictionary.  Request that items be returned in the default sort order
        // for UserDictionary, which is by frequency.
        Cursor cursor = mResolver.query(UserDictionary.Words.CONTENT_URI,
                null, null, null, UserDictionary.Words.DEFAULT_SORT_ORDER);
        if (null == cursor || cursor.getCount() < 1) {
            if (DEBUG) {
                Log.d(TAG, "No entries found in UserDictionary");
            }
        } else {
            // Iterate over the entries in the UserDictionary.  Note, that iteration is in
            // descending frequency by default.
            while (dictWords.size() < MAX_NUM_ENTRIES && cursor.moveToNext()) {
                // If there is no column for locale, skip this entry. An empty
                // locale on the other hand will not be skipped.
                final int dictLocaleIndex = cursor.getColumnIndex(
                        UserDictionary.Words.LOCALE);
                if (dictLocaleIndex < 0) {
                    if (DEBUG) {
                        Log.d(TAG, "Encountered UserDictionary entry " +
                                "without LOCALE, skipping");
                    }
                    continue;
                }
                // If there is no column for word, skip this entry.
                final int dictWordIndex = cursor.getColumnIndex(
                        UserDictionary.Words.WORD);
                if (dictWordIndex < 0) {
                    if (DEBUG) {
                        Log.d(TAG, "Encountered UserDictionary entry without " +
                                "WORD, skipping");
                    }
                    continue;
                }
                // If the word is null, skip this entry.
                final String rawDictWord = cursor.getString(dictWordIndex);
                if (null == rawDictWord) {
                    if (DEBUG) {
                        Log.d(TAG, "Encountered null word");
                    }
                    continue;
                }
                // If the locale is null, that's interpreted to mean all locales. Note, the special
                // zz locale for an Alphabet (QWERTY) layout will not match any actual language.
                String localeString = cursor.getString(dictLocaleIndex);
                if (null == localeString) {
                    if (DEBUG) {
                        Log.d(TAG, "Encountered null locale for word [" +
                                rawDictWord + "], assuming all locales");
                    }
                    // For purposes of LocaleUtils, an empty locale matches
                    // everything.
                    localeString = "";
                }
                final Locale dictLocale = LocaleUtils.constructLocaleFromString(
                        localeString);
                // Lowercase the word before storing it.
                final String dictWord = rawDictWord.toLowerCase(dictLocale);
                if (DEBUG) {
                    Log.d(TAG, "Incorporating UserDictionary word [" + dictWord +
                            "] for locale " + dictLocale);
                }
                // Check if there is an existing entry for this word.
                ArrayList<Locale> dictLocales = dictWords.get(dictWord);
                if (null == dictLocales) {
                    // If there is no entry for this word, create one.
                    if (DEBUG) {
                        Log.d(TAG, "Word [" + dictWord +
                                "] not seen for other locales, creating new entry");
                    }
                    dictLocales = new ArrayList<>();
                    dictWords.put(dictWord, dictLocales);
                }
                // Append the locale to the list of locales this word is in.
                dictLocales.add(dictLocale);
            }
        }

        // Atomically replace the copy of mDictWords.
        mDictWords = dictWords;

        // Allow other calls to loadUserDictionary to execute now.
        mIsLoading.set(false);
    }
}
