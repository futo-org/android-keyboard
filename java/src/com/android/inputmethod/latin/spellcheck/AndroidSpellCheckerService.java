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

package com.android.inputmethod.latin.spellcheck;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.preference.PreferenceManager;
import android.provider.UserDictionary.Words;
import android.service.textservice.SpellCheckerService;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

import com.android.inputmethod.compat.SuggestionsInfoCompatUtils;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.Dictionary.WordCallback;
import com.android.inputmethod.latin.DictionaryCollection;
import com.android.inputmethod.latin.DictionaryFactory;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LocaleUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StringUtils;
import com.android.inputmethod.latin.SynchronouslyLoadedContactsBinaryDictionary;
import com.android.inputmethod.latin.SynchronouslyLoadedContactsDictionary;
import com.android.inputmethod.latin.SynchronouslyLoadedUserBinaryDictionary;
import com.android.inputmethod.latin.SynchronouslyLoadedUserDictionary;
import com.android.inputmethod.latin.WhitelistDictionary;
import com.android.inputmethod.latin.WordComposer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service for spell checking, using LatinIME's dictionaries and mechanisms.
 */
public class AndroidSpellCheckerService extends SpellCheckerService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = AndroidSpellCheckerService.class.getSimpleName();
    private static final boolean DBG = false;
    private static final int POOL_SIZE = 2;

    public static final String PREF_USE_CONTACTS_KEY = "pref_spellcheck_use_contacts";

    private static final int CAPITALIZE_NONE = 0; // No caps, or mixed case
    private static final int CAPITALIZE_FIRST = 1; // First only
    private static final int CAPITALIZE_ALL = 2; // All caps

    private final static String[] EMPTY_STRING_ARRAY = new String[0];
    private Map<String, DictionaryPool> mDictionaryPools =
            Collections.synchronizedMap(new TreeMap<String, DictionaryPool>());
    private Map<String, Dictionary> mUserDictionaries =
            Collections.synchronizedMap(new TreeMap<String, Dictionary>());
    private Map<String, Dictionary> mWhitelistDictionaries =
            Collections.synchronizedMap(new TreeMap<String, Dictionary>());
    private Dictionary mContactsDictionary;

    // The threshold for a candidate to be offered as a suggestion.
    private float mSuggestionThreshold;
    // The threshold for a suggestion to be considered "recommended".
    private float mRecommendedThreshold;
    // Whether to use the contacts dictionary
    private boolean mUseContactsDictionary;
    private final Object mUseContactsLock = new Object();

    private final HashSet<WeakReference<DictionaryCollection>> mDictionaryCollectionsList =
            new HashSet<WeakReference<DictionaryCollection>>();

    public static final int SCRIPT_LATIN = 0;
    public static final int SCRIPT_CYRILLIC = 1;
    private static final String SINGLE_QUOTE = "\u0027";
    private static final String APOSTROPHE = "\u2019";
    private static final TreeMap<String, Integer> mLanguageToScript;
    static {
        // List of the supported languages and their associated script. We won't check
        // words written in another script than the selected script, because we know we
        // don't have those in our dictionary so we will underline everything and we
        // will never have any suggestions, so it makes no sense checking them, and this
        // is done in {@link #shouldFilterOut}. Also, the script is used to choose which
        // proximity to pass to the dictionary descent algorithm.
        // IMPORTANT: this only contains languages - do not write countries in there.
        // Only the language is searched from the map.
        mLanguageToScript = new TreeMap<String, Integer>();
        mLanguageToScript.put("en", SCRIPT_LATIN);
        mLanguageToScript.put("fr", SCRIPT_LATIN);
        mLanguageToScript.put("de", SCRIPT_LATIN);
        mLanguageToScript.put("nl", SCRIPT_LATIN);
        mLanguageToScript.put("cs", SCRIPT_LATIN);
        mLanguageToScript.put("es", SCRIPT_LATIN);
        mLanguageToScript.put("it", SCRIPT_LATIN);
        mLanguageToScript.put("hr", SCRIPT_LATIN);
        mLanguageToScript.put("pt", SCRIPT_LATIN);
        mLanguageToScript.put("ru", SCRIPT_CYRILLIC);
        // TODO: Make a persian proximity, and activate the Farsi subtype.
        // mLanguageToScript.put("fa", SCRIPT_PERSIAN);
    }

    @Override public void onCreate() {
        super.onCreate();
        mSuggestionThreshold =
                Float.parseFloat(getString(R.string.spellchecker_suggestion_threshold_value));
        mRecommendedThreshold =
                Float.parseFloat(getString(R.string.spellchecker_recommended_threshold_value));
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, PREF_USE_CONTACTS_KEY);
    }

    private static int getScriptFromLocale(final Locale locale) {
        final Integer script = mLanguageToScript.get(locale.getLanguage());
        if (null == script) {
            throw new RuntimeException("We have been called with an unsupported language: \""
                    + locale.getLanguage() + "\". Framework bug?");
        }
        return script;
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (!PREF_USE_CONTACTS_KEY.equals(key)) return;
        synchronized(mUseContactsLock) {
            mUseContactsDictionary = prefs.getBoolean(PREF_USE_CONTACTS_KEY, true);
            if (mUseContactsDictionary) {
                startUsingContactsDictionaryLocked();
            } else {
                stopUsingContactsDictionaryLocked();
            }
        }
    }

    private void startUsingContactsDictionaryLocked() {
        if (null == mContactsDictionary) {
            if (LatinIME.USE_BINARY_CONTACTS_DICTIONARY) {
                // TODO: use the right locale for each session
                mContactsDictionary =
                        new SynchronouslyLoadedContactsBinaryDictionary(this, Locale.getDefault());
            } else {
                mContactsDictionary = new SynchronouslyLoadedContactsDictionary(this);
            }
        }
        final Iterator<WeakReference<DictionaryCollection>> iterator =
                mDictionaryCollectionsList.iterator();
        while (iterator.hasNext()) {
            final WeakReference<DictionaryCollection> dictRef = iterator.next();
            final DictionaryCollection dict = dictRef.get();
            if (null == dict) {
                iterator.remove();
            } else {
                dict.addDictionary(mContactsDictionary);
            }
        }
    }

    private void stopUsingContactsDictionaryLocked() {
        if (null == mContactsDictionary) return;
        final Dictionary contactsDict = mContactsDictionary;
        // TODO: revert to the concrete type when USE_BINARY_CONTACTS_DICTIONARY is no longer needed
        mContactsDictionary = null;
        final Iterator<WeakReference<DictionaryCollection>> iterator =
                mDictionaryCollectionsList.iterator();
        while (iterator.hasNext()) {
            final WeakReference<DictionaryCollection> dictRef = iterator.next();
            final DictionaryCollection dict = dictRef.get();
            if (null == dict) {
                iterator.remove();
            } else {
                dict.removeDictionary(contactsDict);
            }
        }
        contactsDict.close();
    }

    @Override
    public Session createSession() {
        return new AndroidSpellCheckerSession(this);
    }

    private static SuggestionsInfo getNotInDictEmptySuggestions() {
        return new SuggestionsInfo(0, EMPTY_STRING_ARRAY);
    }

    private static SuggestionsInfo getInDictEmptySuggestions() {
        return new SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY,
                EMPTY_STRING_ARRAY);
    }

    private static class SuggestionsGatherer implements WordCallback {
        public static class Result {
            public final String[] mSuggestions;
            public final boolean mHasRecommendedSuggestions;
            public Result(final String[] gatheredSuggestions,
                    final boolean hasRecommendedSuggestions) {
                mSuggestions = gatheredSuggestions;
                mHasRecommendedSuggestions = hasRecommendedSuggestions;
            }
        }

        private final ArrayList<CharSequence> mSuggestions;
        private final int[] mScores;
        private final String mOriginalText;
        private final float mSuggestionThreshold;
        private final float mRecommendedThreshold;
        private final int mMaxLength;
        private int mLength = 0;

        // The two following attributes are only ever filled if the requested max length
        // is 0 (or less, which is treated the same).
        private String mBestSuggestion = null;
        private int mBestScore = Integer.MIN_VALUE; // As small as possible

        SuggestionsGatherer(final String originalText, final float suggestionThreshold,
                final float recommendedThreshold, final int maxLength) {
            mOriginalText = originalText;
            mSuggestionThreshold = suggestionThreshold;
            mRecommendedThreshold = recommendedThreshold;
            mMaxLength = maxLength;
            mSuggestions = new ArrayList<CharSequence>(maxLength + 1);
            mScores = new int[mMaxLength];
        }

        @Override
        synchronized public boolean addWord(char[] word, int wordOffset, int wordLength, int score,
                int dicTypeId, int dataType) {
            final int positionIndex = Arrays.binarySearch(mScores, 0, mLength, score);
            // binarySearch returns the index if the element exists, and -<insertion index> - 1
            // if it doesn't. See documentation for binarySearch.
            final int insertIndex = positionIndex >= 0 ? positionIndex : -positionIndex - 1;

            if (insertIndex == 0 && mLength >= mMaxLength) {
                // In the future, we may want to keep track of the best suggestion score even if
                // we are asked for 0 suggestions. In this case, we can use the following
                // (tested) code to keep it:
                // If the maxLength is 0 (should never be less, but if it is, it's treated as 0)
                // then we need to keep track of the best suggestion in mBestScore and
                // mBestSuggestion. This is so that we know whether the best suggestion makes
                // the score cutoff, since we need to know that to return a meaningful
                // looksLikeTypo.
                // if (0 >= mMaxLength) {
                //     if (score > mBestScore) {
                //         mBestScore = score;
                //         mBestSuggestion = new String(word, wordOffset, wordLength);
                //     }
                // }
                return true;
            }
            if (insertIndex >= mMaxLength) {
                // We found a suggestion, but its score is too weak to be kept considering
                // the suggestion limit.
                return true;
            }

            // Compute the normalized score and skip this word if it's normalized score does not
            // make the threshold.
            final String wordString = new String(word, wordOffset, wordLength);
            final float normalizedScore =
                    BinaryDictionary.calcNormalizedScore(mOriginalText, wordString, score);
            if (normalizedScore < mSuggestionThreshold) {
                if (DBG) Log.i(TAG, wordString + " does not make the score threshold");
                return true;
            }

            if (mLength < mMaxLength) {
                final int copyLen = mLength - insertIndex;
                ++mLength;
                System.arraycopy(mScores, insertIndex, mScores, insertIndex + 1, copyLen);
                mSuggestions.add(insertIndex, wordString);
            } else {
                System.arraycopy(mScores, 1, mScores, 0, insertIndex);
                mSuggestions.add(insertIndex, wordString);
                mSuggestions.remove(0);
            }
            mScores[insertIndex] = score;

            return true;
        }

        public Result getResults(final int capitalizeType, final Locale locale) {
            final String[] gatheredSuggestions;
            final boolean hasRecommendedSuggestions;
            if (0 == mLength) {
                // Either we found no suggestions, or we found some BUT the max length was 0.
                // If we found some mBestSuggestion will not be null. If it is null, then
                // we found none, regardless of the max length.
                if (null == mBestSuggestion) {
                    gatheredSuggestions = null;
                    hasRecommendedSuggestions = false;
                } else {
                    gatheredSuggestions = EMPTY_STRING_ARRAY;
                    final float normalizedScore = BinaryDictionary.calcNormalizedScore(
                            mOriginalText, mBestSuggestion, mBestScore);
                    hasRecommendedSuggestions = (normalizedScore > mRecommendedThreshold);
                }
            } else {
                if (DBG) {
                    if (mLength != mSuggestions.size()) {
                        Log.e(TAG, "Suggestion size is not the same as stored mLength");
                    }
                    for (int i = mLength - 1; i >= 0; --i) {
                        Log.i(TAG, "" + mScores[i] + " " + mSuggestions.get(i));
                    }
                }
                Collections.reverse(mSuggestions);
                StringUtils.removeDupes(mSuggestions);
                if (CAPITALIZE_ALL == capitalizeType) {
                    for (int i = 0; i < mSuggestions.size(); ++i) {
                        // get(i) returns a CharSequence which is actually a String so .toString()
                        // should return the same object.
                        mSuggestions.set(i, mSuggestions.get(i).toString().toUpperCase(locale));
                    }
                } else if (CAPITALIZE_FIRST == capitalizeType) {
                    for (int i = 0; i < mSuggestions.size(); ++i) {
                        // Likewise
                        mSuggestions.set(i, StringUtils.toTitleCase(
                                mSuggestions.get(i).toString(), locale));
                    }
                }
                // This returns a String[], while toArray() returns an Object[] which cannot be cast
                // into a String[].
                gatheredSuggestions = mSuggestions.toArray(EMPTY_STRING_ARRAY);

                final int bestScore = mScores[mLength - 1];
                final CharSequence bestSuggestion = mSuggestions.get(0);
                final float normalizedScore =
                        BinaryDictionary.calcNormalizedScore(
                                mOriginalText, bestSuggestion.toString(), bestScore);
                hasRecommendedSuggestions = (normalizedScore > mRecommendedThreshold);
                if (DBG) {
                    Log.i(TAG, "Best suggestion : " + bestSuggestion + ", score " + bestScore);
                    Log.i(TAG, "Normalized score = " + normalizedScore
                            + " (threshold " + mRecommendedThreshold
                            + ") => hasRecommendedSuggestions = " + hasRecommendedSuggestions);
                }
            }
            return new Result(gatheredSuggestions, hasRecommendedSuggestions);
        }
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        closeAllDictionaries();
        return false;
    }

    private void closeAllDictionaries() {
        final Map<String, DictionaryPool> oldPools = mDictionaryPools;
        mDictionaryPools = Collections.synchronizedMap(new TreeMap<String, DictionaryPool>());
        final Map<String, Dictionary> oldUserDictionaries = mUserDictionaries;
        mUserDictionaries = Collections.synchronizedMap(new TreeMap<String, Dictionary>());
        final Map<String, Dictionary> oldWhitelistDictionaries = mWhitelistDictionaries;
        mWhitelistDictionaries = Collections.synchronizedMap(new TreeMap<String, Dictionary>());
        new Thread("spellchecker_close_dicts") {
            @Override
            public void run() {
                for (DictionaryPool pool : oldPools.values()) {
                    pool.close();
                }
                for (Dictionary dict : oldUserDictionaries.values()) {
                    dict.close();
                }
                for (Dictionary dict : oldWhitelistDictionaries.values()) {
                    dict.close();
                }
                synchronized (mUseContactsLock) {
                    if (null != mContactsDictionary) {
                        // The synchronously loaded contacts dictionary should have been in one
                        // or several pools, but it is shielded against multiple closing and it's
                        // safe to call it several times.
                        final Dictionary dictToClose = mContactsDictionary;
                        // TODO: revert to the concrete type when USE_BINARY_CONTACTS_DICTIONARY
                        // is no longer needed
                        mContactsDictionary = null;
                        dictToClose.close();
                    }
                }
            }
        }.start();
    }

    private DictionaryPool getDictionaryPool(final String locale) {
        DictionaryPool pool = mDictionaryPools.get(locale);
        if (null == pool) {
            final Locale localeObject = LocaleUtils.constructLocaleFromString(locale);
            pool = new DictionaryPool(POOL_SIZE, this, localeObject);
            mDictionaryPools.put(locale, pool);
        }
        return pool;
    }

    public DictAndProximity createDictAndProximity(final Locale locale) {
        final int script = getScriptFromLocale(locale);
        final ProximityInfo proximityInfo = ProximityInfo.createSpellCheckerProximityInfo(
                SpellCheckerProximityInfo.getProximityForScript(script),
                SpellCheckerProximityInfo.ROW_SIZE,
                SpellCheckerProximityInfo.PROXIMITY_GRID_WIDTH,
                SpellCheckerProximityInfo.PROXIMITY_GRID_HEIGHT);
        final DictionaryCollection dictionaryCollection =
                DictionaryFactory.createMainDictionaryFromManager(this, locale,
                        true /* useFullEditDistance */);
        final String localeStr = locale.toString();
        Dictionary userDictionary = mUserDictionaries.get(localeStr);
        if (null == userDictionary) {
            if (LatinIME.USE_BINARY_USER_DICTIONARY) {
                userDictionary = new SynchronouslyLoadedUserBinaryDictionary(this, localeStr, true);
            } else {
                userDictionary = new SynchronouslyLoadedUserDictionary(this, localeStr, true);
            }
            mUserDictionaries.put(localeStr, userDictionary);
        }
        dictionaryCollection.addDictionary(userDictionary);
        Dictionary whitelistDictionary = mWhitelistDictionaries.get(localeStr);
        if (null == whitelistDictionary) {
            whitelistDictionary = new WhitelistDictionary(this, locale);
            mWhitelistDictionaries.put(localeStr, whitelistDictionary);
        }
        dictionaryCollection.addDictionary(whitelistDictionary);
        synchronized (mUseContactsLock) {
            if (mUseContactsDictionary) {
                if (null == mContactsDictionary) {
                    // TODO: revert to the concrete type when USE_BINARY_CONTACTS_DICTIONARY is no
                    // longer needed
                    if (LatinIME.USE_BINARY_CONTACTS_DICTIONARY) {
                        // TODO: use the right locale. We can't do it right now because the
                        // spell checker is reusing the contacts dictionary across sessions
                        // without regard for their locale, so we need to fix that first.
                        mContactsDictionary = new SynchronouslyLoadedContactsBinaryDictionary(this,
                                Locale.getDefault());
                    } else {
                        mContactsDictionary = new SynchronouslyLoadedContactsDictionary(this);
                    }
                }
            }
            dictionaryCollection.addDictionary(mContactsDictionary);
            mDictionaryCollectionsList.add(
                    new WeakReference<DictionaryCollection>(dictionaryCollection));
        }
        return new DictAndProximity(dictionaryCollection, proximityInfo);
    }

    // This method assumes the text is not empty or null.
    private static int getCapitalizationType(String text) {
        // If the first char is not uppercase, then the word is either all lower case,
        // and in either case we return CAPITALIZE_NONE.
        if (!Character.isUpperCase(text.codePointAt(0))) return CAPITALIZE_NONE;
        final int len = text.length();
        int capsCount = 1;
        for (int i = 1; i < len; i = text.offsetByCodePoints(i, 1)) {
            if (1 != capsCount && i != capsCount) break;
            if (Character.isUpperCase(text.codePointAt(i))) ++capsCount;
        }
        // We know the first char is upper case. So we want to test if either everything
        // else is lower case, or if everything else is upper case. If the string is
        // exactly one char long, then we will arrive here with capsCount 1, and this is
        // correct, too.
        if (1 == capsCount) return CAPITALIZE_FIRST;
        return (len == capsCount ? CAPITALIZE_ALL : CAPITALIZE_NONE);
    }

    private static class AndroidSpellCheckerSession extends Session {
        // Immutable, but need the locale which is not available in the constructor yet
        private DictionaryPool mDictionaryPool;
        // Likewise
        private Locale mLocale;
        // Cache this for performance
        private int mScript; // One of SCRIPT_LATIN or SCRIPT_CYRILLIC for now.

        private final AndroidSpellCheckerService mService;

        private final SuggestionsCache mSuggestionsCache = new SuggestionsCache();
        private final ContentObserver mObserver;

        private static class SuggestionsParams {
            public final String[] mSuggestions;
            public final int mFlags;
            public SuggestionsParams(String[] suggestions, int flags) {
                mSuggestions = suggestions;
                mFlags = flags;
            }
        }

        private static class SuggestionsCache {
            private static final int MAX_CACHE_SIZE = 50;
            // TODO: support bigram
            private final LruCache<String, SuggestionsParams> mUnigramSuggestionsInfoCache =
                    new LruCache<String, SuggestionsParams>(MAX_CACHE_SIZE);

            public SuggestionsParams getSuggestionsFromCache(String query) {
                return mUnigramSuggestionsInfoCache.get(query);
            }

            public void putSuggestionsToCache(String query, String[] suggestions, int flags) {
                if (suggestions == null || TextUtils.isEmpty(query)) {
                    return;
                }
                mUnigramSuggestionsInfoCache.put(query, new SuggestionsParams(suggestions, flags));
            }

            public void clearCache() {
                mUnigramSuggestionsInfoCache.evictAll();
            }
        }

        AndroidSpellCheckerSession(final AndroidSpellCheckerService service) {
            mService = service;
            final ContentResolver cres = service.getContentResolver();

            mObserver = new ContentObserver(null) {
                @Override
                public void onChange(boolean self) {
                    mSuggestionsCache.clearCache();
                }
            };
            cres.registerContentObserver(Words.CONTENT_URI, true, mObserver);
        }

        @Override
        public void onCreate() {
            final String localeString = getLocale();
            mDictionaryPool = mService.getDictionaryPool(localeString);
            mLocale = LocaleUtils.constructLocaleFromString(localeString);
            mScript = getScriptFromLocale(mLocale);
        }

        @Override
        public void onClose() {
            final ContentResolver cres = mService.getContentResolver();
            cres.unregisterContentObserver(mObserver);
        }

        /*
         * Returns whether the code point is a letter that makes sense for the specified
         * locale for this spell checker.
         * The dictionaries supported by Latin IME are described in res/xml/spellchecker.xml
         * and is limited to EFIGS languages and Russian.
         * Hence at the moment this explicitly tests for Cyrillic characters or Latin characters
         * as appropriate, and explicitly excludes CJK, Arabic and Hebrew characters.
         */
        private static boolean isLetterCheckableByLanguage(final int codePoint,
                final int script) {
            switch (script) {
            case SCRIPT_LATIN:
                // Our supported latin script dictionaries (EFIGS) at the moment only include
                // characters in the C0, C1, Latin Extended A and B, IPA extensions unicode
                // blocks. As it happens, those are back-to-back in the code range 0x40 to 0x2AF,
                // so the below is a very efficient way to test for it. As for the 0-0x3F, it's
                // excluded from isLetter anyway.
                return codePoint <= 0x2AF && Character.isLetter(codePoint);
            case SCRIPT_CYRILLIC:
                // All Cyrillic characters are in the 400~52F block. There are some in the upper
                // Unicode range, but they are archaic characters that are not used in modern
                // russian and are not used by our dictionary.
                return codePoint >= 0x400 && codePoint <= 0x52F && Character.isLetter(codePoint);
            default:
                // Should never come here
                throw new RuntimeException("Impossible value of script: " + script);
            }
        }

        /**
         * Finds out whether a particular string should be filtered out of spell checking.
         *
         * This will loosely match URLs, numbers, symbols. To avoid always underlining words that
         * we know we will never recognize, this accepts a script identifier that should be one
         * of the SCRIPT_* constants defined above, to rule out quickly characters from very
         * different languages.
         *
         * @param text the string to evaluate.
         * @param script the identifier for the script this spell checker recognizes
         * @return true if we should filter this text out, false otherwise
         */
        private static boolean shouldFilterOut(final String text, final int script) {
            if (TextUtils.isEmpty(text) || text.length() <= 1) return true;

            // TODO: check if an equivalent processing can't be done more quickly with a
            // compiled regexp.
            // Filter by first letter
            final int firstCodePoint = text.codePointAt(0);
            // Filter out words that don't start with a letter or an apostrophe
            if (!isLetterCheckableByLanguage(firstCodePoint, script)
                    && '\'' != firstCodePoint) return true;

            // Filter contents
            final int length = text.length();
            int letterCount = 0;
            for (int i = 0; i < length; i = text.offsetByCodePoints(i, 1)) {
                final int codePoint = text.codePointAt(i);
                // Any word containing a '@' is probably an e-mail address
                // Any word containing a '/' is probably either an ad-hoc combination of two
                // words or a URI - in either case we don't want to spell check that
                if ('@' == codePoint || '/' == codePoint) return true;
                if (isLetterCheckableByLanguage(codePoint, script)) ++letterCount;
            }
            // Guestimate heuristic: perform spell checking if at least 3/4 of the characters
            // in this word are letters
            return (letterCount * 4 < length * 3);
        }

        private SentenceSuggestionsInfo fixWronglyInvalidatedWordWithSingleQuote(
                TextInfo ti, SentenceSuggestionsInfo ssi) {
            final String typedText = ti.getText();
            if (!typedText.contains(SINGLE_QUOTE)) {
                return null;
            }
            final int N = ssi.getSuggestionsCount();
            final ArrayList<Integer> additionalOffsets = new ArrayList<Integer>();
            final ArrayList<Integer> additionalLengths = new ArrayList<Integer>();
            final ArrayList<SuggestionsInfo> additionalSuggestionsInfos =
                    new ArrayList<SuggestionsInfo>();
            for (int i = 0; i < N; ++i) {
                final SuggestionsInfo si = ssi.getSuggestionsInfoAt(i);
                final int flags = si.getSuggestionsAttributes();
                if ((flags & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) == 0) {
                    continue;
                }
                final int offset = ssi.getOffsetAt(i);
                final int length = ssi.getLengthAt(i);
                final String subText = typedText.substring(offset, offset + length);
                if (!subText.contains(SINGLE_QUOTE)) {
                    continue;
                }
                final String[] splitTexts = subText.split(SINGLE_QUOTE, -1);
                if (splitTexts == null || splitTexts.length <= 1) {
                    continue;
                }
                final int splitNum = splitTexts.length;
                for (int j = 0; j < splitNum; ++j) {
                    final String splitText = splitTexts[j];
                    if (TextUtils.isEmpty(splitText)) {
                        continue;
                    }
                    if (mSuggestionsCache.getSuggestionsFromCache(splitText) == null) {
                        continue;
                    }
                    final int newLength = splitText.length();
                    // Neither RESULT_ATTR_IN_THE_DICTIONARY nor RESULT_ATTR_LOOKS_LIKE_TYPO
                    final int newFlags = 0;
                    final SuggestionsInfo newSi = new SuggestionsInfo(newFlags, EMPTY_STRING_ARRAY);
                    newSi.setCookieAndSequence(si.getCookie(), si.getSequence());
                    if (DBG) {
                        Log.d(TAG, "Override and remove old span over: "
                                + splitText + ", " + offset + "," + newLength);
                    }
                    additionalOffsets.add(offset);
                    additionalLengths.add(newLength);
                    additionalSuggestionsInfos.add(newSi);
                }
            }
            final int additionalSize = additionalOffsets.size();
            if (additionalSize <= 0) {
                return null;
            }
            final int suggestionsSize = N + additionalSize;
            final int[] newOffsets = new int[suggestionsSize];
            final int[] newLengths = new int[suggestionsSize];
            final SuggestionsInfo[] newSuggestionsInfos = new SuggestionsInfo[suggestionsSize];
            int i;
            for (i = 0; i < N; ++i) {
                newOffsets[i] = ssi.getOffsetAt(i);
                newLengths[i] = ssi.getLengthAt(i);
                newSuggestionsInfos[i] = ssi.getSuggestionsInfoAt(i);
            }
            for (; i < suggestionsSize; ++i) {
                newOffsets[i] = additionalOffsets.get(i - N);
                newLengths[i] = additionalLengths.get(i - N);
                newSuggestionsInfos[i] = additionalSuggestionsInfos.get(i - N);
            }
            return new SentenceSuggestionsInfo(newSuggestionsInfos, newOffsets, newLengths);
        }

        @Override
        public SentenceSuggestionsInfo[] onGetSentenceSuggestionsMultiple(
                TextInfo[] textInfos, int suggestionsLimit) {
            final SentenceSuggestionsInfo[] retval = super.onGetSentenceSuggestionsMultiple(
                    textInfos, suggestionsLimit);
            if (retval == null || retval.length != textInfos.length) {
                return retval;
            }
            for (int i = 0; i < retval.length; ++i) {
                final SentenceSuggestionsInfo tempSsi =
                        fixWronglyInvalidatedWordWithSingleQuote(textInfos[i], retval[i]);
                if (tempSsi != null) {
                    retval[i] = tempSsi;
                }
            }
            return retval;
        }

        @Override
        public SuggestionsInfo[] onGetSuggestionsMultiple(TextInfo[] textInfos,
                int suggestionsLimit, boolean sequentialWords) {
            final int length = textInfos.length;
            final SuggestionsInfo[] retval = new SuggestionsInfo[length];
            for (int i = 0; i < length; ++i) {
                final String prevWord;
                if (sequentialWords && i > 0) {
                    final String prevWordCandidate = textInfos[i - 1].getText();
                    // Note that an empty string would be used to indicate the initial word
                    // in the future.
                    prevWord = TextUtils.isEmpty(prevWordCandidate) ? null : prevWordCandidate;
                } else {
                    prevWord = null;
                }
                retval[i] = onGetSuggestions(textInfos[i], prevWord, suggestionsLimit);
                retval[i].setCookieAndSequence(
                        textInfos[i].getCookie(), textInfos[i].getSequence());
            }
            return retval;
        }

        // Note : this must be reentrant
        /**
         * Gets a list of suggestions for a specific string. This returns a list of possible
         * corrections for the text passed as an argument. It may split or group words, and
         * even perform grammatical analysis.
         */
        @Override
        public SuggestionsInfo onGetSuggestions(final TextInfo textInfo,
                final int suggestionsLimit) {
            return onGetSuggestions(textInfo, null, suggestionsLimit);
        }

        private SuggestionsInfo onGetSuggestions(
                final TextInfo textInfo, final String prevWord, final int suggestionsLimit) {
            try {
                final String inText = textInfo.getText();
                final SuggestionsParams cachedSuggestionsParams =
                        mSuggestionsCache.getSuggestionsFromCache(inText);
                if (cachedSuggestionsParams != null) {
                    if (DBG) {
                        Log.d(TAG, "Cache hit: " + inText + ", " + cachedSuggestionsParams.mFlags);
                    }
                    return new SuggestionsInfo(
                            cachedSuggestionsParams.mFlags, cachedSuggestionsParams.mSuggestions);
                }

                if (shouldFilterOut(inText, mScript)) {
                    DictAndProximity dictInfo = null;
                    try {
                        dictInfo = mDictionaryPool.takeOrGetNull();
                        if (null == dictInfo) return getNotInDictEmptySuggestions();
                        return dictInfo.mDictionary.isValidWord(inText) ?
                                getInDictEmptySuggestions() : getNotInDictEmptySuggestions();
                    } finally {
                        if (null != dictInfo) {
                            if (!mDictionaryPool.offer(dictInfo)) {
                                Log.e(TAG, "Can't re-insert a dictionary into its pool");
                            }
                        }
                    }
                }
                final String text = inText.replaceAll(APOSTROPHE, SINGLE_QUOTE);

                // TODO: Don't gather suggestions if the limit is <= 0 unless necessary
                final SuggestionsGatherer suggestionsGatherer = new SuggestionsGatherer(text,
                        mService.mSuggestionThreshold, mService.mRecommendedThreshold,
                        suggestionsLimit);
                final WordComposer composer = new WordComposer();
                final int length = text.length();
                for (int i = 0; i < length; i = text.offsetByCodePoints(i, 1)) {
                    final int codePoint = text.codePointAt(i);
                    // The getXYForCodePointAndScript method returns (Y << 16) + X
                    final int xy = SpellCheckerProximityInfo.getXYForCodePointAndScript(
                            codePoint, mScript);
                    if (SpellCheckerProximityInfo.NOT_A_COORDINATE_PAIR == xy) {
                        composer.add(codePoint, WordComposer.NOT_A_COORDINATE,
                                WordComposer.NOT_A_COORDINATE, null);
                    } else {
                        composer.add(codePoint, xy & 0xFFFF, xy >> 16, null);
                    }
                }

                final int capitalizeType = getCapitalizationType(text);
                boolean isInDict = true;
                DictAndProximity dictInfo = null;
                try {
                    dictInfo = mDictionaryPool.takeOrGetNull();
                    if (null == dictInfo) return getNotInDictEmptySuggestions();
                    dictInfo.mDictionary.getWords(composer, prevWord, suggestionsGatherer,
                            dictInfo.mProximityInfo);
                    isInDict = dictInfo.mDictionary.isValidWord(text);
                    if (!isInDict && CAPITALIZE_NONE != capitalizeType) {
                        // We want to test the word again if it's all caps or first caps only.
                        // If it's fully down, we already tested it, if it's mixed case, we don't
                        // want to test a lowercase version of it.
                        isInDict = dictInfo.mDictionary.isValidWord(text.toLowerCase(mLocale));
                    }
                } finally {
                    if (null != dictInfo) {
                        if (!mDictionaryPool.offer(dictInfo)) {
                            Log.e(TAG, "Can't re-insert a dictionary into its pool");
                        }
                    }
                }

                final SuggestionsGatherer.Result result = suggestionsGatherer.getResults(
                        capitalizeType, mLocale);

                if (DBG) {
                    Log.i(TAG, "Spell checking results for " + text + " with suggestion limit "
                            + suggestionsLimit);
                    Log.i(TAG, "IsInDict = " + isInDict);
                    Log.i(TAG, "LooksLikeTypo = " + (!isInDict));
                    Log.i(TAG, "HasRecommendedSuggestions = " + result.mHasRecommendedSuggestions);
                    if (null != result.mSuggestions) {
                        for (String suggestion : result.mSuggestions) {
                            Log.i(TAG, suggestion);
                        }
                    }
                }

                final int flags =
                        (isInDict ? SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY
                                : SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO)
                        | (result.mHasRecommendedSuggestions
                                ? SuggestionsInfoCompatUtils
                                        .getValueOf_RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS()
                                : 0);
                final SuggestionsInfo retval = new SuggestionsInfo(flags, result.mSuggestions);
                mSuggestionsCache.putSuggestionsToCache(text, result.mSuggestions, flags);
                return retval;
            } catch (RuntimeException e) {
                // Don't kill the keyboard if there is a bug in the spell checker
                if (DBG) {
                    throw e;
                } else {
                    Log.e(TAG, "Exception while spellcheking: " + e);
                    return getNotInDictEmptySuggestions();
                }
            }
        }
    }
}
