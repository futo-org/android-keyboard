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

import android.service.textservice.SpellCheckerService;
import android.util.Log;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

/**
 * Service for spell checking, using LatinIME's dictionaries and mechanisms.
 */
public class AndroidSpellCheckerService extends SpellCheckerService {
    private static final String TAG = AndroidSpellCheckerService.class.getSimpleName();
    private static final boolean DBG = true;
    @Override
    public SuggestionsInfo getSuggestions(TextInfo textInfo, int suggestionsLimit,
            String locale) {
        // TODO: implement this
        final String text = textInfo.getText();
        if (DBG) {
            Log.w(TAG, "getSuggestions: " + text);
        }
        String[] candidates0 = new String[] {text, "candidate1", "candidate2", "candidate3"};
        String[] candidates1 = new String[] {text, "candidateA", "candidateB"};
        final int textLength = textInfo.getText().length() % 3;
        if (textLength % 3 == 0) {
            return new SuggestionsInfo(2
                    | SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, candidates0);
        } else if (textLength % 3 == 1) {
            return new SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, candidates1);
        } else {
            return new SuggestionsInfo(0, null);
        }
    }
}
