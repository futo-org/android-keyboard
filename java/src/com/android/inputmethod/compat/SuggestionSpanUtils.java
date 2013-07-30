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

package com.android.inputmethod.compat;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;

import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestionSpanPickedNotificationReceiver;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;

public final class SuggestionSpanUtils {
    // Note that SuggestionSpan.FLAG_AUTO_CORRECTION has been introduced
    // in API level 15 (Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1).
    public static final Field FIELD_FLAG_AUTO_CORRECTION = CompatUtils.getField(
            SuggestionSpan.class, "FLAG_AUTO_CORRECTION");
    public static final Integer OBJ_FLAG_AUTO_CORRECTION = (Integer) CompatUtils.getFieldValue(
            null /* receiver */, null /* defaultValue */, FIELD_FLAG_AUTO_CORRECTION);

    static {
        if (LatinImeLogger.sDBG) {
            if (OBJ_FLAG_AUTO_CORRECTION == null) {
                throw new RuntimeException("Field is accidentially null.");
            }
        }
    }

    private SuggestionSpanUtils() {
        // This utility class is not publicly instantiable.
    }

    public static CharSequence getTextWithAutoCorrectionIndicatorUnderline(
            final Context context, final String text) {
        if (TextUtils.isEmpty(text) || OBJ_FLAG_AUTO_CORRECTION == null) {
            return text;
        }
        final Spannable spannable = new SpannableString(text);
        final SuggestionSpan suggestionSpan = new SuggestionSpan(context, null /* locale */,
                new String[] {} /* suggestions */, OBJ_FLAG_AUTO_CORRECTION,
                SuggestionSpanPickedNotificationReceiver.class);
        spannable.setSpan(suggestionSpan, 0, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_COMPOSING);
        return spannable;
    }

    public static CharSequence getTextWithSuggestionSpan(final Context context,
            final String pickedWord, final SuggestedWords suggestedWords,
            final boolean dictionaryAvailable) {
        if (!dictionaryAvailable || TextUtils.isEmpty(pickedWord) || suggestedWords.isEmpty()
                || suggestedWords.mIsPrediction || suggestedWords.mIsPunctuationSuggestions) {
            return pickedWord;
        }

        final Spannable spannable = new SpannableString(pickedWord);
        final ArrayList<String> suggestionsList = CollectionUtils.newArrayList();
        for (int i = 0; i < suggestedWords.size(); ++i) {
            if (suggestionsList.size() >= SuggestionSpan.SUGGESTIONS_MAX_SIZE) {
                break;
            }
            final String word = suggestedWords.getWord(i);
            if (!TextUtils.equals(pickedWord, word)) {
                suggestionsList.add(word.toString());
            }
        }

        // TODO: We should avoid adding suggestion span candidates that came from the bigram
        // prediction.
        final SuggestionSpan suggestionSpan = new SuggestionSpan(context, null /* locale */,
                suggestionsList.toArray(new String[suggestionsList.size()]), 0 /* flags */,
                SuggestionSpanPickedNotificationReceiver.class);
        spannable.setSpan(suggestionSpan, 0, pickedWord.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }
}
