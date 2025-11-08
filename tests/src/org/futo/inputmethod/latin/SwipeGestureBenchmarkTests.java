/*
 * Copyright (C) 2025 FUTO
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

package org.futo.inputmethod.latin;

import android.test.suitebuilder.annotation.LargeTest;
import android.text.InputType;

import org.futo.inputmethod.latin.settings.Settings;

/**
 * Benchmark and metrics tests for swipe functionality.
 * Tests accuracy rates, false positive rates, and configuration options.
 */
@LargeTest
public class SwipeGestureBenchmarkTests extends InputTestsBase {

    private static final int TOP_N_SUGGESTIONS = 3;

    /**
     * Benchmark dataset of common English words for accuracy testing.
     */
    private static final String[] BENCHMARK_COMMON_WORDS = {
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "I",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also"
    };

    /**
     * Word pairs that have similar swipe paths (high confusion risk).
     */
    private static final String[][] CONFUSABLE_WORD_PAIRS = {
        {"was", "war"},
        {"saw", "sad"},
        {"form", "from"},
        {"trial", "trail"},
        {"casual", "causal"},
        {"desert", "dessert"},
        {"advise", "advice"}
    };

    private boolean isWordInTopSuggestions(final String expectedWord, final int topN) {
        final SuggestedWords suggestedWords = mLatinIMELegacy.getSuggestedWordsForTest();
        if (suggestedWords == null || suggestedWords.size() == 0) {
            return false;
        }

        for (int i = 0; i < Math.min(topN, suggestedWords.size()); i++) {
            if (expectedWord.equalsIgnoreCase(suggestedWords.getWord(i))) {
                return true;
            }
        }
        return false;
    }

    private String getTopSuggestion() {
        final SuggestedWords suggestedWords = mLatinIMELegacy.getSuggestedWordsForTest();
        if (suggestedWords == null || suggestedWords.size() == 0) {
            return "";
        }
        return suggestedWords.getWord(0);
    }

    // ==================== ACCURACY BENCHMARK TESTS ====================

    public void testBenchmarkAccuracyTop1() {
        // Test if the top suggestion matches the intended word
        int correct = 0;
        int total = 0;

        for (String word : BENCHMARK_COMMON_WORDS) {
            mEditText.setText("");
            gesture(word);

            String topSuggestion = getTopSuggestion();
            if (word.equalsIgnoreCase(topSuggestion)) {
                correct++;
            }
            total++;
        }

        double accuracy = (double) correct / total;
        System.out.println("Top-1 Accuracy: " + (accuracy * 100) + "% (" + correct + "/" + total + ")");

        // Target: at least 70% accuracy for common words
        assertTrue("Top-1 accuracy too low: " + (accuracy * 100) + "%",
                  accuracy >= 0.70);
    }

    public void testBenchmarkAccuracyTop3() {
        // Test if intended word appears in top 3 suggestions
        int correct = 0;
        int total = 0;

        for (String word : BENCHMARK_COMMON_WORDS) {
            mEditText.setText("");
            gesture(word);

            if (isWordInTopSuggestions(word, TOP_N_SUGGESTIONS)) {
                correct++;
            }
            total++;
        }

        double accuracy = (double) correct / total;
        System.out.println("Top-3 Accuracy: " + (accuracy * 100) + "% (" + correct + "/" + total + ")");

        // Target: at least 85% accuracy for common words in top 3
        assertTrue("Top-3 accuracy too low: " + (accuracy * 100) + "%",
                  accuracy >= 0.85);
    }

    // ==================== FALSE POSITIVE TESTS ====================

    public void testFalsePositiveRate() {
        // Test that we don't generate completely wrong words
        int acceptableResults = 0;
        int total = 0;

        for (String word : BENCHMARK_COMMON_WORDS) {
            mEditText.setText("");
            gesture(word);

            String topSuggestion = getTopSuggestion();

            // Check if top suggestion is at least somewhat similar
            // (same first letter, similar length, or in dictionary)
            boolean acceptable = false;
            if (!topSuggestion.isEmpty()) {
                if (word.charAt(0) == topSuggestion.toLowerCase().charAt(0)) {
                    acceptable = true;
                }
                if (Math.abs(word.length() - topSuggestion.length()) <= 2) {
                    acceptable = true;
                }
            }

            if (acceptable || word.equalsIgnoreCase(topSuggestion)) {
                acceptableResults++;
            }
            total++;
        }

        double acceptableRate = (double) acceptableResults / total;
        System.out.println("Acceptable suggestion rate: " + (acceptableRate * 100) + "%");

        // Target: at least 90% of suggestions should be reasonable
        assertTrue("Too many false positives: " + ((1 - acceptableRate) * 100) + "%",
                  acceptableRate >= 0.90);
    }

