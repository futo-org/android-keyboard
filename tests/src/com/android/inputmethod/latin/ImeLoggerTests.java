package com.android.inputmethod.latin;

import android.test.ServiceTestCase;

public class ImeLoggerTests extends ServiceTestCase<LatinIME> {

    private static final String WORD_SEPARATORS
            = ".\u0009\u0020,;:!?\n()[]*&@{}<>;_+=|\\u0022";

    public ImeLoggerTests() {
        super(LatinIME.class);
    }
    static LatinImeLogger sLogger;
    @Override
    protected void setUp() {
        try {
            super.setUp();
        } catch (Exception e) {
            e.printStackTrace();
        }
        setupService();
        // startService(null); // can't be started because VoiceInput can't be found.
        final LatinIME context = getService();
        context.mWordSeparators = WORD_SEPARATORS;
        LatinImeLogger.init(context);
        sLogger = LatinImeLogger.sLatinImeLogger;
    }
    /*********************** Tests *********************/
    public void testRingBuffer() {
        for (int i = 0; i < sLogger.mRingCharBuffer.BUFSIZE * 2; ++i) {
            LatinImeLogger.logOnDelete();
        }
        assertEquals("", sLogger.mRingCharBuffer.getLastString());
        LatinImeLogger.logOnInputChar('t');
        LatinImeLogger.logOnInputChar('g');
        LatinImeLogger.logOnInputChar('i');
        LatinImeLogger.logOnInputChar('s');
        LatinImeLogger.logOnInputChar(' ');
        LatinImeLogger.logOnAutoSuggestion("tgis", "this");
        LatinImeLogger.logOnInputChar(' ');
        LatinImeLogger.logOnDelete();
        assertEquals("", sLogger.mRingCharBuffer.getLastString());
        LatinImeLogger.logOnDelete();
        assertEquals("tgis", sLogger.mRingCharBuffer.getLastString());
        assertEquals("tgis", LatinImeLogger.sLastAutoSuggestBefore);
        LatinImeLogger.logOnAutoSuggestionCanceled();
        assertEquals("", LatinImeLogger.sLastAutoSuggestBefore);
        LatinImeLogger.logOnDelete();
        assertEquals("tgi", sLogger.mRingCharBuffer.getLastString());
        for (int i = 0; i < sLogger.mRingCharBuffer.BUFSIZE * 2; ++i) {
            LatinImeLogger.logOnDelete();
        }
        assertEquals("", sLogger.mRingCharBuffer.getLastString());
        for (int i = 0; i < sLogger.mRingCharBuffer.BUFSIZE * 2; ++i) {
            LatinImeLogger.logOnInputChar('a');
        }
        assertEquals(sLogger.mRingCharBuffer.BUFSIZE, sLogger.mRingCharBuffer.length);
    }
}
