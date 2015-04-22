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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.define.DebugFlags;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SuggestionSpanUtils {
    // Note that SuggestionSpan.FLAG_AUTO_CORRECTION has been introduced
    // in API level 15 (Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1).
    private static final Field FIELD_FLAG_AUTO_CORRECTION = CompatUtils.getField(
            SuggestionSpan.class, "FLAG_AUTO_CORRECTION");
    private static final Integer OBJ_FLAG_AUTO_CORRECTION = (Integer) CompatUtils.getFieldValue(
            null /* receiver */, null /* defaultValue */, FIELD_FLAG_AUTO_CORRECTION);

    static {
        if (DebugFlags.DEBUG_ENABLED) {
            if (OBJ_FLAG_AUTO_CORRECTION == null) {
                throw new RuntimeException("Field is accidentially null.");
            }
        }
    }

    private SuggestionSpanUtils() {
        // This utility class is not publicly instantiable.
    }

    @UsedForTesting
    public static CharSequence getTextWithAutoCorrectionIndicatorUnderline(
            final Context context, final String text, @Nonnull final Locale locale) {
        if (TextUtils.isEmpty(text) || OBJ_FLAG_AUTO_CORRECTION == null) {
            return text;
        }
        final Spannable spannable = new SpannableString(text);
        final SuggestionSpan suggestionSpan = new SuggestionSpan(context, locale,
                new String[] {} /* suggestions */, OBJ_FLAG_AUTO_CORRECTION, null);
        spannable.setSpan(suggestionSpan, 0, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_COMPOSING);
        return spannable;
    }

    @UsedForTesting
    public static CharSequence getTextWithSuggestionSpan(final Context context,
            final String pickedWord, final SuggestedWords suggestedWords, final Locale locale) {
        if (TextUtils.isEmpty(pickedWord) || suggestedWords.isEmpty()
                || suggestedWords.isPrediction() || suggestedWords.isPunctuationSuggestions()) {
            return pickedWord;
        }

        final ArrayList<String> suggestionsList = new ArrayList<>();
        for (int i = 0; i < suggestedWords.size(); ++i) {
            if (suggestionsList.size() >= SuggestionSpan.SUGGESTIONS_MAX_SIZE) {
                break;
            }
            final SuggestedWordInfo info = suggestedWords.getInfo(i);
            if (info.isKindOf(SuggestedWordInfo.KIND_PREDICTION)) {
                continue;
            }
            final String word = suggestedWords.getWord(i);
            if (!TextUtils.equals(pickedWord, word)) {
                suggestionsList.add(word.toString());
            }
        }
        final SuggestionSpan suggestionSpan = new SuggestionSpan(context, locale,
                suggestionsList.toArray(new String[suggestionsList.size()]), 0 /* flags */, null);
        final Spannable spannable = new SpannableString(pickedWord);
        spannable.setSpan(suggestionSpan, 0, pickedWord.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    /**
     * Returns first {@link Locale} found in the given array of {@link SuggestionSpan}.
     * @param suggestionSpans the array of {@link SuggestionSpan} to be examined.
     * @return the first {@link Locale} found in {@code suggestionSpans}. {@code null} when not
     * found.
     */
    @UsedForTesting
    @Nullable
    public static Locale findFirstLocaleFromSuggestionSpans(
            final SuggestionSpan[] suggestionSpans) {
        for (final SuggestionSpan suggestionSpan : suggestionSpans) {
            final String localeString = suggestionSpan.getLocale();
            if (TextUtils.isEmpty(localeString)) {
                continue;
            }
            return LocaleUtils.constructLocaleFromString(localeString);
        }
        return null;
    }
}