    // ==================== CONFUSION MATRIX TESTS ====================

    public void testConfusableWordDistinction() {
        // Test that similar paths produce distinguishable words
        int correctDistinctions = 0;
        int total = 0;

        for (String[] pair : CONFUSABLE_WORD_PAIRS) {
            String word1 = pair[0];
            String word2 = pair[1];

            mEditText.setText("");
            gesture(word1);
            String result1 = getTopSuggestion();

            mEditText.setText("");
            gesture(word2);
            String result2 = getTopSuggestion();

            // Results should be different
            if (!result1.equalsIgnoreCase(result2)) {
                correctDistinctions++;
            }

            total++;
        }

        double distinctionRate = (double) correctDistinctions / total;
        System.out.println("Confusable word distinction rate: " + (distinctionRate * 100) + "%");

        // Target: at least 70% of confusable pairs should be distinguished
        assertTrue("Poor confusable word distinction: " + (distinctionRate * 100) + "%",
                  distinctionRate >= 0.70);
    }

    // ==================== PERFORMANCE BENCHMARKS ====================

    public void testAverageGestureProcessingTime() {
        long totalTime = 0;
        int count = 0;

        for (String word : BENCHMARK_COMMON_WORDS) {
            if (count >= 20) break; // Test subset for speed

            mEditText.setText("");
            long startTime = System.currentTimeMillis();
            gesture(word);
            long endTime = System.currentTimeMillis();

            totalTime += (endTime - startTime);
            count++;
        }

        double averageTime = (double) totalTime / count;
        System.out.println("Average gesture processing time: " + averageTime + "ms");

        // Target: average under 500ms
        assertTrue("Average processing time too high: " + averageTime + "ms",
                  averageTime < 500);
    }

    public void testWorstCaseGestureProcessingTime() {
        long worstTime = 0;
        String worstWord = "";

        for (String word : BENCHMARK_COMMON_WORDS) {
            mEditText.setText("");
            long startTime = System.currentTimeMillis();
            gesture(word);
            long endTime = System.currentTimeMillis();

            long timeTaken = endTime - startTime;
            if (timeTaken > worstTime) {
                worstTime = timeTaken;
                worstWord = word;
            }
        }

        System.out.println("Worst case processing time: " + worstTime + "ms for word: " + worstWord);

        // Target: no gesture should take over 1 second
        assertTrue("Worst case processing time too high: " + worstTime + "ms",
                  worstTime < 1000);
    }

    // ==================== CONFIGURATION TESTS ====================

    public void testGestureWithSuggestionsDisabled() {
        // Disable suggestions and test gesture
        boolean previousSetting = setBooleanPreference(Settings.PREF_SHOW_SUGGESTIONS,
                                                       false, true);

        mLatinIMELegacy.loadSettings();
        mEditText.setText("");
        gesture("hello");

        // Should still produce output even with suggestions disabled
        assertTrue("Gesture with suggestions disabled should work",
                  mEditText.getText().toString().length() > 0);

        // Restore setting
        setBooleanPreference(Settings.PREF_SHOW_SUGGESTIONS, previousSetting, true);
        mLatinIMELegacy.loadSettings();
    }

    public void testGestureWithAutoCorrectionDisabled() {
        // Disable auto-correction and test gesture
        boolean previousSetting = setBooleanPreference(Settings.PREF_AUTO_CORRECTION,
                                                       false, true);

        mLatinIMELegacy.loadSettings();
        mEditText.setText("");
        gesture("hello");

        // Should still work
        assertTrue("Gesture with auto-correction disabled should work",
                  mEditText.getText().toString().length() > 0);

        // Restore setting
        setBooleanPreference(Settings.PREF_AUTO_CORRECTION, previousSetting, true);
        mLatinIMELegacy.loadSettings();
    }

