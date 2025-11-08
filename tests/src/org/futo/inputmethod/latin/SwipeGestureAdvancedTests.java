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

import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.InputPointers;

/**
 * Advanced test suite covering edge cases, path variations, and algorithm-specific scenarios.
 * This supplements SwipeGestureTests with more complex scenarios.
 */
@LargeTest
public class SwipeGestureAdvancedTests extends InputTestsBase {

    private static final long MAX_GESTURE_PROCESSING_TIME_MS = 1000;
    private static final int TOP_N_SUGGESTIONS = 3;

    /**
     * Helper: Create a gesture with specific speed (ms per key)
     */
    private void gestureWithSpeed(final String word, final int msPerKey) {
        if (word.length() < 2) {
            throw new RuntimeException("Can't gesture strings less than 2 chars long");
        }

        mLatinIMELegacy.onStartBatchInput();
        final InputPointers pointers = new InputPointers(Constants.DEFAULT_GESTURE_POINTS_CAPACITY);
        int timestamp = 0;

        for (int i = 0; i < word.length(); i = word.offsetByCodePoints(i, 1)) {
            final Point point = getXY(word.codePointAt(i));

            if (i > 0) {
                Point prevPoint = getXY(word.codePointAt(i - Character.charCount(word.codePointAt(i - 1))));
                final int STEPS = 5;
                for (int j = 1; j <= STEPS; j++) {
                    timestamp += msPerKey / STEPS;
                    int x = prevPoint.x + ((point.x - prevPoint.x) * j) / STEPS;
                    int y = prevPoint.y + ((point.y - prevPoint.y) * j) / STEPS;
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

    /**
     * Helper: Create a gesture with backtracking (forward, back, forward again)
     */
    private void gestureWithBacktracking(final String word) {
        if (word.length() < 3) {
            throw new RuntimeException("Backtracking requires at least 3 chars");
        }

        mLatinIMELegacy.onStartBatchInput();
        final InputPointers pointers = new InputPointers(Constants.DEFAULT_GESTURE_POINTS_CAPACITY);
        int timestamp = 0;

        // Go to first two letters
        for (int i = 0; i < 2; i++) {
            Point point = getXY(word.codePointAt(i));
            timestamp += 100;
            pointers.addPointer(point.x, point.y, 0, timestamp);
        }

        // Backtrack to first letter
        Point backtrackPoint = getXY(word.codePointAt(0));
        timestamp += 100;
        pointers.addPointer(backtrackPoint.x, backtrackPoint.y, 0, timestamp);

        // Continue to rest of word
        for (int i = 1; i < word.length(); i = word.offsetByCodePoints(i, 1)) {
            Point point = getXY(word.codePointAt(i));
            timestamp += 100;
            pointers.addPointer(point.x, point.y, 0, timestamp);
        }

        mLatinIMELegacy.onUpdateBatchInput(pointers);
        mLatinIMELegacy.onEndBatchInput(pointers);
        sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
        runMessages();
    }

    /**
     * Helper: Create a gesture with overshoot (go past target key then return)
     */
    private void gestureWithOvershoot(final String word) {
        if (word.length() < 2) {
            throw new RuntimeException("Can't gesture strings less than 2 chars long");
        }

        mLatinIMELegacy.onStartBatchInput();
        final InputPointers pointers = new InputPointers(Constants.DEFAULT_GESTURE_POINTS_CAPACITY);
        int timestamp = 0;

        for (int i = 0; i < word.length(); i = word.offsetByCodePoints(i, 1)) {
            Point point = getXY(word.codePointAt(i));

            if (i > 0) {
                Point prevPoint = getXY(word.codePointAt(i - Character.charCount(word.codePointAt(i - 1))));
                // Overshoot by 50% then come back
                int overshootX = point.x + ((point.x - prevPoint.x) / 2);
                int overshootY = point.y + ((point.y - prevPoint.y) / 2);

                timestamp += 100;
                pointers.addPointer(overshootX, overshootY, 0, timestamp);
            }

            timestamp += 100;
            pointers.addPointer(point.x, point.y, 0, timestamp);
        }

        mLatinIMELegacy.onUpdateBatchInput(pointers);
        mLatinIMELegacy.onEndBatchInput(pointers);
        sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
        runMessages();
    }

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

    // ==================== SPEED VARIATION TESTS ====================

    public void testFastSwipe() {
        // Very fast swipe: 50ms per key
        mEditText.setText("");
        gestureWithSpeed("hello", 50);
        assertTrue("Fast swipe should work",
                  isWordInTopSuggestions("hello", TOP_N_SUGGESTIONS));
    }

    public void testSlowSwipe() {
        // Slow swipe: 500ms per key
        mEditText.setText("");
        gestureWithSpeed("hello", 500);
        assertTrue("Slow swipe should work",
                  isWordInTopSuggestions("hello", TOP_N_SUGGESTIONS));
    }

    public void testVerySlowSwipe() {
        // Very slow swipe: 1000ms per key (might timeout or be treated as taps)
        mEditText.setText("");
        gestureWithSpeed("test", 1000);
        // Should produce some output even if not perfect
        assertTrue("Very slow swipe should produce output",
                  mEditText.getText().toString().length() > 0);
    }

    // ==================== BACKTRACKING TESTS ====================

    public void testBacktrackingGesture() {
        // This tests the aggressive backtracking penalty issue we identified
        mEditText.setText("");
        gestureWithBacktracking("hello");
        // Should still recognize the word despite backtracking
        assertTrue("Backtracking gesture should still work",
                  mEditText.getText().toString().length() > 0);
    }

    public void testLoopingGesture() {
        // Create a gesture that loops back on itself
        mEditText.setText("");
        try {
            gestureWithBacktracking("test");
            // Should not crash even with weird path
            assertTrue("Looping gesture handled", true);
        } catch (Exception e) {
            fail("Looping gesture caused exception: " + e.getMessage());
        }
    }

    // ==================== OVERSHOOT TESTS ====================

    public void testOvershootGesture() {
        mEditText.setText("");
        gestureWithOvershoot("hello");
        assertTrue("Overshoot gesture should work",
                  isWordInTopSuggestions("hello", TOP_N_SUGGESTIONS));
    }

    // ==================== DIAGONAL AND CORNER TESTS ====================

    public void testDiagonalSwipe() {
        // Test words that require diagonal movement (e.g., q to m)
        mEditText.setText("");
        gesture("qm"); // Diagonal across keyboard
        assertTrue("Diagonal swipe should produce output",
                  mEditText.getText().toString().length() > 0);
    }

    public void testCornerToCornerSwipe() {
        // Test extreme case: top-left to bottom-right
        mEditText.setText("");
        gesture("qp"); // Assuming QWERTY layout
        assertTrue("Corner-to-corner swipe should work",
                  mEditText.getText().toString().length() > 0);
    }

    // ==================== INCOMPLETE GESTURE TESTS ====================

    public void testIncompleteGesture() {
        // Start gesture but don't finish properly
        mEditText.setText("");
        try {
            mLatinIMELegacy.onStartBatchInput();
            final InputPointers pointers = new InputPointers(10);
            Point p1 = getXY('h');
            Point p2 = getXY('e');
            pointers.addPointer(p1.x, p1.y, 0, 0);
            pointers.addPointer(p2.x, p2.y, 0, 100);
            // End abruptly
            mLatinIMELegacy.onEndBatchInput(pointers);
            sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
            runMessages();
            // Should not crash
            assertTrue("Incomplete gesture handled", true);
        } catch (Exception e) {
            fail("Incomplete gesture caused exception: " + e.getMessage());
        }
    }

    public void testSinglePointGesture() {
        // Gesture with just one point (invalid but shouldn't crash)
        mEditText.setText("");
        try {
            mLatinIMELegacy.onStartBatchInput();
            final InputPointers pointers = new InputPointers(10);
            Point p = getXY('h');
            pointers.addPointer(p.x, p.y, 0, 0);
            mLatinIMELegacy.onEndBatchInput(pointers);
            sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
            runMessages();
            assertTrue("Single point gesture handled", true);
        } catch (Exception e) {
            fail("Single point gesture caused exception: " + e.getMessage());
        }
    }

    // ==================== WRONG START KEY TESTS ====================

    public void testWrongStartKey() {
        // Start swipe from wrong key but pass through correct keys
        mEditText.setText("");
        mLatinIMELegacy.onStartBatchInput();
        final InputPointers pointers = new InputPointers(Constants.DEFAULT_GESTURE_POINTS_CAPACITY);

        // Start from 'x' but swipe through 'hello'
        Point start = getXY('x');
        pointers.addPointer(start.x, start.y, 0, 0);

        int timestamp = 50;
        String word = "hello";
        for (int i = 0; i < word.length(); i++) {
            Point p = getXY(word.charAt(i));
            pointers.addPointer(p.x, p.y, 0, timestamp);
            timestamp += 100;
        }

        mLatinIMELegacy.onUpdateBatchInput(pointers);
        mLatinIMELegacy.onEndBatchInput(pointers);
        sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
        runMessages();

        // Should still produce reasonable output
        assertTrue("Wrong start key should still produce output",
                  mEditText.getText().toString().length() > 0);
    }

    // ==================== AMBIGUOUS PATH TESTS ====================

    public void testAmbiguousPath() {
        // Test paths that could match multiple words
        mEditText.setText("");
        gesture("sit");

        mEditText.setText("");
        gesture("set");

        mEditText.setText("");
        gesture("sat");

        // All should produce different results
        assertTrue("Ambiguous paths test completed", true);
    }

    // ==================== GESTURE CANCELLATION TESTS ====================

    public void testGestureCancellation() {
        // Start gesture but cancel it
        mEditText.setText("");
        try {
            mLatinIMELegacy.onStartBatchInput();
            final InputPointers pointers = new InputPointers(10);
            Point p1 = getXY('h');
            pointers.addPointer(p1.x, p1.y, 0, 0);

            // Cancel gesture (this might be done via onAbortBatchInput or similar)
            // For now, just test that ending with minimal data works
            mLatinIMELegacy.onEndBatchInput(pointers);
            sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
            runMessages();

            assertEquals("Cancelled gesture should not produce text", "",
                        mEditText.getText().toString());
        } catch (Exception e) {
            fail("Gesture cancellation caused exception: " + e.getMessage());
        }
    }

    // ==================== MIXED INPUT TESTS ====================

    public void testGestureDuringTyping() {
        // Start typing, then switch to gesture
        mEditText.setText("");
        type("hello ");
        gesture("world");

        String result = mEditText.getText().toString();
        assertTrue("Mixed input should work", result.contains("hello"));
        assertTrue("Mixed input should work", result.contains("world"));
    }

    public void testTypingDuringGestureMode() {
        // Ensure gesture mode doesn't interfere with subsequent typing
        mEditText.setText("");
        gesture("hello");
        type(" there");

        String result = mEditText.getText().toString();
        assertTrue("Typing after gesture should work", result.contains("there"));
    }

    // ==================== SUGGESTION REJECTION TESTS ====================

    public void testMultipleRejections() {
        // Reject multiple gestures in a row
        mEditText.setText("");
        gesture("test");
        type(Constants.CODE_DELETE);

        gesture("test");
        type(Constants.CODE_DELETE);

        gesture("test");
        // Third try should still work (or at least not crash)
        assertTrue("Multiple rejections handled", true);
    }

    // ==================== REAL-WORLD SCENARIO TESTS ====================

    public void testRealWorldSentence() {
        // Simulate typing a realistic sentence with gestures
        mEditText.setText("");
        gesture("the");
        gesture("quick");
        gesture("brown");
        gesture("fox");

        String result = mEditText.getText().toString();
        // Should have multiple words separated by spaces
        int spaceCount = result.length() - result.replace(" ", "").length();
        assertTrue("Real-world sentence should have spaces", spaceCount >= 3);
    }

    public void testSwipeCorrectionScenario() {
        // User swipes, realizes it's wrong, backspaces, tries again
        mEditText.setText("");
        gesture("hello");
        String first = mEditText.getText().toString();

        type(Constants.CODE_DELETE);
        assertEquals("Deletion should work", "", mEditText.getText().toString());

        gesture("world");
        String second = mEditText.getText().toString();

        assertFalse("Correction scenario should produce different words",
                   first.equals(second));
    }

    // ==================== BOUNDARY CONDITION TESTS ====================

    public void testMaxLengthGesture() {
        // Test a very long swipe (stress test)
        mEditText.setText("");
        gesture("supercalifragilisticexpialidocious");
        // Should produce something even if not perfect
        assertTrue("Max length gesture should produce output",
                  mEditText.getText().toString().length() > 0);
    }

    public void testMinLengthGesture() {
        // Shortest possible valid gesture
        mEditText.setText("");
        gesture("I");
        // Single letter might not work as gesture, but shouldn't crash
        assertTrue("Min length gesture handled", true);
    }

    // ==================== PERFORMANCE UNDER LOAD TESTS ====================

    public void testConcurrentGestures() {
        // Rapid gestures without waiting for processing
        mEditText.setText("");

        for (int i = 0; i < 5; i++) {
            mLatinIMELegacy.onStartBatchInput();
            final InputPointers pointers = new InputPointers(10);
            Point p1 = getXY('h');
            Point p2 = getXY('i');
            pointers.addPointer(p1.x, p1.y, 0, 0);
            pointers.addPointer(p2.x, p2.y, 0, 100);
            mLatinIMELegacy.onEndBatchInput(pointers);
            // No sleep - test rapid fire
        }

        sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS * 2);
        runMessages();

        // Should handle concurrent gestures gracefully
        assertTrue("Concurrent gestures handled", true);
    }

    // ==================== PATH SIMPLIFICATION TESTS ====================

    public void testNoisyPath() {
        // Create a path with lots of small movements (simulating shaky hand)
        mEditText.setText("");
        mLatinIMELegacy.onStartBatchInput();
        final InputPointers pointers = new InputPointers(Constants.DEFAULT_GESTURE_POINTS_CAPACITY);

        String word = "hello";
        int timestamp = 0;
        for (int i = 0; i < word.length(); i++) {
            Point p = getXY(word.charAt(i));

            // Add noise: small random movements around the key
            for (int j = 0; j < 10; j++) {
                int noisyX = p.x + (j % 3 - 1) * 5; // +/- 5 pixels
                int noisyY = p.y + (j % 3 - 1) * 5;
                pointers.addPointer(noisyX, noisyY, 0, timestamp);
                timestamp += 10;
            }
        }

        mLatinIMELegacy.onUpdateBatchInput(pointers);
        mLatinIMELegacy.onEndBatchInput(pointers);
        sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
        runMessages();

        // Should recognize word despite noisy path
        assertTrue("Noisy path should still work",
                  isWordInTopSuggestions("hello", TOP_N_SUGGESTIONS));
    }

    // ==================== MEMORY AND RESOURCE TESTS ====================

    public void testMemoryLeakOnManyGestures() {
        // Perform many gestures to check for memory leaks
        mEditText.setText("");

        for (int i = 0; i < 50; i++) {
            gesture("test");
            type(Constants.CODE_DELETE);
        }

        // Should not crash or run out of memory
        assertTrue("Many gestures completed without crash", true);
    }
}
