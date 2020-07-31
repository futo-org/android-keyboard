/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import javax.annotation.Nullable;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SuggestionSpanUtilsTest {

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    /**
     * Helper method to create a placeholder {@link SuggestedWordInfo}.
     *
     * @param kindAndFlags the kind and flags to be used to create {@link SuggestedWordInfo}.
     * @param word the word to be used to create {@link SuggestedWordInfo}.
     * @return a new instance of {@link SuggestedWordInfo}.
     */
    private static SuggestedWordInfo createWordInfo(final String word, final int kindAndFlags) {
        return new SuggestedWordInfo(word, "" /* prevWordsContext */, 1 /* score */, kindAndFlags,
                null /* sourceDict */,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */);
    }

    private static void assertNotSuggestionSpan(final String expectedText,
            final CharSequence actualText) {
        assertTrue(TextUtils.equals(expectedText, actualText));
        if (!(actualText instanceof Spanned)) {
            return;
        }
        final Spanned spanned = (Spanned)actualText;
        final SuggestionSpan[] suggestionSpans = spanned.getSpans(0, spanned.length(),
                SuggestionSpan.class);
        assertEquals(0, suggestionSpans.length);
    }

    private static void assertSuggestionSpan(final String expectedText,
            final int reuiredSuggestionSpanFlags, final int requiredSpanFlags,
            final String[] expectedSuggestions, @Nullable final Locale expectedLocale,
            final CharSequence actualText) {
        assertTrue(TextUtils.equals(expectedText, actualText));
        assertTrue(actualText instanceof Spanned);
        final Spanned spanned = (Spanned)actualText;
        final SuggestionSpan[] suggestionSpans = spanned.getSpans(0, spanned.length(),
                SuggestionSpan.class);
        assertEquals(1, suggestionSpans.length);
        final SuggestionSpan suggestionSpan = suggestionSpans[0];
        if (reuiredSuggestionSpanFlags != 0) {
            assertTrue((suggestionSpan.getFlags() & reuiredSuggestionSpanFlags) != 0);
        }
        if (requiredSpanFlags != 0) {
            assertTrue((spanned.getSpanFlags(suggestionSpan) & requiredSpanFlags) != 0);
        }
        if (expectedSuggestions != null) {
            final String[] actualSuggestions = suggestionSpan.getSuggestions();
            assertEquals(expectedSuggestions.length, actualSuggestions.length);
            for (int i = 0; i < expectedSuggestions.length; ++i) {
                assertEquals(expectedSuggestions[i], actualSuggestions[i]);
            }
        }
        // CAVEAT: SuggestionSpan#getLocale() returns String rather than Locale object.
        assertEquals(expectedLocale.toString(), suggestionSpan.getLocale());
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    @Test
    public void testGetTextWithAutoCorrectionIndicatorUnderline() {
        final String ORIGINAL_TEXT = "Hey!";
        final Locale NONNULL_LOCALE = new Locale("en", "GB");
        final CharSequence text = SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(
                getContext(), ORIGINAL_TEXT, NONNULL_LOCALE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            assertNotSuggestionSpan(ORIGINAL_TEXT, text);
            return;
        }
        assertSuggestionSpan(ORIGINAL_TEXT,
                SuggestionSpan.FLAG_AUTO_CORRECTION /* reuiredSuggestionSpanFlags */,
                Spanned.SPAN_COMPOSING | Spanned.SPAN_EXCLUSIVE_EXCLUSIVE /* requiredSpanFlags */,
                new String[]{}, NONNULL_LOCALE, text);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    @Test
    public void testGetTextWithAutoCorrectionIndicatorUnderlineRootLocale() {
        final String ORIGINAL_TEXT = "Hey!";
        final CharSequence text = SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(
                getContext(), ORIGINAL_TEXT, Locale.ROOT);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            assertNotSuggestionSpan(ORIGINAL_TEXT, text);
            return;
        }
        assertSuggestionSpan(ORIGINAL_TEXT,
                SuggestionSpan.FLAG_AUTO_CORRECTION /* reuiredSuggestionSpanFlags */,
                Spanned.SPAN_COMPOSING | Spanned.SPAN_EXCLUSIVE_EXCLUSIVE /* requiredSpanFlags */,
                new String[]{}, Locale.ROOT, text);
    }

    @Test
    public void testGetTextWithSuggestionSpan() {
        final SuggestedWordInfo prediction1 =
                createWordInfo("Quality", SuggestedWordInfo.KIND_PREDICTION);
        final SuggestedWordInfo prediction2 =
                createWordInfo("Speed", SuggestedWordInfo.KIND_PREDICTION);
        final SuggestedWordInfo prediction3 =
                createWordInfo("Price", SuggestedWordInfo.KIND_PREDICTION);

        final SuggestedWordInfo typed =
                createWordInfo("Hey", SuggestedWordInfo.KIND_TYPED);

        final SuggestedWordInfo[] corrections =
                new SuggestedWordInfo[SuggestionSpan.SUGGESTIONS_MAX_SIZE * 2];
        for (int i = 0; i < corrections.length; ++i) {
            corrections[i] = createWordInfo("correction" + i, SuggestedWordInfo.KIND_CORRECTION);
        }

        final Locale NONNULL_LOCALE = new Locale("en", "GB");

        // SuggestionSpan will not be attached when {@link SuggestedWords#INPUT_STYLE_PREDICTION}
        // is specified.
        {
            final SuggestedWords predictedWords = new SuggestedWords(
                    new ArrayList<>(Arrays.asList(prediction1, prediction2, prediction3)),
                    null /* rawSuggestions */,
                    null /* typedWord */,
                    false /* typedWordValid */,
                    false /* willAutoCorrect */,
                    false /* isObsoleteSuggestions */,
                    SuggestedWords.INPUT_STYLE_PREDICTION,
                    SuggestedWords.NOT_A_SEQUENCE_NUMBER);
            final String PICKED_WORD = prediction2.mWord;
            // Note that the framework uses the context locale as a fallback locale.
            assertNotSuggestionSpan(
                    PICKED_WORD,
                    SuggestionSpanUtils.getTextWithSuggestionSpan(getContext(), PICKED_WORD,
                            predictedWords, NONNULL_LOCALE));
        }

        final ArrayList<SuggestedWordInfo> suggestedWordList = new ArrayList<>();
        suggestedWordList.add(typed);
        suggestedWordList.add(prediction1);
        suggestedWordList.add(prediction2);
        suggestedWordList.add(prediction3);
        suggestedWordList.addAll(Arrays.asList(corrections));
        final SuggestedWords typedAndCollectedWords = new SuggestedWords(
                suggestedWordList,
                null /* rawSuggestions */,
                null /* typedWord */,
                false /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isObsoleteSuggestions */,
                SuggestedWords.INPUT_STYLE_TYPING,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER);

        for (final SuggestedWordInfo pickedWord : suggestedWordList) {
            final String PICKED_WORD = pickedWord.mWord;

            final ArrayList<String> expectedSuggestions = new ArrayList<>();
            for (SuggestedWordInfo suggestedWordInfo : suggestedWordList) {
                if (expectedSuggestions.size() >= SuggestionSpan.SUGGESTIONS_MAX_SIZE) {
                    break;
                }
                if (suggestedWordInfo.isKindOf(SuggestedWordInfo.KIND_PREDICTION)) {
                    // Currently predictions are not filled into SuggestionSpan.
                    continue;
                }
                final String suggestedWord = suggestedWordInfo.mWord;
                if (TextUtils.equals(PICKED_WORD, suggestedWord)) {
                    // Typed word itself is not added to SuggestionSpan.
                    continue;
                }
                expectedSuggestions.add(suggestedWord);
            }

            // non-null locale
            assertSuggestionSpan(
                    PICKED_WORD,
                    0 /* reuiredSuggestionSpanFlags */,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE /* requiredSpanFlags */,
                    expectedSuggestions.toArray(new String[expectedSuggestions.size()]),
                    NONNULL_LOCALE,
                    SuggestionSpanUtils.getTextWithSuggestionSpan(getContext(), PICKED_WORD,
                            typedAndCollectedWords, NONNULL_LOCALE));

            // root locale
            assertSuggestionSpan(
                    PICKED_WORD,
                    0 /* reuiredSuggestionSpanFlags */,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE /* requiredSpanFlags */,
                    expectedSuggestions.toArray(new String[expectedSuggestions.size()]),
                    Locale.ROOT,
                    SuggestionSpanUtils.getTextWithSuggestionSpan(getContext(), PICKED_WORD,
                            typedAndCollectedWords, Locale.ROOT));
        }
    }

    @Test
    public void testFindFirstLocaleFromSuggestionSpans() {
        final String[] suggestions = new String[] {"Quality", "Speed", "Price"};
        final SuggestionSpan nullLocaleSpan = new SuggestionSpan((Locale)null, suggestions, 0);
        final SuggestionSpan emptyLocaleSpan = new SuggestionSpan(new Locale(""), suggestions, 0);
        final SuggestionSpan enUsLocaleSpan = new SuggestionSpan(Locale.US, suggestions, 0);
        final SuggestionSpan jaJpLocaleSpan = new SuggestionSpan(Locale.JAPAN, suggestions, 0);

        assertEquals(null, SuggestionSpanUtils.findFirstLocaleFromSuggestionSpans(
                new SuggestionSpan[] {}));

        assertEquals(null, SuggestionSpanUtils.findFirstLocaleFromSuggestionSpans(
                new SuggestionSpan[] {emptyLocaleSpan}));

        assertEquals(Locale.US, SuggestionSpanUtils.findFirstLocaleFromSuggestionSpans(
                new SuggestionSpan[] {enUsLocaleSpan}));

        assertEquals(Locale.US, SuggestionSpanUtils.findFirstLocaleFromSuggestionSpans(
                new SuggestionSpan[] {nullLocaleSpan, enUsLocaleSpan}));

        assertEquals(Locale.US, SuggestionSpanUtils.findFirstLocaleFromSuggestionSpans(
                new SuggestionSpan[] {nullLocaleSpan, emptyLocaleSpan, enUsLocaleSpan}));

        assertEquals(Locale.JAPAN, SuggestionSpanUtils.findFirstLocaleFromSuggestionSpans(
                new SuggestionSpan[] {nullLocaleSpan, jaJpLocaleSpan, enUsLocaleSpan}));
    }
}
