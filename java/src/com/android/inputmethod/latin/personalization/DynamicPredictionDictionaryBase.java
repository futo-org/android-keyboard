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

package com.android.inputmethod.latin.personalization;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.ExpandableDictionary;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.makedict.BinaryDictDecoder;
import com.android.inputmethod.latin.makedict.FormatSpec.FormatOptions;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils.BigramDictionaryInterface;
import com.android.inputmethod.latin.utils.UserHistoryDictIOUtils.OnAddWordListener;
import com.android.inputmethod.latin.utils.UserHistoryForgettingCurveUtils;
import com.android.inputmethod.latin.utils.UserHistoryForgettingCurveUtils.ForgettingCurveParams;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is a base class of a dictionary for the personalized prediction language model.
 */
public abstract class DynamicPredictionDictionaryBase extends ExpandableDictionary {

    private static final String TAG = DynamicPredictionDictionaryBase.class.getSimpleName();
    public static final boolean DBG_SAVE_RESTORE = false;
    private static final boolean DBG_STRESS_TEST = false;
    private static final boolean PROFILE_SAVE_RESTORE = LatinImeLogger.sDBG;

    private static final FormatOptions VERSION3 = new FormatOptions(3,
            true /* supportsDynamicUpdate */);

    /** Any pair being typed or picked */
    private static final int FREQUENCY_FOR_TYPED = 2;

    /** Maximum number of pairs. Pruning will start when databases goes above this number. */
    private static final int MAX_HISTORY_BIGRAMS = 10000;

    /** Locale for which this user history dictionary is storing words */
    private final String mLocale;

    private final UserHistoryDictionaryBigramList mBigramList =
            new UserHistoryDictionaryBigramList();
    private final ReentrantLock mBigramListLock = new ReentrantLock();
    private final SharedPreferences mPrefs;

    private final ArrayList<PersonalizationDictionaryUpdateSession> mSessions =
            CollectionUtils.newArrayList();

    private final AtomicReference<AsyncTask<Void, Void, Void>> mWaitingTask;

    // Should always be false except when we use this class for test
    @UsedForTesting boolean mIsTest = false;

