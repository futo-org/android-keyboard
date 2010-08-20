/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.UserBigramDictionary;
import com.android.inputmethod.latin.WordComposer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public class SuggestHelper {
    private Suggest mSuggest;
    private UserBigramDictionary mUserBigram;
    private final String TAG;

    /** Uses main dictionary only **/
    public SuggestHelper(String tag, Context context, int[] resId) {
        TAG = tag;
        InputStream[] is = null;
        try {
            // merging separated dictionary into one if dictionary is separated
            int total = 0;
            is = new InputStream[resId.length];
            for (int i = 0; i < resId.length; i++) {
                is[i] = context.getResources().openRawResource(resId[i]);
                total += is[i].available();
            }

            ByteBuffer byteBuffer =
                ByteBuffer.allocateDirect(total).order(ByteOrder.nativeOrder());
            int got = 0;
            for (int i = 0; i < resId.length; i++) {
                 got += Channels.newChannel(is[i]).read(byteBuffer);
            }
            if (got != total) {
                Log.w(TAG, "Read " + got + " bytes, expected " + total);
            } else {
                mSuggest = new Suggest(context, byteBuffer);
                Log.i(TAG, "Created mSuggest " + total + " bytes");
            }
        } catch (IOException e) {
            Log.w(TAG, "No available memory for binary dictionary");
        } finally {
            try {
                if (is != null) {
                    for (int i = 0; i < is.length; i++) {
                        is[i].close();
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to close input stream");
            }
        }
        mSuggest.setAutoTextEnabled(false);
        mSuggest.setCorrectionMode(Suggest.CORRECTION_FULL_BIGRAM);
    }

    /** Uses both main dictionary and user-bigram dictionary **/
    public SuggestHelper(String tag, Context context, int[] resId, int userBigramMax,
            int userBigramDelete) {
        this(tag, context, resId);
        mUserBigram = new UserBigramDictionary(context, null, Locale.US.toString(),
                Suggest.DIC_USER);
        mUserBigram.setDatabaseMax(userBigramMax);
        mUserBigram.setDatabaseDelete(userBigramDelete);
        mSuggest.setUserBigramDictionary(mUserBigram);
    }

    void changeUserBigramLocale(Context context, Locale locale) {
        if (mUserBigram != null) {
            flushUserBigrams();
            mUserBigram.close();
            mUserBigram = new UserBigramDictionary(context, null, locale.toString(),
                    Suggest.DIC_USER);
            mSuggest.setUserBigramDictionary(mUserBigram);
        }
    }

    private WordComposer createWordComposer(CharSequence s) {
        WordComposer word = new WordComposer();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            int[] codes;
            // If it's not a lowercase letter, don't find adjacent letters
            if (c < 'a' || c > 'z') {
                codes = new int[] { c };
            } else {
                codes = adjacents[c - 'a'];
            }
            word.add(c, codes);
        }
        return word;
    }

    private void showList(String title, List<CharSequence> suggestions) {
        Log.i(TAG, title);
        for (int i = 0; i < suggestions.size(); i++) {
            Log.i(title, suggestions.get(i) + ", ");
        }
    }

    private boolean isDefaultSuggestion(List<CharSequence> suggestions, CharSequence word) {
        // Check if either the word is what you typed or the first alternative
        return suggestions.size() > 0 &&
                (/*TextUtils.equals(suggestions.get(0), word) || */
                  (suggestions.size() > 1 && TextUtils.equals(suggestions.get(1), word)));
    }

    boolean isDefaultSuggestion(CharSequence typed, CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, null);
        return isDefaultSuggestion(suggestions, expected);
    }

    boolean isDefaultCorrection(CharSequence typed, CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, null);
        return isDefaultSuggestion(suggestions, expected) && mSuggest.hasMinimalCorrection();
    }

    boolean isASuggestion(CharSequence typed, CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, null);
        for (int i = 1; i < suggestions.size(); i++) {
            if (TextUtils.equals(suggestions.get(i), expected)) return true;
        }
        return false;
    }

    private void getBigramSuggestions(CharSequence previous, CharSequence typed) {
        if (!TextUtils.isEmpty(previous) && (typed.length() > 1)) {
            WordComposer firstChar = createWordComposer(Character.toString(typed.charAt(0)));
            mSuggest.getSuggestions(null, firstChar, false, previous);
        }
    }

    boolean isDefaultNextSuggestion(CharSequence previous, CharSequence typed,
            CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, previous);
        return isDefaultSuggestion(suggestions, expected);
    }

    boolean isDefaultNextCorrection(CharSequence previous, CharSequence typed,
            CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, previous);
        return isDefaultSuggestion(suggestions, expected) && mSuggest.hasMinimalCorrection();
    }

    boolean isASuggestion(CharSequence previous, CharSequence typed,
            CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        List<CharSequence> suggestions = mSuggest.getSuggestions(null, word, false, previous);
        for (int i = 1; i < suggestions.size(); i++) {
            if (TextUtils.equals(suggestions.get(i), expected)) return true;
        }
        return false;
    }

    boolean isValid(CharSequence typed) {
        return mSuggest.isValidWord(typed);
    }

    boolean isUserBigramSuggestion(CharSequence previous, char typed,
           CharSequence expected) {
        WordComposer word = createWordComposer(Character.toString(typed));

        if (mUserBigram == null) return false;

        flushUserBigrams();
        if (!TextUtils.isEmpty(previous) && !TextUtils.isEmpty(Character.toString(typed))) {
            WordComposer firstChar = createWordComposer(Character.toString(typed));
            mSuggest.getSuggestions(null, firstChar, false, previous);
            boolean reloading = mUserBigram.reloadDictionaryIfRequired();
            if (reloading) mUserBigram.waitForDictionaryLoading();
            mUserBigram.getBigrams(firstChar, previous, mSuggest, null);
        }

        List<CharSequence> suggestions = mSuggest.mBigramSuggestions;
        for (int i = 0; i < suggestions.size(); i++) {
            if (TextUtils.equals(suggestions.get(i), expected)) return true;
        }

        return false;
    }

    void addToUserBigram(String sentence) {
        StringTokenizer st = new StringTokenizer(sentence);
        String previous = null;
        while (st.hasMoreTokens()) {
            String current = st.nextToken();
            if (previous != null) {
                addToUserBigram(new String[] {previous, current});
            }
            previous = current;
        }
    }

    void addToUserBigram(String[] pair) {
        if (mUserBigram != null && pair.length == 2) {
            mUserBigram.addBigrams(pair[0], pair[1]);
        }
    }

    void flushUserBigrams() {
        if (mUserBigram != null) {
            mUserBigram.flushPendingWrites();
            mUserBigram.waitUntilUpdateDBDone();
        }
    }

    final int[][] adjacents = {
                               {'a','s','w','q',-1},
                               {'b','h','v','n','g','j',-1},
                               {'c','v','f','x','g',},
                               {'d','f','r','e','s','x',-1},
                               {'e','w','r','s','d',-1},
                               {'f','g','d','c','t','r',-1},
                               {'g','h','f','y','t','v',-1},
                               {'h','j','u','g','b','y',-1},
                               {'i','o','u','k',-1},
                               {'j','k','i','h','u','n',-1},
                               {'k','l','o','j','i','m',-1},
                               {'l','k','o','p',-1},
                               {'m','k','n','l',-1},
                               {'n','m','j','k','b',-1},
                               {'o','p','i','l',-1},
                               {'p','o',-1},
                               {'q','w',-1},
                               {'r','t','e','f',-1},
                               {'s','d','e','w','a','z',-1},
                               {'t','y','r',-1},
                               {'u','y','i','h','j',-1},
                               {'v','b','g','c','h',-1},
                               {'w','e','q',-1},
                               {'x','c','d','z','f',-1},
                               {'y','u','t','h','g',-1},
                               {'z','s','x','a','d',-1},
                              };
}
