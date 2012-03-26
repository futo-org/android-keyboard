/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;

import com.android.inputmethod.latin.ResearchLogger.LogFileManager;

import java.io.FileNotFoundException;

public class ResearchLoggerTests extends InputTestsBase {

    private static final String TAG = ResearchLoggerTests.class.getSimpleName();
    private static final int TEST_INT = 0x12345678;
    private static final long TEST_LONG = 0x1234567812345678L;

    private static ResearchLogger sLogger;
    private MockLogFileManager mMockLogFileManager;

    @Override
    protected void setUp() {
        super.setUp();
        sLogger = ResearchLogger.getInstance();
        mMockLogFileManager = new MockLogFileManager();
        sLogger.setLogFileManager(mMockLogFileManager);
        ResearchLogger.sIsLogging = true;
    }

    public static class MockLogFileManager extends LogFileManager {
        private final StringBuilder mContents = new StringBuilder();

        @Override
        public void init(InputMethodService ims) {
        }

        @Override
        public synchronized void createLogFile() {
            mContents.setLength(0);
        }

        @Override
        public synchronized void createLogFile(String dir, String filename)
                throws FileNotFoundException {
            mContents.setLength(0);
        }

        @Override
        public synchronized boolean append(String s) {
            mContents.append(s);
            return true;
        }

        @Override
        public synchronized void reset() {
            mContents.setLength(0);
        }

        @Override
        public synchronized void close() {
            mContents.setLength(0);
        }

        private String getAppendedString() {
            return mContents.toString();
        }
    }

    private void waitOnResearchLogger() {
        // post another Runnable that notify()'s the test that it may proceed.
        // assumes that the MessageQueue is processed in-order
        Handler handler = sLogger.mLoggingHandler;
        handler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (ResearchLoggerTests.this) {
                    ResearchLoggerTests.this.notify();
                }
            }
        });
        synchronized (this) {
            try {
                wait();
            } catch (InterruptedException e) {
                Log.i(TAG, "interrupted when waiting for handler to finish.", e);
            }
        }
    }

    /*********************** Tests *********************/
    public void testLogStartsEmpty() {
        waitOnResearchLogger();
        String result = mMockLogFileManager.getAppendedString();
        assertEquals(result, "");
    }

    public void testMotionEvent() {
        // verify that input values appear somewhere in output
        sLogger.logMotionEvent(MotionEvent.ACTION_CANCEL,
                TEST_LONG, TEST_INT, 1111, 3333, 5555, 7777);
        waitOnResearchLogger();
        String output = mMockLogFileManager.getAppendedString();
        assertTrue(output.matches("(?sui).*\\bcancel\\b.*"));
        assertFalse(output.matches("(?sui).*\\bdown\\b.*"));
        assertTrue(output.matches("(?s).*\\b" + TEST_LONG + "\\b.*"));
        assertTrue(output.matches("(?s).*\\b" + TEST_INT + "\\b.*"));
        assertTrue(output.matches("(?s).*\\b1111\\b.*"));
        assertTrue(output.matches("(?s).*\\b3333\\b.*"));
        assertTrue(output.matches("(?s).*\\b5555\\b.*"));
        assertTrue(output.matches("(?s).*\\b7777\\b.*"));
    }

    public void testKeyEvent() {
        type("abc");
        waitOnResearchLogger();
        String output = mMockLogFileManager.getAppendedString();
        assertTrue(output.matches("(?s).*\\ba\\b.*"));
        assertTrue(output.matches("(?s).*\\bb\\b.*"));
        assertTrue(output.matches("(?s).*\\bc\\b.*"));
    }

    public void testCorrection() {
        sLogger.logCorrection("aaaa", "thos", "this", 1);
        waitOnResearchLogger();
        String output = mMockLogFileManager.getAppendedString();
        assertTrue(output.matches("(?sui).*\\baaaa\\b.*"));
        assertTrue(output.matches("(?sui).*\\bthos\\b.*"));
        assertTrue(output.matches("(?sui).*\\bthis\\b.*"));
    }

    public void testStateChange() {
        sLogger.logStateChange("aaaa", "bbbb");
        waitOnResearchLogger();
        String output = mMockLogFileManager.getAppendedString();
        assertTrue(output.matches("(?sui).*\\baaaa\\b.*"));
        assertTrue(output.matches("(?sui).*\\bbbbb\\b.*"));
    }

    // TODO: add integration tests that start at point of event generation.
}
