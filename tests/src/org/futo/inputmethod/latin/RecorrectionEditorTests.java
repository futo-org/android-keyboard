package org.futo.inputmethod.latin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SdkSuppress;

import org.futo.inputmethod.engine.InputMethodConnectionProvider;
import org.futo.inputmethod.event.Event;
import org.futo.inputmethod.latin.common.Constants;
import org.junit.Test;

import java.util.Arrays;

import kotlin.Pair;

public class RecorrectionEditorTests {
    @Test
    @UiThreadTest
    @SdkSuppress(maxSdkVersion = 30)
    public void slowSelectionReadIsNotRepeatedWithinSameInput() {
        final EditText editor = new EditText(InstrumentationRegistry.getTargetContext());
        final char[] document = new char[32_768];
        Arrays.fill(document, 'a');
        editor.setText(new String(document) + " alpha bravo");
        final int caret = document.length + 6;
        editor.setSelection(caret);
        final EditorInfo info = new EditorInfo();
        final int[] extractCalls = {0};
        final InputConnection target = new InputConnectionWrapper(
                editor.onCreateInputConnection(info), false) {
            @Override
            public ExtractedText getExtractedText(final ExtractedTextRequest request,
                    final int flags) {
                extractCalls[0]++;
                SystemClock.sleep(250);
                return super.getExtractedText(request, flags);
            }
        };
        final RichInputConnection connection = connect(editor.getContext(), target, info);
        assertEquals(new Pair<>(caret, caret), connection.getExtractedSelection());
        for (int i = 0; i < 4; i++) {
            assertEquals(new Pair<>(-1, -1), connection.getExtractedSelection());
        }
        assertEquals("a slow full-document query must not run on each Backspace", 1,
                extractCalls[0]);
        connection.onStartInput();
        assertEquals(new Pair<>(caret, caret), connection.getExtractedSelection());
        assertEquals(2, extractCalls[0]);
    }

    @Test
    @UiThreadTest
    @SdkSuppress(maxSdkVersion = 30)
    public void abandonedCompositionFinishesEvenIfEditorCannotReloadText() {
        final EditText editor = new EditText(InstrumentationRegistry.getTargetContext());
        editor.setText("alpha bravo");
        editor.setSelection(5);
        final EditorInfo info = new EditorInfo();
        final boolean[] cacheUnavailable = {false};
        final InputConnection target = new InputConnectionWrapper(
                editor.onCreateInputConnection(info), false) {
            @Override
            public CharSequence getTextBeforeCursor(final int n, final int flags) {
                return cacheUnavailable[0] ? null : super.getTextBeforeCursor(n, flags);
            }
        };
        final RichInputConnection connection = connect(editor.getContext(), target, info);
        assertTrue(connection.resetCachesUponCursorMoveAndReturnSuccess(5, 5, false));
        connection.setComposingRegion(0, 5, "alpha");
        assertEquals(0, BaseInputConnection.getComposingSpanStart(editor.getText()));

        editor.setSelection(8);
        cacheUnavailable[0] = true;
        assertFalse(connection.resetCachesUponCursorMoveAndReturnSuccess(8, 8, true));
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.getText()));
        target.commitText("x", 1);
        assertEquals("alpha brxavo", editor.getText().toString());
        assertEquals(9, editor.getSelectionStart());
    }

    @Test
    public void knownComposingWordDoesNotRequireFullDocument() {
        final InputConnection editor = new InputConnectionWrapper(null, false) {
            @Override
            public ExtractedText getExtractedText(final ExtractedTextRequest request,
                    final int flags) {
                throw new AssertionError("reopening known text must not read the document again");
            }
        };
        final InputConnectionInternalComposingWrapper connection =
                new InputConnectionInternalComposingWrapper(false, false, editor);
        assertTrue(connection.setComposingRegionWithText(100, 105, "alpha"));
        assertEquals("alpha", connection.getComposingText());
    }

    @Test
    public void editingAReopenedWordRestoresAutocorrectionEligibility() {
        final WordComposer composer = new WordComposer();
        assertTrue(composer.setComposingWord(new int[] {'t', 'g', 'i', 's', 'x'}, new int[10], true));
        assertTrue("merely opening a word must not autocorrect it", composer.isResumed());

        composer.applyProcessedEvent(composer.processEvent(
                Event.createSoftwareKeypressEvent(Constants.NOT_A_CODE, Constants.CODE_DELETE,
                        0, 0, false)));
        assertEquals("tgis", composer.getTypedWord());
        assertFalse("the user edited the word; do not suppress autocorrection any longer",
                composer.isResumed());
    }

    @Test
    public void editingAReconstructedPrefixDoesNotAutocorrectThePartialWord() {
        final WordComposer composer = new WordComposer();
        assertTrue(composer.setComposingWord(new int[] {'t', 'g', 'i', 's', 'x'}, new int[10], false));
        composer.applyProcessedEvent(composer.processEvent(
                Event.createSoftwareKeypressEvent(Constants.NOT_A_CODE, Constants.CODE_DELETE,
                        0, 0, false)));
        assertEquals("tgis", composer.getTypedWord());
        assertTrue(composer.isResumed());
    }

    @Test
    @UiThreadTest
    public void abandoningBufferedNativeRegionCannotReplaceTheOldWord() {
        final EditText editor = new EditText(InstrumentationRegistry.getTargetContext());
        editor.setText("alpha bravo");
        editor.setSelection(5);
        final InputConnectionInternalComposingWrapper connection =
                new InputConnectionInternalComposingWrapper(true, true,
                        editor.onCreateInputConnection(new EditorInfo()));
        connection.setComposingRegionWithText(0, 5, "alpha");

        editor.setSelection(8);
        connection.abandonComposingText();
        connection.send();
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.getText()));
        connection.commitText("x", 1);
        connection.send();
        assertEquals("alpha brxavo", editor.getText().toString());
        assertEquals(9, editor.getSelectionStart());
    }

    private static RichInputConnection connect(final Context context, final InputConnection target,
            final EditorInfo info) {
        return new RichInputConnection(new InputMethodConnectionProvider() {
            @Override public InputConnection getCurrentInputConnection() { return target; }
            @Override public EditorInfo getCurrentEditorInfo() { return info; }
            @Override public Context getContextForSettings() { return context; }
        });
    }
}
