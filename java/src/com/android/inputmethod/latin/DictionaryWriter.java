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

package com.android.inputmethod.latin;

import android.content.Context;

import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.makedict.DictEncoder;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.PtNodeArray;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * An in memory dictionary for memorizing entries and writing a binary dictionary.
 */
public class DictionaryWriter extends AbstractDictionaryWriter {
    private static final int BINARY_DICT_VERSION = 3;
    private static final FormatSpec.FormatOptions FORMAT_OPTIONS =
            new FormatSpec.FormatOptions(BINARY_DICT_VERSION, true /* supportsDynamicUpdate */);

    private FusionDictionary mFusionDictionary;

    public DictionaryWriter(final Context context, final String dictType) {
        super(context, dictType);
        clear();
    }

    @Override
    public void clear() {
        final HashMap<String, String> attributes = CollectionUtils.newHashMap();
        mFusionDictionary = new FusionDictionary(new PtNodeArray(),
                new FusionDictionary.DictionaryOptions(attributes, false, false));
    }

    /**
     * Adds a word unigram to the fusion dictionary.
     */
    // TODO: Create "cache dictionary" to cache fresh words for frequently updated dictionaries,
    // considering performance regression.
    @Override
    public void addUnigramWord(final String word, final String shortcutTarget, final int frequency,
            final int shortcutFreq, final boolean isNotAWord) {
        if (shortcutTarget == null) {
            mFusionDictionary.add(word, frequency, null, isNotAWord);
        } else {
            // TODO: Do this in the subclass, with this class taking an arraylist.
            final ArrayList<WeightedString> shortcutTargets = CollectionUtils.newArrayList();
            shortcutTargets.add(new WeightedString(shortcutTarget, shortcutFreq));
            mFusionDictionary.add(word, frequency, shortcutTargets, isNotAWord);
        }
    }

    @Override
    public void addBigramWords(final String word0, final String word1, final int frequency,
            final boolean isValid, final long lastModifiedTime) {
        mFusionDictionary.setBigram(word0, word1, frequency);
    }

    @Override
    public void removeBigramWords(final String word0, final String word1) {
        // This class don't support removing bigram words.
    }

    @Override
    protected void writeDictionary(final DictEncoder dictEncoder,
            final Map<String, String> attributeMap) throws IOException, UnsupportedFormatException {
        for (final Map.Entry<String, String> entry : attributeMap.entrySet()) {
            mFusionDictionary.addOptionAttribute(entry.getKey(), entry.getValue());
        }
        dictEncoder.writeDictionary(mFusionDictionary, FORMAT_OPTIONS);
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            boolean blockOffensiveWords, final int[] additionalFeaturesOptions) {
        // This class doesn't support suggestion.
        return null;
    }

    @Override
    public boolean isValidWord(String word) {
        // This class doesn't support dictionary retrieval.
        return false;
    }
}
