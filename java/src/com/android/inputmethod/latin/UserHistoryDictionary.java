/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.UserHistoryDictIOUtils.BigramDictionaryInterface;
import com.android.inputmethod.latin.UserHistoryDictIOUtils.OnAddWordListener;
import com.android.inputmethod.latin.UserHistoryForgettingCurveUtils.ForgettingCurveParams;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Locally gathers stats about the words user types and various other signals like auto-correction
 * cancellation or manual picks. This allows the keyboard to adapt to the typist over time.
 */
public final class UserHistoryDictionary extends ExpandableDictionary {
    private static final String TAG = UserHistoryDictionary.class.getSimpleName();
    private static final String NAME = UserHistoryDictionary.class.getSimpleName();
    public static final boolean DBG_SAVE_RESTORE = false;
    public static final boolean DBG_STRESS_TEST = false;
    public static final boolean DBG_ALWAYS_WRITE = false;
    public static final boolean PROFILE_SAVE_RESTORE = LatinImeLogger.sDBG;

    private static final FormatOptions VERSION3 = new FormatOptions(3,
            true /* supportsDynamicUpdate */);

    /** Any pair being typed or picked */
    private static final int FREQUENCY_FOR_TYPED = 2;

    /** Maximum number of pairs. Pruning will start when databases goes above this number. */
    public static final int MAX_HISTORY_BIGRAMS = 10000;

    /**
     * When it hits maximum bigram pair, it will delete until you are left with
     * only (sMaxHistoryBigrams - sDeleteHistoryBigrams) pairs.
     * Do not keep this number small to avoid deleting too often.
     */
    public static final int DELETE_HISTORY_BIGRAMS = 1000;

    /** Locale for which this user history dictionary is storing words */
    private final String mLocale;

    private final UserHistoryDictionaryBigramList mBigramList =
            new UserHistoryDictionaryBigramList();
    private final ReentrantLock mBigramListLock = new ReentrantLock();
    private final SharedPreferences mPrefs;

    // Should always be false except when we use this class for test
    @UsedForTesting boolean isTest = false;

    private static final ConcurrentHashMap<String, SoftReference<UserHistoryDictionary>>
            sLangDictCache = CollectionUtils.newConcurrentHashMap();

    public static synchronized UserHistoryDictionary getInstance(
            final Context context, final String locale, final SharedPreferences sp) {
        if (sLangDictCache.containsKey(locale)) {
            final SoftReference<UserHistoryDictionary> ref = sLangDictCache.get(locale);
            final UserHistoryDictionary dict = ref == null ? null : ref.get();
            if (dict != null) {
                if (PROFILE_SAVE_RESTORE) {
                    Log.w(TAG, "Use cached UserHistoryDictionary for " + locale);
                }
                return dict;
            }
        }
        final UserHistoryDictionary dict =
                new UserHistoryDictionary(context, locale, sp);
        sLangDictCache.put(locale, new SoftReference<UserHistoryDictionary>(dict));
        return dict;
    }

    private UserHistoryDictionary(final Context context, final String locale,
            final SharedPreferences sp) {
        super(context, Dictionary.TYPE_USER_HISTORY);
        mLocale = locale;
        mPrefs = sp;
        if (mLocale != null && mLocale.length() > 1) {
            loadDictionary();
        }
    }

    @Override
    public void close() {
        flushPendingWrites();
        // Don't close the database as locale changes will require it to be reopened anyway
        // Also, the database is written to somewhat frequently, so it needs to be kept alive
        // throughout the life of the process.
        // mOpenHelper.close();
        // Ignore close because we cache UserHistoryDictionary for each language. See getInstance()
        // above.
        // super.close();
    }

    @Override
    protected ArrayList<SuggestedWordInfo> getWordsInner(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo) {
        // Inhibit suggestions (not predictions) for user history for now. Removing this method
        // is enough to use it through the standard ExpandableDictionary way.
        return null;
    }

    /**
     * Return whether the passed charsequence is in the dictionary.
     */
    @Override
    public synchronized boolean isValidWord(final String word) {
        // TODO: figure out what is the correct thing to do here.
        return false;
    }

