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

package com.android.inputmethod.latin.utils;

import java.util.regex.Pattern;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.PrevWordsInfo;
import com.android.inputmethod.latin.PrevWordsInfo.WordInfo;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;

public final class PrevWordsInfoUtils {
    private PrevWordsInfoUtils() {
        // Intentional empty constructor for utility class.
    }

    private static final Pattern SPACE_REGEX = Pattern.compile("\\s+");
    // Get context information from nth word before the cursor. n = 1 retrieves the words
    // immediately before the cursor, n = 2 retrieves the words before that, and so on. This splits
    // on whitespace only.
    // Also, it won't return words that end in a separator (if the nth word before the cursor
    // ends in a separator, it returns information representing beginning-of-sentence).
    // Example (when Constants.MAX_PREV_WORD_COUNT_FOR_N_GRAM is 2):
    // (n = 1) "abc def|" -> abc, def
    // (n = 1) "abc def |" -> abc, def
    // (n = 1) "abc 'def|" -> empty, 'def
    // (n = 1) "abc def. |" -> beginning-of-sentence
    // (n = 1) "abc def . |" -> beginning-of-sentence
    // (n = 2) "abc def|" -> beginning-of-sentence, abc
    // (n = 2) "abc def |" -> beginning-of-sentence, abc
    // (n = 2) "abc 'def|" -> empty. The context is different from "abc def", but we cannot
    // represent this situation using PrevWordsInfo. See TODO in the method.
    // TODO: The next example's result should be "abc, def". This have to be fixed before we
    // retrieve the prior context of Beginning-of-Sentence.
    // (n = 2) "abc def. |" -> beginning-of-sentence, abc
    // (n = 2) "abc def . |" -> abc, def
    // (n = 2) "abc|" -> beginning-of-sentence
    // (n = 2) "abc |" -> beginning-of-sentence
    // (n = 2) "abc. def|" -> beginning-of-sentence
    public static PrevWordsInfo getPrevWordsInfoFromNthPreviousWord(final CharSequence prev,
            final SpacingAndPunctuations spacingAndPunctuations, final int n) {
        if (prev == null) return PrevWordsInfo.EMPTY_PREV_WORDS_INFO;
        final String[] w = SPACE_REGEX.split(prev);
        final WordInfo[] prevWordsInfo = new WordInfo[Constants.MAX_PREV_WORD_COUNT_FOR_N_GRAM];
        for (int i = 0; i < prevWordsInfo.length; i++) {
            final int focusedWordIndex = w.length - n - i;
            // Referring to the word after the focused word.
            if ((focusedWordIndex + 1) >= 0 && (focusedWordIndex + 1) < w.length) {
                final String wordFollowingTheNthPrevWord = w[focusedWordIndex + 1];
                if (!wordFollowingTheNthPrevWord.isEmpty()) {
                    final char firstChar = wordFollowingTheNthPrevWord.charAt(0);
                    if (spacingAndPunctuations.isWordConnector(firstChar)) {
                        // The word following the focused word is starting with a word connector.
                        // TODO: Return meaningful context for this case.
                        prevWordsInfo[i] = WordInfo.EMPTY_WORD_INFO;
                        break;
                    }
                }
            }
            // If we can't find (n + i) words, the context is beginning-of-sentence.
            if (focusedWordIndex < 0) {
                prevWordsInfo[i] = WordInfo.BEGINNING_OF_SENTENCE;
                break;
            }
            final String focusedWord = w[focusedWordIndex];
            // If the word is, the context is beginning-of-sentence.
            final int length = focusedWord.length();
            if (length <= 0) {
                prevWordsInfo[i] = WordInfo.BEGINNING_OF_SENTENCE;
                break;
            }
            // If ends in a sentence separator, the context is beginning-of-sentence.
            final char lastChar = focusedWord.charAt(length - 1);
            if (spacingAndPunctuations.isSentenceSeparator(lastChar)) {
                prevWordsInfo[i] = WordInfo.BEGINNING_OF_SENTENCE;
                break;
            }
            // If ends in a word separator or connector, the context is unclear.
            // TODO: Return meaningful context for this case.
            if (spacingAndPunctuations.isWordSeparator(lastChar)
                    || spacingAndPunctuations.isWordConnector(lastChar)) {
                prevWordsInfo[i] = WordInfo.EMPTY_WORD_INFO;
                break;
            }
            prevWordsInfo[i] = new WordInfo(focusedWord);
        }
        return new PrevWordsInfo(prevWordsInfo);
    }
}
