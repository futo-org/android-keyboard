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

import android.graphics.Point;
import android.test.suitebuilder.annotation.LargeTest;

import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.InputPointers;

/**
 * Comprehensive test suite for swipe/gesture typing functionality.
 * Tests cover accuracy, performance, edge cases, and context awareness.
 */
@LargeTest
public class SwipeGestureTests extends InputTestsBase {

    // Performance threshold: suggestions should appear within 1 second
    private static final long MAX_GESTURE_PROCESSING_TIME_MS = 1000;

    // Accuracy threshold: top 3 suggestions should contain the intended word
    private static final int TOP_N_SUGGESTIONS = 3;

    /**
     * Helper method to perform gesture and measure time taken.
     * @return time in milliseconds
     */
    private long gestureWithTiming(final String word) {
        final long startTime = System.currentTimeMillis();
        gesture(word);
        final long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    /**
     * Helper method to check if intended word appears in top N suggestions.
     */
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

    /**
     * Helper method to create a curved swipe path (simulating natural human swiping).
     */
    private void gestureCurvedPath(final String word) {
        if (word.length() < 2) {
            throw new RuntimeException("Can't gesture strings less than 2 chars long");
        }

        mLatinIMELegacy.onStartBatchInput();
        final InputPointers pointers = new InputPointers(Constants.DEFAULT_GESTURE_POINTS_CAPACITY);
        int timestamp = 0;

        for (int i = 0; i < word.length(); i = word.offsetByCodePoints(i, 1)) {
            final Point point = getXY(word.codePointAt(i));

            // Add natural curve to the path by adding intermediate points with slight offset
            if (i > 0) {
                final int STEPS = 5;
                Point prevPoint = getXY(word.codePointAt(i - Character.charCount(word.codePointAt(i - 1))));
                for (int j = 1; j <= STEPS; j++) {
                    timestamp += 100;
                    // Add slight curve (arc) to the path
                    int x = prevPoint.x + ((point.x - prevPoint.x) * j) / STEPS;
                    int y = prevPoint.y + ((point.y - prevPoint.y) * j) / STEPS;
                    // Add curve offset (perpendicular to direction)
                    int curveOffset = (int) (20 * Math.sin(Math.PI * j / STEPS));
                    x += curveOffset;
                    pointers.addPointer(x, y, 0, timestamp);
                }
            } else {
                pointers.addPointer(point.x, point.y, 0, timestamp);
            }
        }

        mLatinIMELegacy.onUpdateBatchInput(pointers);
        mLatinIMELegacy.onEndBatchInput(pointers);
        sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
        runMessages();
    }

    // ==================== BASIC ACCURACY TESTS ====================

    public void testBasicCommonWords() {
        final String[] commonWords = {"the", "and", "for", "are", "but", "not", "you", "all",
                                       "can", "her", "was", "one", "our", "out", "day"};

        for (String word : commonWords) {
            mEditText.setText("");
            gesture(word);
            assertTrue("Failed to recognize common word: " + word,
                      isWordInTopSuggestions(word, TOP_N_SUGGESTIONS));
        }
    }

    public void testShortWords() {
        final String[] shortWords = {"it", "is", "at", "in", "on", "to", "of", "by"};

        for (String word : shortWords) {
            mEditText.setText("");
            gesture(word);
            assertTrue("Failed to recognize short word: " + word,
                      isWordInTopSuggestions(word, TOP_N_SUGGESTIONS));
        }
    }

    public void testMediumWords() {
        final String[] mediumWords = {"hello", "world", "keyboard", "swipe", "gesture",
                                       "typing", "android", "testing"};

        for (String word : mediumWords) {
            mEditText.setText("");
            gesture(word);
            assertTrue("Failed to recognize medium-length word: " + word,
                      isWordInTopSuggestions(word, TOP_N_SUGGESTIONS));
        }
    }

    public void testLongWords() {
        final String[] longWords = {"understand", "international", "communication", "development"};

        for (String word : longWords) {
            mEditText.setText("");
            gesture(word);
            assertTrue("Failed to recognize long word: " + word,
                      isWordInTopSuggestions(word, TOP_N_SUGGESTIONS));
        }
    }

    // ==================== PERFORMANCE TESTS ====================

    public void testGesturePerformanceShortWord() {
        mEditText.setText("");
        final long timeTaken = gestureWithTiming("the");
        assertTrue("Gesture took too long: " + timeTaken + "ms (max: " +
                  MAX_GESTURE_PROCESSING_TIME_MS + "ms)",
                  timeTaken < MAX_GESTURE_PROCESSING_TIME_MS);
    }

    public void testGesturePerformanceMediumWord() {
        mEditText.setText("");
        final long timeTaken = gestureWithTiming("hello");
        assertTrue("Gesture took too long: " + timeTaken + "ms (max: " +
                  MAX_GESTURE_PROCESSING_TIME_MS + "ms)",
                  timeTaken < MAX_GESTURE_PROCESSING_TIME_MS);
    }

    public void testGesturePerformanceLongWord() {
        mEditText.setText("");
        final long timeTaken = gestureWithTiming("international");
        assertTrue("Gesture took too long: " + timeTaken + "ms (max: " +
                  MAX_GESTURE_PROCESSING_TIME_MS + "ms)",
                  timeTaken < MAX_GESTURE_PROCESSING_TIME_MS);
    }

    public void testMultipleConsecutiveGesturesPerformance() {
        mEditText.setText("");
        final String[] words = {"hello", "world", "this", "is", "a", "test"};

        for (String word : words) {
            final long timeTaken = gestureWithTiming(word);
            assertTrue("Consecutive gesture took too long for word '" + word + "': " +
                      timeTaken + "ms", timeTaken < MAX_GESTURE_PROCESSING_TIME_MS);
        }
    }

    // ==================== EDGE CASE TESTS ====================

    public void testCurvedSwipePath() {
        mEditText.setText("");
        gestureCurvedPath("hello");
        assertTrue("Failed to recognize word with curved swipe path",
                  isWordInTopSuggestions("hello", TOP_N_SUGGESTIONS));
    }

    public void testSimilarPaths() {
        // Words with similar swipe paths should be distinguishable
        mEditText.setText("");
        gesture("was");
        final String result1 = mEditText.getText().toString();

        mEditText.setText("");
        gesture("war");
        final String result2 = mEditText.getText().toString();

        assertFalse("Similar paths produced identical results",
                   result1.equals(result2));
    }

    public void testAdjacentKeys() {
        // Test words with adjacent keys that might be confused
        final String[] adjacentKeyWords = {"qwerty", "asdf", "zxcv"};

        for (String word : adjacentKeyWords) {
            mEditText.setText("");
            gesture(word);
            assertTrue("Failed to recognize word with adjacent keys: " + word,
                      isWordInTopSuggestions(word, TOP_N_SUGGESTIONS));
        }
    }

    public void testRepeatedLetters() {
        final String[] wordsWithRepeats = {"book", "moon", "door", "feed"};

        for (String word : wordsWithRepeats) {
            mEditText.setText("");
            gesture(word);
            assertTrue("Failed to recognize word with repeated letters: " + word,
                      isWordInTopSuggestions(word, TOP_N_SUGGESTIONS));
        }
    }

    // ==================== CONTEXT AWARENESS TESTS ====================

    public void testContextAwareSuggestions() {
        // Type context, then gesture
        mEditText.setText("");
        type("The cat sat on the ");
        gesture("mat");
        assertTrue("Failed to use context for prediction",
                  isWordInTopSuggestions("mat", TOP_N_SUGGESTIONS));
    }

    public void testSentenceBeginningCapitalization() {
        mEditText.setText("");
        gesture("hello");
        final String result = mEditText.getText().toString();
        assertTrue("First word should be capitalized",
                  Character.isUpperCase(result.charAt(0)));
    }

    public void testMidSentenceGesture() {
        mEditText.setText("");
        type("I am going to the ");
        gesture("store");
        assertTrue("Failed mid-sentence gesture",
                  mEditText.getText().toString().contains("store"));
    }

    // ==================== CORRECTION AND DISAMBIGUATION TESTS ====================

    public void testGestureAfterTyping() {
        mEditText.setText("");
        type("hello ");
        gesture("world");
        assertEquals("Mixed typing and gesture failed", "hello world",
                    mEditText.getText().toString());
    }

    public void testTypingAfterGesture() {
        mEditText.setText("");
        gesture("hello");
        type(" world");
        assertTrue("Typing after gesture failed",
                  mEditText.getText().toString().contains("hello"));
        assertTrue("Typing after gesture failed",
                  mEditText.getText().toString().contains("world"));
    }

    public void testBackspaceAfterGesture() {
        mEditText.setText("");
        gesture("hello");
        final String afterGesture = mEditText.getText().toString();
        type(Constants.CODE_DELETE);
        final String afterBackspace = mEditText.getText().toString();

        assertTrue("Backspace should delete content after gesture",
                  afterBackspace.length() < afterGesture.length());
    }

    public void testGestureRejectionAndRetry() {
        mEditText.setText("");
        gesture("hello");
        type(Constants.CODE_DELETE); // Reject the suggestion
        assertEquals("Rejection should clear the gesture result", "",
                    mEditText.getText().toString());

        // Try gesturing again - should work
        gesture("hello");
        assertTrue("Retry after rejection failed",
                  mEditText.getText().toString().length() > 0);
    }

    // ==================== MULTI-WORD GESTURE TESTS ====================

    public void testMultipleConsecutiveGestures() {
        mEditText.setText("");
        gesture("hello");
        gesture("world");
        gesture("testing");

        final String result = mEditText.getText().toString();
        assertTrue("Multi-gesture should contain first word", result.contains("hello"));
        assertTrue("Multi-gesture should contain second word", result.contains("world"));
        assertTrue("Multi-gesture should contain third word", result.contains("testing"));
    }

    public void testGestureSpacing() {
        mEditText.setText("");
        gesture("hello");
        gesture("world");

        final String result = mEditText.getText().toString();
        assertTrue("Consecutive gestures should have space between words",
                  result.contains(" "));
    }

    // ==================== ERROR HANDLING TESTS ====================

    public void testVeryShortGesture() {
        // Gesture with just 2 points should still work
        mEditText.setText("");
        gesture("it");
        assertTrue("Very short gesture failed",
                  mEditText.getText().toString().length() > 0);
    }

    public void testEmptyGestureHandling() {
        // Test that invalid gestures don't crash
        mEditText.setText("");
        try {
            mLatinIMELegacy.onStartBatchInput();
            final InputPointers emptyPointers = new InputPointers(10);
            mLatinIMELegacy.onEndBatchInput(emptyPointers);
            sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
            runMessages();
            // Should not crash
            assertTrue("Empty gesture handling failed", true);
        } catch (Exception e) {
            fail("Empty gesture caused exception: " + e.getMessage());
        }
    }

    // ==================== COMPARISON WITH TYPING TESTS ====================

    public void testGestureVsTypingSameWord() {
        // Gesture and typing should produce similar results
        mEditText.setText("");
        gesture("hello");
        final String gestureResult = mEditText.getText().toString();

        mEditText.setText("");
        type("hello");
        final String typeResult = mEditText.getText().toString();

        // Results should be similar (may have different capitalization or corrections)
        assertTrue("Gesture and typing should produce similar results",
                  gestureResult.toLowerCase().contains("hello") &&
                  typeResult.toLowerCase().contains("hello"));
    }

    // ==================== DICTIONARY INTEGRATION TESTS ====================

    public void testGestureWithUncommonWord() {
        // Test that uncommon but valid dictionary words work
        final String[] uncommonWords = {"zenith", "quartz", "azure"};

        for (String word : uncommonWords) {
            mEditText.setText("");
            gesture(word);
            // Should at least produce some suggestion
            assertTrue("No suggestion for uncommon word: " + word,
                      mEditText.getText().toString().length() > 0);
        }
    }

    // ==================== LANGUAGE-SPECIFIC TESTS ====================

    public void testGestureEnglishLocale() {
        changeLanguage("en_US");
        mEditText.setText("");
        gesture("hello");
        assertTrue("English gesture failed",
                  isWordInTopSuggestions("hello", TOP_N_SUGGESTIONS));
    }

    // ==================== STRESS TESTS ====================

    public void testRapidConsecutiveGestures() {
        // Test system can handle rapid gestures without lag
        mEditText.setText("");
        final String[] words = {"the", "quick", "brown", "fox", "jumps"};

        for (String word : words) {
            gesture(word);
            // No sleep between gestures - test system under stress
        }

        final String result = mEditText.getText().toString();
        assertTrue("Rapid gestures should produce output", result.length() > 0);
    }

    // ==================== SUGGESTION QUALITY TESTS ====================

    public void testTopSuggestionAccuracy() {
        // The first suggestion should be the most accurate for common words
        final String[] words = {"hello", "world", "testing", "keyboard"};
        int correctTopSuggestions = 0;

        for (String word : words) {
            mEditText.setText("");
            gesture(word);
            final SuggestedWords suggestions = mLatinIMELegacy.getSuggestedWordsForTest();
            if (suggestions != null && suggestions.size() > 0 &&
                word.equalsIgnoreCase(suggestions.getWord(0))) {
                correctTopSuggestions++;
            }
        }

        // At least 75% of top suggestions should be correct for common words
        final double accuracy = (double) correctTopSuggestions / words.length;
        assertTrue("Top suggestion accuracy too low: " + (accuracy * 100) + "%",
                  accuracy >= 0.75);
    }

    public void testSuggestionDiversity() {
        // Test that suggestions offer diverse alternatives
        mEditText.setText("");
        gesture("test");

        final SuggestedWords suggestions = mLatinIMELegacy.getSuggestedWordsForTest();
        assertTrue("Should provide multiple suggestions",
                  suggestions != null && suggestions.size() >= 2);

        // Check suggestions are different
        if (suggestions.size() >= 2) {
            assertFalse("Suggestions should be different",
                       suggestions.getWord(0).equals(suggestions.getWord(1)));
        }
    }
}