    /**
     * Pair will be added to the user history dictionary.
     *
     * The first word may be null. That means we don't know the context, in other words,
     * it's only a unigram. The first word may also be an empty string : this means start
     * context, as in beginning of a sentence for example.
     * The second word may not be null (a NullPointerException would be thrown).
     */
    public int addToUserHistory(final String word1, final String word2, final boolean isValid) {
        if (word2.length() >= Constants.Dictionary.MAX_WORD_LENGTH ||
                (word1 != null && word1.length() >= Constants.Dictionary.MAX_WORD_LENGTH)) {
            return -1;
        }
        if (mBigramListLock.tryLock()) {
            try {
                super.addWord(
                        word2, null /* the "shortcut" parameter is null */, FREQUENCY_FOR_TYPED);
                mBigramList.addBigram(null, word2, (byte)FREQUENCY_FOR_TYPED);
                // Do not insert a word as a bigram of itself
                if (word2.equals(word1)) {
                    return 0;
                }
                final int freq;
                if (null == word1) {
                    freq = FREQUENCY_FOR_TYPED;
                } else {
                    freq = super.setBigramAndGetFrequency(
                            word1, word2, new ForgettingCurveParams(isValid));
                }
                mBigramList.addBigram(word1, word2);
                return freq;
            } finally {
                mBigramListLock.unlock();
            }
        }
        return -1;
    }

    public boolean cancelAddingUserHistory(final String word1, final String word2) {
        if (mBigramListLock.tryLock()) {
            try {
                if (mBigramList.removeBigram(word1, word2)) {
                    return super.removeBigram(word1, word2);
                }
            } finally {
                mBigramListLock.unlock();
            }
        }
        return false;
    }

    /**
     * Schedules a background thread to write any pending words to the database.
     */
    private void flushPendingWrites() {
        // Create a background thread to write the pending entries
        new UpdateBinaryTask(mBigramList, mLocale, this, mPrefs, getContext()).execute();
    }

    @Override
    public void loadDictionaryAsync() {
        // This must be run on non-main thread
        mBigramListLock.lock();
        try {
            loadDictionaryAsyncLocked();
        } finally {
            mBigramListLock.unlock();
        }
    }

    private int profTotal;

    private void loadDictionaryAsyncLocked() {
        if (DBG_STRESS_TEST) {
            try {
                Log.w(TAG, "Start stress in loading: " + mLocale);
                Thread.sleep(15000);
                Log.w(TAG, "End stress in loading");
            } catch (InterruptedException e) {
            }
        }
        final long last = Settings.readLastUserHistoryWriteTime(mPrefs, mLocale);
        final boolean initializing = last == 0;
        final long now = System.currentTimeMillis();
        profTotal = 0;
        final String fileName = NAME + "." + mLocale + ".dict";
        final ExpandableDictionary dictionary = this;
        final OnAddWordListener listener = new OnAddWordListener() {
            @Override
            public void setUnigram(final String word, final String shortcutTarget,
                    final int frequency) {
                profTotal++;
                if (DBG_SAVE_RESTORE) {
                    Log.d(TAG, "load unigram: " + word + "," + frequency);
                }
                dictionary.addWord(word, shortcutTarget, frequency);
                mBigramList.addBigram(null, word, (byte)frequency);
            }

            @Override
            public void setBigram(final String word1, final String word2, final int frequency) {
                if (word1.length() < Constants.Dictionary.MAX_WORD_LENGTH
                        && word2.length() < Constants.Dictionary.MAX_WORD_LENGTH) {
                    profTotal++;
                    if (DBG_SAVE_RESTORE) {
                        Log.d(TAG, "load bigram: " + word1 + "," + word2 + "," + frequency);
                    }
                    dictionary.setBigramAndGetFrequency(
                            word1, word2, initializing ? new ForgettingCurveParams(true)
                            : new ForgettingCurveParams(frequency, now, last));
                }
                mBigramList.addBigram(word1, word2, (byte)frequency);
            }
        };

        // Load the dictionary from binary file
        FileInputStream inStream = null;
        try {
            final File file = new File(getContext().getFilesDir(), fileName);
            final byte[] buffer = new byte[(int)file.length()];
            inStream = new FileInputStream(file);
            inStream.read(buffer);
            UserHistoryDictIOUtils.readDictionaryBinary(
                    new UserHistoryDictIOUtils.ByteArrayWrapper(buffer), listener);
        } catch (FileNotFoundException e) {
            // This is an expected condition: we don't have a user history dictionary for this
            // language yet. It will be created sometime later.
        } catch (IOException e) {
            Log.e(TAG, "IOException on opening a bytebuffer", e);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
            if (PROFILE_SAVE_RESTORE) {
                final long diff = System.currentTimeMillis() - now;
                Log.d(TAG, "PROF: Load UserHistoryDictionary: "
                        + mLocale + ", " + diff + "ms. load " + profTotal + "entries.");
            }
        }
    }

