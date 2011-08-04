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

package com.android.inputmethod.latin;

import com.android.inputmethod.keyboard.ProximityInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class for a collection of dictionaries that behave like one dictionary.
 */
public class DictionaryCollection extends Dictionary {

    protected final List<Dictionary> mDictionaries;

    public DictionaryCollection() {
        mDictionaries = new CopyOnWriteArrayList<Dictionary>();
    }

    public DictionaryCollection(Dictionary... dictionaries) {
        if (null == dictionaries) {
            mDictionaries = new CopyOnWriteArrayList<Dictionary>();
        } else {
            mDictionaries = new CopyOnWriteArrayList<Dictionary>(dictionaries);
            mDictionaries.removeAll(Collections.singleton(null));
        }
    }

    public DictionaryCollection(Collection<Dictionary> dictionaries) {
        mDictionaries = new CopyOnWriteArrayList<Dictionary>(dictionaries);
        mDictionaries.removeAll(Collections.singleton(null));
    }

    @Override
    public void getWords(final WordComposer composer, final WordCallback callback,
            final ProximityInfo proximityInfo) {
        for (final Dictionary dict : mDictionaries)
            dict.getWords(composer, callback, proximityInfo);
    }

    @Override
    public void getBigrams(final WordComposer composer, final CharSequence previousWord,
            final WordCallback callback) {
        for (final Dictionary dict : mDictionaries)
            dict.getBigrams(composer, previousWord, callback);
    }

    @Override
    public boolean isValidWord(CharSequence word) {
        for (int i = mDictionaries.size() - 1; i >= 0; --i)
            if (mDictionaries.get(i).isValidWord(word)) return true;
        return false;
    }

    @Override
    public void close() {
        for (final Dictionary dict : mDictionaries)
            dict.close();
    }

    public void addDictionary(Dictionary newDict) {
        if (null != newDict) mDictionaries.add(newDict);
    }
}
