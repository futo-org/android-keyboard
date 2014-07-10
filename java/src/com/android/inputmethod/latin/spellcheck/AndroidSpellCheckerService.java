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

package com.android.inputmethod.latin.spellcheck;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.service.textservice.SpellCheckerService;
import android.text.InputType;
import android.util.Log;
import android.util.LruCache;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;
import android.view.textservice.SuggestionsInfo;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.ContactsBinaryDictionary;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.DictionaryCollection;
import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.DictionaryFactory;
import com.android.inputmethod.latin.PrevWordsInfo;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;
import com.android.inputmethod.latin.UserBinaryDictionary;
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.LocaleUtils;
import com.android.inputmethod.latin.utils.ScriptUtils;
import com.android.inputmethod.latin.utils.StringUtils;
import com.android.inputmethod.latin.utils.SuggestionResults;
import com.android.inputmethod.latin.WordComposer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Service for spell checking, using LatinIME's dictionaries and mechanisms.
 */
public final class AndroidSpellCheckerService extends SpellCheckerService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = AndroidSpellCheckerService.class.getSimpleName();
    private static final boolean DBG = false;

    public static final String PREF_USE_CONTACTS_KEY = "pref_spellcheck_use_contacts";

    private static final int SPELLCHECKER_DUMMY_KEYBOARD_WIDTH = 480;
    private static final int SPELLCHECKER_DUMMY_KEYBOARD_HEIGHT = 368;

    private static final String DICTIONARY_NAME_PREFIX = "spellcheck_";
    private static final int WAIT_FOR_LOADING_MAIN_DICT_IN_MILLISECONDS = 1000;
    private static final int MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT = 5;

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private final HashSet<Locale> mCachedLocales = new HashSet<>();

    private final int MAX_NUM_OF_THREADS_READ_DICTIONARY = 2;
    private final Semaphore mSemaphore = new Semaphore(MAX_NUM_OF_THREADS_READ_DICTIONARY,
            true /* fair */);
    // TODO: Make each spell checker session has its own session id.
    private final ConcurrentLinkedQueue<Integer> mSessionIdPool = new ConcurrentLinkedQueue<>();

    private static class DictionaryFacilitatorLruCache extends
            LruCache<Locale, DictionaryFacilitator> {
        private final HashSet<Locale> mCachedLocales;
        public DictionaryFacilitatorLruCache(final HashSet<Locale> cachedLocales, int maxSize) {
            super(maxSize);
            mCachedLocales = cachedLocales;
        }

        @Override
        protected void entryRemoved(boolean evicted, Locale key,
                DictionaryFacilitator oldValue, DictionaryFacilitator newValue) {
            if (oldValue != null && oldValue != newValue) {
                oldValue.closeDictionaries();
            }
            if (key != null && newValue == null) {
                // Remove locale from the cache when the dictionary facilitator for the locale is
                // evicted and new facilitator is not set for the locale.
                mCachedLocales.remove(key);
                if (size() >= maxSize()) {
                    Log.w(TAG, "DictionaryFacilitator for " + key.toString()
                            + " has been evicted due to cache size limit."
                            + " size: " + size() + ", maxSize: " + maxSize());
                }
            }
        }
    }

    private static final int MAX_DICTIONARY_FACILITATOR_COUNT = 3;
    private final LruCache<Locale, DictionaryFacilitator> mDictionaryFacilitatorCache =
            new DictionaryFacilitatorLruCache(mCachedLocales, MAX_DICTIONARY_FACILITATOR_COUNT);
    private final ConcurrentHashMap<Locale, Keyboard> mKeyboardCache = new ConcurrentHashMap<>();

    // The threshold for a suggestion to be considered "recommended".
    private float mRecommendedThreshold;
    // Whether to use the contacts dictionary
    private boolean mUseContactsDictionary;
    // TODO: make a spell checker option to block offensive words or not
    private final SettingsValuesForSuggestion mSettingsValuesForSuggestion =
            new SettingsValuesForSuggestion(true /* blockPotentiallyOffensive */,
                    true /* spaceAwareGestureEnabled */,
                    null /* additionalFeaturesSettingValues */);
    private final Object mDictionaryLock = new Object();

    public static final String SINGLE_QUOTE = "\u0027";
    public static final String APOSTROPHE = "\u2019";

    public AndroidSpellCheckerService() {
        super();
        for (int i = 0; i < MAX_NUM_OF_THREADS_READ_DICTIONARY; i++) {
            mSessionIdPool.add(i);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        mRecommendedThreshold =
                Float.parseFloat(getString(R.string.spellchecker_recommended_threshold_value));
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, PREF_USE_CONTACTS_KEY);
    }

    public float getRecommendedThreshold() {
        return mRecommendedThreshold;
    }

    private static String getKeyboardLayoutNameForScript(final int script) {
        switch (script) {
        case ScriptUtils.SCRIPT_LATIN:
            return "qwerty";
        case ScriptUtils.SCRIPT_CYRILLIC:
            return "east_slavic";
        case ScriptUtils.SCRIPT_GREEK:
            return "greek";
        default:
            throw new RuntimeException("Wrong script supplied: " + script);
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (!PREF_USE_CONTACTS_KEY.equals(key)) return;
            final boolean useContactsDictionary = prefs.getBoolean(PREF_USE_CONTACTS_KEY, true);
            if (useContactsDictionary != mUseContactsDictionary) {
                mSemaphore.acquireUninterruptibly(MAX_NUM_OF_THREADS_READ_DICTIONARY);
                try {
                    mUseContactsDictionary = useContactsDictionary;
                    for (final Locale locale : mCachedLocales) {
                        final DictionaryFacilitator dictionaryFacilitator =
                                mDictionaryFacilitatorCache.get(locale);
                        resetDictionariesForLocale(this /* context  */,
                                dictionaryFacilitator, locale, mUseContactsDictionary);
                    }
                } finally {
                    mSemaphore.release(MAX_NUM_OF_THREADS_READ_DICTIONARY);
                }
            }
    }

    @Override
    public Session createSession() {
        // Should not refer to AndroidSpellCheckerSession directly considering
        // that AndroidSpellCheckerSession may be overlaid.
        return AndroidSpellCheckerSessionFactory.newInstance(this);
    }

    /**
     * Returns an empty SuggestionsInfo with flags signaling the word is not in the dictionary.
     * @param reportAsTypo whether this should include the flag LOOKS_LIKE_TYPO, for red underline.
     * @return the empty SuggestionsInfo with the appropriate flags set.
     */
    public static SuggestionsInfo getNotInDictEmptySuggestions(final boolean reportAsTypo) {
        return new SuggestionsInfo(reportAsTypo ? SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO : 0,
                EMPTY_STRING_ARRAY);
    }

    /**
     * Returns an empty suggestionInfo with flags signaling the word is in the dictionary.
     * @return the empty SuggestionsInfo with the appropriate flags set.
     */
    public static SuggestionsInfo getInDictEmptySuggestions() {
        return new SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY,
                EMPTY_STRING_ARRAY);
    }

    public boolean isValidWord(final Locale locale, final String word) {
        mSemaphore.acquireUninterruptibly();
        try {
            DictionaryFacilitator dictionaryFacilitatorForLocale =
                    getDictionaryFacilitatorForLocaleLocked(locale);
            return dictionaryFacilitatorForLocale.isValidWord(word, false /* igroreCase */);
        } finally {
            mSemaphore.release();
        }
    }

    public SuggestionResults getSuggestionResults(final Locale locale, final WordComposer composer,
            final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo) {
        Integer sessionId = null;
        mSemaphore.acquireUninterruptibly();
        try {
            sessionId = mSessionIdPool.poll();
            DictionaryFacilitator dictionaryFacilitatorForLocale =
                    getDictionaryFacilitatorForLocaleLocked(locale);
            return dictionaryFacilitatorForLocale.getSuggestionResults(composer, prevWordsInfo,
                    proximityInfo, mSettingsValuesForSuggestion, sessionId);
        } finally {
            if (sessionId != null) {
                mSessionIdPool.add(sessionId);
            }
            mSemaphore.release();
        }
    }

    public boolean hasMainDictionaryForLocale(final Locale locale) {
        mSemaphore.acquireUninterruptibly();
        try {
            final DictionaryFacilitator dictionaryFacilitator =
                    getDictionaryFacilitatorForLocaleLocked(locale);
            return dictionaryFacilitator.hasInitializedMainDictionary();
        } finally {
            mSemaphore.release();
        }
    }

    private DictionaryFacilitator getDictionaryFacilitatorForLocaleLocked(final Locale locale) {
        DictionaryFacilitator dictionaryFacilitatorForLocale =
                mDictionaryFacilitatorCache.get(locale);
        if (dictionaryFacilitatorForLocale == null) {
            dictionaryFacilitatorForLocale = new DictionaryFacilitator();
            mDictionaryFacilitatorCache.put(locale, dictionaryFacilitatorForLocale);
            mCachedLocales.add(locale);
            resetDictionariesForLocale(this /* context */, dictionaryFacilitatorForLocale,
                    locale, mUseContactsDictionary);
        }
        return dictionaryFacilitatorForLocale;
    }

    private static void resetDictionariesForLocale(final Context context,
            final DictionaryFacilitator dictionaryFacilitator, final Locale locale,
            final boolean useContactsDictionary) {
        dictionaryFacilitator.resetDictionariesWithDictNamePrefix(context, locale,
                useContactsDictionary, false /* usePersonalizedDicts */,
                false /* forceReloadMainDictionary */, null /* listener */,
                DICTIONARY_NAME_PREFIX);
        for (int i = 0; i < MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT; i++) {
            try {
                dictionaryFacilitator.waitForLoadingMainDictionary(
                        WAIT_FOR_LOADING_MAIN_DICT_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
                return;
            } catch (final InterruptedException e) {
                Log.i(TAG, "Interrupted during waiting for loading main dictionary.", e);
                if (i < MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT - 1) {
                    Log.i(TAG, "Retry", e);
                } else {
                    Log.w(TAG, "Give up retrying. Retried "
                            + MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT + " times.", e);
                }
            }
        }
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        mSemaphore.acquireUninterruptibly(MAX_NUM_OF_THREADS_READ_DICTIONARY);
        try {
            mDictionaryFacilitatorCache.evictAll();
            mCachedLocales.clear();
        } finally {
            mSemaphore.release(MAX_NUM_OF_THREADS_READ_DICTIONARY);
        }
        mKeyboardCache.clear();
        return false;
    }

    public Keyboard getKeyboardForLocale(final Locale locale) {
        Keyboard keyboard = mKeyboardCache.get(locale);
        if (keyboard == null) {
            keyboard = createKeyboardForLocale(locale);
            if (keyboard != null) {
                mKeyboardCache.put(locale, keyboard);
            }
        }
        return keyboard;
    }

    private Keyboard createKeyboardForLocale(final Locale locale) {
        final int script = ScriptUtils.getScriptFromSpellCheckerLocale(locale);
        final String keyboardLayoutName = getKeyboardLayoutNameForScript(script);
        final InputMethodSubtype subtype = AdditionalSubtypeUtils.createDummyAdditionalSubtype(
                locale.toString(), keyboardLayoutName);
        final KeyboardLayoutSet keyboardLayoutSet = createKeyboardSetForSpellChecker(subtype);
        return keyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET);
    }

    private KeyboardLayoutSet createKeyboardSetForSpellChecker(final InputMethodSubtype subtype) {
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT;
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(this, editorInfo);
        builder.setKeyboardGeometry(
                SPELLCHECKER_DUMMY_KEYBOARD_WIDTH, SPELLCHECKER_DUMMY_KEYBOARD_HEIGHT);
        builder.setSubtype(subtype);
        builder.setIsSpellChecker(true /* isSpellChecker */);
        builder.disableTouchPositionCorrectionData();
        return builder.build();
    }
}