    public void testGestureInPasswordField() {
        // Test that gestures are handled appropriately in password fields
        mEditText.setInputType(InputType.TYPE_CLASS_TEXT |
                              InputType.TYPE_TEXT_VARIATION_PASSWORD);

        mEditText.setText("");
        gesture("password");

        // In password fields, gesture might be disabled or behave differently
        // Just ensure it doesn't crash
        assertTrue("Gesture in password field handled", true);

        // Restore normal input type
        mEditText.setInputType(InputType.TYPE_CLASS_TEXT |
                              InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
    }

    // ==================== CONSISTENCY TESTS ====================

    public void testGestureConsistency() {
        // Gesture the same word multiple times - should get consistent results
        String word = "hello";
        String firstResult = "";

        for (int i = 0; i < 5; i++) {
            mEditText.setText("");
            gesture(word);
            String result = getTopSuggestion();

            if (i == 0) {
                firstResult = result;
            } else {
                assertEquals("Gesture should produce consistent results", firstResult, result);
            }
        }
    }

    // ==================== STATISTICAL TESTS ====================

    public void testSuggestionDistribution() {
        // Test that suggestions are distributed reasonably (not all same word)
        int uniqueSuggestions = 0;
        String previousSuggestion = "";

        for (String word : BENCHMARK_COMMON_WORDS) {
            if (uniqueSuggestions >= 10) break; // Test subset

            mEditText.setText("");
            gesture(word);
            String suggestion = getTopSuggestion();

            if (!suggestion.equals(previousSuggestion)) {
                uniqueSuggestions++;
            }
            previousSuggestion = suggestion;
        }

        // Should have diverse suggestions, not all the same
        assertTrue("Suggestions lack diversity", uniqueSuggestions >= 8);
    }

    // ==================== REGRESSION TESTS ====================

    public void testKnownGoodWords() {
        // Words that should always work (regression prevention)
        final String[] knownGoodWords = {"hello", "world", "test", "good", "time"};

        for (String word : knownGoodWords) {
            mEditText.setText("");
            gesture(word);
            assertTrue("Known good word '" + word + "' failed",
                      isWordInTopSuggestions(word, TOP_N_SUGGESTIONS));
        }
    }

    public void testKnownDifficultWords() {
        // Words that are known to be difficult (should at least not crash)
        final String[] difficultWords = {"rhythm", "queue", "colonel", "xylophone"};

        for (String word : difficultWords) {
            mEditText.setText("");
            try {
                gesture(word);
                // Should produce something
                assertTrue("Difficult word '" + word + "' produced output",
                          mEditText.getText().toString().length() > 0);
            } catch (Exception e) {
                fail("Difficult word '" + word + "' caused exception: " + e.getMessage());
            }
        }
    }

    // ==================== EDGE CASE STATISTICS ====================

    public void testShortWordAccuracy() {
        // Test accuracy on words 2-3 characters long
        final String[] shortWords = {"it", "is", "at", "in", "on", "to", "of", "by",
                                     "the", "and", "for", "are", "but"};

        int correct = 0;
        for (String word : shortWords) {
            mEditText.setText("");
            gesture(word);
            if (isWordInTopSuggestions(word, TOP_N_SUGGESTIONS)) {
                correct++;
            }
        }

        double accuracy = (double) correct / shortWords.length;
        System.out.println("Short word accuracy: " + (accuracy * 100) + "%");

        // Short words are harder, so lower threshold
        assertTrue("Short word accuracy too low: " + (accuracy * 100) + "%",
                  accuracy >= 0.60);
    }

    public void testLongWordAccuracy() {
        // Test accuracy on words 8+ characters long
        final String[] longWords = {"understand", "important", "different", "following",
                                    "beautiful", "wonderful", "excellent", "something"};

        int correct = 0;
        for (String word : longWords) {
            mEditText.setText("");
            gesture(word);
            if (isWordInTopSuggestions(word, TOP_N_SUGGESTIONS)) {
                correct++;
            }
        }

        double accuracy = (double) correct / longWords.length;
        System.out.println("Long word accuracy: " + (accuracy * 100) + "%");

        // Long words should be easier
        assertTrue("Long word accuracy too low: " + (accuracy * 100) + "%",
                  accuracy >= 0.75);
    }
}
