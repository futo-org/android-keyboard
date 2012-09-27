/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.database.ContentObserver;
import android.provider.UserDictionary.Words;
import android.service.textservice.SpellCheckerService.Session;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

import com.android.inputmethod.compat.SuggestionsInfoCompatUtils;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LocaleUtils;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.spellcheck.AndroidSpellCheckerService.SuggestionsGatherer;

import java.util.ArrayList;
import java.util.Locale;

public abstract class AndroidWordLevelSpellCheckerSession extends Session {
    private static final String TAG = AndroidWordLevelSpellCheckerSession.class.getSimpleName();
    private static final boolean DBG = false;

    // Immutable, but need the locale which is not available in the constructor yet
    private DictionaryPool mDictionaryPool;
    // Likewise
    private Locale mLocale;
    // Cache this for performance
    private int mScript; // One of SCRIPT_LATIN or SCRIPT_CYRILLIC for now.
    private final AndroidSpellCheckerService mService;
    protected final SuggestionsCache mSuggestionsCache = new SuggestionsCache();
    private final ContentObserver mObserver;

    private static final class SuggestionsParams {
        public final String[] mSuggestions;
        public final int mFlags;
        public SuggestionsParams(String[] suggestions, int flags) {
            mSuggestions = suggestions;
            mFlags = flags;
        }
    }

    protected static final class SuggestionsCache {
        private static final char CHAR_DELIMITER = '\uFFFC';
        private static final int MAX_CACHE_SIZE = 50;
        private final LruCache<String, SuggestionsParams> mUnigramSuggestionsInfoCache =
                new LruCache<String, SuggestionsParams>(MAX_CACHE_SIZE);

        // TODO: Support n-gram input
        private static String generateKey(String query, String prevWord) {
            if (TextUtils.isEmpty(query) || TextUtils.isEmpty(prevWord)) {
                return query;
            }
            return query + CHAR_DELIMITER + prevWord;
        }

        // TODO: Support n-gram input
        public SuggestionsParams getSuggestionsFromCache(String query, String prevWord) {
            return mUnigramSuggestionsInfoCache.get(generateKey(query, prevWord));
        }

        // TODO: Support n-gram input
        public void putSuggestionsToCache(
                String query, String prevWord, String[] suggestions, int flags) {
            if (suggestions == null || TextUtils.isEmpty(query)) {
                return;
            }
            mUnigramSuggestionsInfoCache.put(
                    generateKey(query, prevWord), new SuggestionsParams(suggestions, flags));
        }

        public void clearCache() {
            mUnigramSuggestionsInfoCache.evictAll();
        }
    }

    AndroidWordLevelSpellCheckerSession(final AndroidSpellCheckerService service) {
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
        mScript = AndroidSpellCheckerService.getScriptFromLocale(mLocale);
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
        case AndroidSpellCheckerService.SCRIPT_LATIN:
            // Our supported latin script dictionaries (EFIGS) at the moment only include
            // characters in the C0, C1, Latin Extended A and B, IPA extensions unicode
            // blocks. As it happens, those are back-to-back in the code range 0x40 to 0x2AF,
            // so the below is a very efficient way to test for it. As for the 0-0x3F, it's
            // excluded from isLetter anyway.
            return codePoint <= 0x2AF && Character.isLetter(codePoint);
        case AndroidSpellCheckerService.SCRIPT_CYRILLIC:
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

    protected SuggestionsInfo onGetSuggestions(
            final TextInfo textInfo, final String prevWord, final int suggestionsLimit) {
        try {
            final String inText = textInfo.getText();
            final SuggestionsParams cachedSuggestionsParams =
                    mSuggestionsCache.getSuggestionsFromCache(inText, prevWord);
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
                    dictInfo = mDictionaryPool.pollWithDefaultTimeout();
                    if (!DictionaryPool.isAValidDictionary(dictInfo)) {
                        return AndroidSpellCheckerService.getNotInDictEmptySuggestions();
                    }
                    return dictInfo.mDictionary.isValidWord(inText)
                            ? AndroidSpellCheckerService.getInDictEmptySuggestions()
                            : AndroidSpellCheckerService.getNotInDictEmptySuggestions();
                } finally {
                    if (null != dictInfo) {
                        if (!mDictionaryPool.offer(dictInfo)) {
                            Log.e(TAG, "Can't re-insert a dictionary into its pool");
                        }
                    }
                }
            }
            final String text = inText.replaceAll(
                    AndroidSpellCheckerService.APOSTROPHE, AndroidSpellCheckerService.SINGLE_QUOTE);

            // TODO: Don't gather suggestions if the limit is <= 0 unless necessary
            //final SuggestionsGatherer suggestionsGatherer = new SuggestionsGatherer(text,
            //mService.mSuggestionThreshold, mService.mRecommendedThreshold,
            //suggestionsLimit);
            final SuggestionsGatherer suggestionsGatherer = mService.newSuggestionsGatherer(
                    text, suggestionsLimit);
            final WordComposer composer = new WordComposer();
            final int length = text.length();
            for (int i = 0; i < length; i = text.offsetByCodePoints(i, 1)) {
                final int codePoint = text.codePointAt(i);
                // The getXYForCodePointAndScript method returns (Y << 16) + X
                final int xy = SpellCheckerProximityInfo.getXYForCodePointAndScript(
                        codePoint, mScript);
                if (SpellCheckerProximityInfo.NOT_A_COORDINATE_PAIR == xy) {
                    composer.add(codePoint,
                            Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
                } else {
                    composer.add(codePoint, xy & 0xFFFF, xy >> 16);
                }
            }

            final int capitalizeType = AndroidSpellCheckerService.getCapitalizationType(text);
            boolean isInDict = true;
            DictAndProximity dictInfo = null;
            try {
                dictInfo = mDictionaryPool.pollWithDefaultTimeout();
                if (!DictionaryPool.isAValidDictionary(dictInfo)) {
                    return AndroidSpellCheckerService.getNotInDictEmptySuggestions();
                }
                final ArrayList<SuggestedWordInfo> suggestions =
                        dictInfo.mDictionary.getSuggestions(composer, prevWord,
                                dictInfo.mProximityInfo);
                for (final SuggestedWordInfo suggestion : suggestions) {
                    final String suggestionStr = suggestion.mWord.toString();
                    suggestionsGatherer.addWord(suggestionStr.toCharArray(), null, 0,
                            suggestionStr.length(), suggestion.mScore);
                }
                isInDict = dictInfo.mDictionary.isValidWord(text);
                if (!isInDict && AndroidSpellCheckerService.CAPITALIZE_NONE != capitalizeType) {
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
            mSuggestionsCache.putSuggestionsToCache(text, prevWord, result.mSuggestions, flags);
            return retval;
        } catch (RuntimeException e) {
            // Don't kill the keyboard if there is a bug in the spell checker
            if (DBG) {
                throw e;
            } else {
                Log.e(TAG, "Exception while spellcheking: " + e);
                return AndroidSpellCheckerService.getNotInDictEmptySuggestions();
            }
        }
    }
}