    /**
     * Async task to write pending words to the binarydicts.
     */
    private static final class UpdateBinaryTask extends AsyncTask<Void, Void, Void>
            implements BigramDictionaryInterface {
        private final UserHistoryDictionaryBigramList mBigramList;
        private final boolean mAddLevel0Bigrams;
        private final String mLocale;
        private final UserHistoryDictionary mUserHistoryDictionary;
        private final SharedPreferences mPrefs;
        private final Context mContext;

        public UpdateBinaryTask(final UserHistoryDictionaryBigramList pendingWrites,
                final String locale, final UserHistoryDictionary dict,
                final SharedPreferences prefs, final Context context) {
            mBigramList = pendingWrites;
            mLocale = locale;
            mUserHistoryDictionary = dict;
            mPrefs = prefs;
            mContext = context;
            mAddLevel0Bigrams = mBigramList.size() <= MAX_HISTORY_BIGRAMS;
        }

        @Override
        protected Void doInBackground(final Void... v) {
            if (mUserHistoryDictionary.isTest) {
                // If isTest == true, wait until the lock is released.
                mUserHistoryDictionary.mBigramListLock.lock();
                try {
                    doWriteTaskLocked();
                } finally {
                    mUserHistoryDictionary.mBigramListLock.unlock();
                }
            } else if (mUserHistoryDictionary.mBigramListLock.tryLock()) {
                doWriteTaskLocked();
            }
            return null;
        }

        private void doWriteTaskLocked() {
            if (DBG_STRESS_TEST) {
                try {
                    Log.w(TAG, "Start stress in closing: " + mLocale);
                    Thread.sleep(15000);
                    Log.w(TAG, "End stress in closing");
                } catch (InterruptedException e) {
                    Log.e(TAG, "In stress test", e);
                }
            }

            final long now = PROFILE_SAVE_RESTORE ? System.currentTimeMillis() : 0;
            final String fileName = NAME + "." + mLocale + ".dict";
            final File file = new File(mContext.getFilesDir(), fileName);
            FileOutputStream out = null;

            try {
                out = new FileOutputStream(file);
                UserHistoryDictIOUtils.writeDictionaryBinary(out, this, mBigramList, VERSION3);
                out.flush();
                out.close();
            } catch (IOException e) {
                Log.e(TAG, "IO Exception while writing file", e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

            // Save the timestamp after we finish writing the binary dictionary.
            Settings.writeLastUserHistoryWriteTime(mPrefs, mLocale);
            if (PROFILE_SAVE_RESTORE) {
                final long diff = System.currentTimeMillis() - now;
                Log.w(TAG, "PROF: Write User HistoryDictionary: " + mLocale + ", " + diff + "ms.");
            }
        }

        @Override
        public int getFrequency(final String word1, final String word2) {
            final int freq;
            if (word1 == null) { // unigram
                freq = FREQUENCY_FOR_TYPED;
                final byte prevFc = mBigramList.getBigrams(word1).get(word2);
            } else { // bigram
                final NextWord nw = mUserHistoryDictionary.getBigramWord(word1, word2);
                if (nw != null) {
                    final ForgettingCurveParams fcp = nw.getFcParams();
                    final byte prevFc = mBigramList.getBigrams(word1).get(word2);
                    final byte fc = fcp.getFc();
                    final boolean isValid = fcp.isValid();
                    if (prevFc > 0 && prevFc == fc) {
                        freq = fc & 0xFF;
                    } else if (UserHistoryForgettingCurveUtils.
                            needsToSave(fc, isValid, mAddLevel0Bigrams)) {
                        freq = fc & 0xFF;
                    } else {
                        // Delete this entry
                        freq = -1;
                    }
                } else {
                    // Delete this entry
                    freq = -1;
                }
            }
            return freq;
        }
    }

    @UsedForTesting
    void forceAddWordForTest(final String word1, final String word2, final boolean isValid) {
        mBigramListLock.lock();
        try {
            addToUserHistory(word1, word2, isValid);
        } finally {
            mBigramListLock.unlock();
        }
    }
}
