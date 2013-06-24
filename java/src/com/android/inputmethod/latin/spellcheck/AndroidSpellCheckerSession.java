/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.os.Binder;
import android.text.TextUtils;
import android.util.Log;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.ArrayList;

public final class AndroidSpellCheckerSession extends AndroidWordLevelSpellCheckerSession {
    private static final String TAG = AndroidSpellCheckerSession.class.getSimpleName();
    private static final boolean DBG = false;
    private final static String[] EMPTY_STRING_ARRAY = new String[0];

    public AndroidSpellCheckerSession(AndroidSpellCheckerService service) {
        super(service);
    }

    private SentenceSuggestionsInfo fixWronglyInvalidatedWordWithSingleQuote(TextInfo ti,
            SentenceSuggestionsInfo ssi) {
        final String typedText = ti.getText();
        if (!typedText.contains(AndroidSpellCheckerService.SINGLE_QUOTE)) {
            return null;
        }
        final int N = ssi.getSuggestionsCount();
        final ArrayList<Integer> additionalOffsets = CollectionUtils.newArrayList();
        final ArrayList<Integer> additionalLengths = CollectionUtils.newArrayList();
        final ArrayList<SuggestionsInfo> additionalSuggestionsInfos =
                CollectionUtils.newArrayList();
        String currentWord = null;
        for (int i = 0; i < N; ++i) {
            final SuggestionsInfo si = ssi.getSuggestionsInfoAt(i);
            final int flags = si.getSuggestionsAttributes();
            if ((flags & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) == 0) {
                continue;
            }
            final int offset = ssi.getOffsetAt(i);
            final int length = ssi.getLengthAt(i);
            final String subText = typedText.substring(offset, offset + length);
            final String prevWord = currentWord;
            currentWord = subText;
            if (!subText.contains(AndroidSpellCheckerService.SINGLE_QUOTE)) {
                continue;
            }
            final String[] splitTexts =
                    subText.split(AndroidSpellCheckerService.SINGLE_QUOTE, -1);
            if (splitTexts == null || splitTexts.length <= 1) {
                continue;
            }
            final int splitNum = splitTexts.length;
            for (int j = 0; j < splitNum; ++j) {
                final String splitText = splitTexts[j];
                if (TextUtils.isEmpty(splitText)) {
                    continue;
                }
                if (mSuggestionsCache.getSuggestionsFromCache(splitText, prevWord) == null) {
                    continue;
                }
                final int newLength = splitText.length();
                // Neither RESULT_ATTR_IN_THE_DICTIONARY nor RESULT_ATTR_LOOKS_LIKE_TYPO
                final int newFlags = 0;
                final SuggestionsInfo newSi =
                        new SuggestionsInfo(newFlags, EMPTY_STRING_ARRAY);
                newSi.setCookieAndSequence(si.getCookie(), si.getSequence());
                if (DBG) {
                    Log.d(TAG, "Override and remove old span over: " + splitText + ", "
                            + offset + "," + newLength);
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
    public SentenceSuggestionsInfo[] onGetSentenceSuggestionsMultiple(TextInfo[] textInfos,
            int suggestionsLimit) {
        final SentenceSuggestionsInfo[] retval =
                super.onGetSentenceSuggestionsMultiple(textInfos, suggestionsLimit);
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
        long ident = Binder.clearCallingIdentity();
        try {
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
                retval[i] = onGetSuggestionsInternal(textInfos[i], prevWord, suggestionsLimit);
                retval[i].setCookieAndSequence(textInfos[i].getCookie(),
                        textInfos[i].getSequence());
            }
            return retval;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }
}