    /* package */ DynamicPredictionDictionaryBase(final Context context, final String locale,
            final SharedPreferences sp, final String dictionaryType) {
        super(context, dictionaryType);
        mLocale = locale;
        mPrefs = sp;
        mWaitingTask = new AtomicReference<AsyncTask<Void, Void, Void>>();
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
        // Ignore close because we cache PersonalizationPredictionDictionary for each language.
        // See getInstance() above.
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
     * Pair will be added to the personalization prediction dictionary.
     *
     * The first word may be null. That means we don't know the context, in other words,
     * it's only a unigram. The first word may also be an empty string : this means start
     * context, as in beginning of a sentence for example.
     * The second word may not be null (a NullPointerException would be thrown).
     */
    public int addToPersonalizationPredictionDictionary(
            final String word1, final String word2, final boolean isValid) {
        if (word2.length() >= Constants.DICTIONARY_MAX_WORD_LENGTH ||
                (word1 != null && word1.length() >= Constants.DICTIONARY_MAX_WORD_LENGTH)) {
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
        final AsyncTask<Void, Void, Void> old = mWaitingTask.getAndSet(new UpdateBinaryTask(
                mBigramList, mLocale, this, mPrefs, getContext()).execute());
        if (old != null) {
            old.cancel(false);
        }
    }

    @Override
    public final void loadDictionaryAsync() {
        // This must be run on non-main thread
        mBigramListLock.lock();
        try {
            loadDictionaryAsyncLocked();
        } finally {
            mBigramListLock.unlock();
        }
    }

    private void loadDictionaryAsyncLocked() {
        final int[] profTotalCount = { 0 };
        final String locale = getLocale();
        if (DBG_STRESS_TEST) {
            try {
                Log.w(TAG, "Start stress in loading: " + locale);
                Thread.sleep(15000);
                Log.w(TAG, "End stress in loading");
            } catch (InterruptedException e) {
            }
        }
        final long last = Settings.readLastUserHistoryWriteTime(mPrefs, locale);
        final boolean initializing = last == 0;
        final long now = System.currentTimeMillis();
        final String fileName = getDictionaryFileName();
        final ExpandableDictionary dictionary = this;
        final OnAddWordListener listener = new OnAddWordListener() {
            @Override
            public void setUnigram(final String word, final String shortcutTarget,
                    final int frequency) {
                if (DBG_SAVE_RESTORE) {
                    Log.d(TAG, "load unigram: " + word + "," + frequency);
                }
                dictionary.addWord(word, shortcutTarget, frequency);
                ++profTotalCount[0];
                addToBigramListLocked(null, word, (byte)frequency);
            }

            @Override
            public void setBigram(final String word1, final String word2, final int frequency) {
                if (word1.length() < Constants.DICTIONARY_MAX_WORD_LENGTH
                        && word2.length() < Constants.DICTIONARY_MAX_WORD_LENGTH) {
                    if (DBG_SAVE_RESTORE) {
                        Log.d(TAG, "load bigram: " + word1 + "," + word2 + "," + frequency);
                    }
                    ++profTotalCount[0];
                    dictionary.setBigramAndGetFrequency(
                            word1, word2, initializing ? new ForgettingCurveParams(true)
                            : new ForgettingCurveParams(frequency, now, last));
                }
                addToBigramListLocked(word1, word2, (byte)frequency);
            }
        };

        // Load the dictionary from binary file
        final BinaryDictDecoder reader = new BinaryDictDecoder(
                new File(getContext().getFilesDir(), fileName));
        try {
            reader.openDictBuffer(new BinaryDictDecoder.DictionaryBufferFromByteArrayFactory());
            UserHistoryDictIOUtils.readDictionaryBinary(reader, listener);
        } catch (FileNotFoundException e) {
            // This is an expected condition: we don't have a user history dictionary for this
            // language yet. It will be created sometime later.
        } catch (IOException e) {
            Log.e(TAG, "IOException on opening a bytebuffer", e);
        } finally {
            if (PROFILE_SAVE_RESTORE) {
                final long diff = System.currentTimeMillis() - now;
                Log.d(TAG, "PROF: Load UserHistoryDictionary: "
                        + locale + ", " + diff + "ms. load " + profTotalCount[0] + "entries.");
            }
        }
    }

    protected abstract String getDictionaryFileName();

    protected String getLocale() {
        return mLocale;
    }

    private void addToBigramListLocked(String word0, String word1, byte fcValue) {
        mBigramList.addBigram(word0, word1, fcValue);
    }

    /**
     * Async task to write pending words to the binarydicts.
     */
    private static final class UpdateBinaryTask extends AsyncTask<Void, Void, Void>
            implements BigramDictionaryInterface {
        private final UserHistoryDictionaryBigramList mBigramList;
        private final boolean mAddLevel0Bigrams;
        private final String mLocale;
        private final DynamicPredictionDictionaryBase mDynamicPredictionDictionary;
        private final SharedPreferences mPrefs;
        private final Context mContext;

        public UpdateBinaryTask(final UserHistoryDictionaryBigramList pendingWrites,
                final String locale, final DynamicPredictionDictionaryBase dict,
                final SharedPreferences prefs, final Context context) {
            mBigramList = pendingWrites;
            mLocale = locale;
            mDynamicPredictionDictionary = dict;
            mPrefs = prefs;
            mContext = context;
            mAddLevel0Bigrams = mBigramList.size() <= MAX_HISTORY_BIGRAMS;
        }

        @Override
        protected Void doInBackground(final Void... v) {
            if (isCancelled()) return null;
            if (mDynamicPredictionDictionary.mIsTest) {
                // If mIsTest == true, wait until the lock is released.
                mDynamicPredictionDictionary.mBigramListLock.lock();
                try {
                    doWriteTaskLocked();
                } finally {
                    mDynamicPredictionDictionary.mBigramListLock.unlock();
                }
            } else if (mDynamicPredictionDictionary.mBigramListLock.tryLock()) {
                try {
                    doWriteTaskLocked();
                } finally {
                    mDynamicPredictionDictionary.mBigramListLock.unlock();
                }
            }
            return null;
        }

        private void doWriteTaskLocked() {
            if (isCancelled()) return;
            mDynamicPredictionDictionary.mWaitingTask.compareAndSet(this, null);

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
            final String fileName =
                    mDynamicPredictionDictionary.getDictionaryFileName();
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
                final NextWord nw =
                        mDynamicPredictionDictionary.getBigramWord(word1, word2);
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
    /* package for test */ void forceAddWordForTest(
            final String word1, final String word2, final boolean isValid) {
        mBigramListLock.lock();
        try {
            addToPersonalizationPredictionDictionary(word1, word2, isValid);
        } finally {
            mBigramListLock.unlock();
        }
    }

    public void registerUpdateSession(PersonalizationDictionaryUpdateSession session) {
        session.setPredictionDictionary(mLocale, this);
        mSessions.add(session);
        session.onDictionaryReady();
    }

    public void unRegisterUpdateSession(PersonalizationDictionaryUpdateSession session) {
        mSessions.remove(session);
    }
}
