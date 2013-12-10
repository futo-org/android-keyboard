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

import com.android.inputmethod.latin.BinaryDictionary.LanguageModelParam;
import com.android.inputmethod.latin.ExpandableBinaryDictionary;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

/**
 * This class is a session where a data provider can communicate with a personalization
 * dictionary.
 */
public abstract class PersonalizationDictionaryUpdateSession {
    // TODO: Use a dynamic binary dictionary instead
    public WeakReference<PersonalizationDictionary> mDictionary;
    public WeakReference<DecayingExpandableBinaryDictionaryBase> mPredictionDictionary;
    public final Locale mSystemLocale;

    public PersonalizationDictionaryUpdateSession(final Locale locale) {
        mSystemLocale = locale;
    }

    public abstract void onDictionaryReady();

    public abstract void onDictionaryClosed(Context context);

    public void setDictionary(PersonalizationDictionary dictionary) {
        mDictionary = new WeakReference<PersonalizationDictionary>(dictionary);
    }

    public void setPredictionDictionary(DecayingExpandableBinaryDictionaryBase dictionary) {
        mPredictionDictionary =
                new WeakReference<DecayingExpandableBinaryDictionaryBase>(dictionary);
    }

    protected PersonalizationDictionary getDictionary() {
        return mDictionary == null ? null : mDictionary.get();
    }

    protected DecayingExpandableBinaryDictionaryBase getPredictionDictionary() {
        return mPredictionDictionary == null ? null : mPredictionDictionary.get();
    }

    private void unsetDictionary() {
        final PersonalizationDictionary dictionary = getDictionary();
        if (dictionary == null) {
            return;
        }
        dictionary.unRegisterUpdateSession(this);
    }

    private void unsetPredictionDictionary() {
        final DecayingExpandableBinaryDictionaryBase dictionary = getPredictionDictionary();
        if (dictionary == null) {
            return;
        }
        dictionary.unRegisterUpdateSession(this);
    }

    public void clearAndFlushPredictionDictionary(Context context) {
        final DecayingExpandableBinaryDictionaryBase dictionary = getPredictionDictionary();
        if (dictionary == null) {
            return;
        }
        dictionary.clearAndFlushDictionary();
    }

    public void closeSession(Context context) {
        unsetDictionary();
        unsetPredictionDictionary();
        onDictionaryClosed(context);
    }

    // TODO: Support multi locale to add bigram
    public void addBigramToPersonalizationDictionary(String word0, String word1, boolean isValid,
            int frequency, int timestamp) {
        final DecayingExpandableBinaryDictionaryBase dictionary = getPredictionDictionary();
        if (dictionary == null) {
            return;
        }
        dictionary.addToDictionary(word0, word1, isValid, timestamp);
    }

    // TODO: Support multi locale.
    public void addMultipleDictionaryEntriesToPersonalizationDictionary(
            final ArrayList<LanguageModelParam> languageModelParams,
            final ExpandableBinaryDictionary.AddMultipleDictionaryEntriesCallback callback) {
        final DecayingExpandableBinaryDictionaryBase dictionary = getPredictionDictionary();
        if (dictionary == null) {
            if (callback != null) {
                callback.onFinished();
            }
            return;
        }
        dictionary.addMultipleDictionaryEntriesToDictionary(languageModelParams, callback);
    }
}
