package org.futo.inputmethod.latin.inputlogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.futo.inputmethod.engine.InputMethodConnectionProvider;
import org.futo.inputmethod.latin.RichInputConnection;
import org.futo.inputmethod.latin.WordComposer;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CursorWordEditTests {
    @Test
    @UiThreadTest
    public void editorAndSuggestionComposerKeepBothSidesOfAnEditedWord() {
        final EditText editor = new EditText(InstrumentationRegistry.getTargetContext());
        editor.setText("fork next");
        editor.setSelection(3);
        final EditorInfo info = new EditorInfo();
        final InputConnection target = new InputConnectionWrapper(
                editor.onCreateInputConnection(info), false) {
            @Override
            public ExtractedText getExtractedText(final ExtractedTextRequest request,
                    final int flags) {
                throw new AssertionError("literal middle edits must not extract the document");
            }

            @Override
            public CharSequence getSelectedText(final int flags) {
                return editor.getSelectionStart() == editor.getSelectionEnd()
                        ? "" : super.getSelectedText(flags);
            }
        };
        final RichInputConnection connection = new RichInputConnection(new InputMethodConnectionProvider() {
            @Override public InputConnection getCurrentInputConnection() { return target; }
            @Override public EditorInfo getCurrentEditorInfo() { return info; }
            @Override public Context getContextForSettings() { return editor.getContext(); }
        });
        connection.resetCachesUponCursorMoveAndReturnSuccess(3, 3, false);
        connection.setComposingRegion(0, 4, "fork");
        connection.send();
        final WordComposer composer = new WordComposer();
        final CursorWordEdit original = new CursorWordEdit("fork", 3);
        editor.setSelection(3, 4);
        assertNull(original.apply(connection, composer, null, CursorWordEditTests::coordinates));
        assertEquals("fork next", editor.getText().toString());
        editor.setSelection(3);
        final CursorWordEdit afterDelete = original.apply(
                connection, composer, null, CursorWordEditTests::coordinates);
        assertNotNull(afterDelete);
        assertEquals("fok next", editor.getText().toString());
        assertEquals(2, editor.getSelectionStart());
        assertEquals("fok", composer.getTypedWord());
        assertEquals(2, composer.sizeBeforeCursor());
        final CursorWordEdit afterInsert = afterDelete.apply(connection, composer, "n",
                CursorWordEditTests::coordinates);
        assertNotNull(afterInsert);
        assertEquals("fonk next", editor.getText().toString());
        assertEquals(3, editor.getSelectionStart());
        assertEquals("fonk", composer.getTypedWord());
        assertEquals(3, composer.sizeBeforeCursor());
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.getText()));

        // The editor already moved, but has not sent a selection callback. Do not delete along
        // an old range or assume the UTF-16 length of the character before the new caret.
        editor.setSelection(7);
        assertNull(afterInsert.apply(connection, composer, null, CursorWordEditTests::coordinates));
        assertEquals("fonk next", editor.getText().toString());
        assertEquals(7, editor.getSelectionStart());
    }

    @Test
    @UiThreadTest
    public void editorHandlesDeleteWhenSelectionTextIsUnavailable() {
        final EditText editor = new EditText(InstrumentationRegistry.getTargetContext());
        editor.setText("fork next");
        editor.setSelection(3);
        final EditorInfo info = new EditorInfo();
        final InputConnection target = new InputConnectionWrapper(
                editor.onCreateInputConnection(info), false) {
            @Override
            public CharSequence getSelectedText(final int flags) {
                return null;
            }

            @Override
            public boolean sendKeyEvent(final KeyEvent event) {
                // The instrumentation fixture has no window. Hand the fallback key to the real editor.
                return editor.dispatchKeyEvent(event);
            }
        };
        final RichInputConnection connection = new RichInputConnection(new InputMethodConnectionProvider() {
            @Override public InputConnection getCurrentInputConnection() { return target; }
            @Override public EditorInfo getCurrentEditorInfo() { return info; }
            @Override public Context getContextForSettings() { return editor.getContext(); }
        });
        connection.resetCachesUponCursorMoveAndReturnSuccess(3, 3, false);
        connection.setComposingRegion(0, 4, "fork");
        connection.send();
        editor.setSelection(3, 4);

        new CursorWordEdit("fork", 3).apply(connection, new WordComposer(), null,
                CursorWordEditTests::coordinates);

        assertEquals("for next", editor.getText().toString());
        assertEquals(3, editor.getSelectionStart());
        assertEquals(3, editor.getSelectionEnd());
    }

    @Test
    @UiThreadTest
    public void insertionAfterEditorMovedDoesNotReplaceTheReopenedWord() {
        final EditText editor = new EditText(InstrumentationRegistry.getTargetContext());
        editor.setText("fork next");
        editor.setSelection(3);
        final EditorInfo info = new EditorInfo();
        final InputConnection target = editor.onCreateInputConnection(info);
        final RichInputConnection connection = new RichInputConnection(new InputMethodConnectionProvider() {
            @Override public InputConnection getCurrentInputConnection() { return target; }
            @Override public EditorInfo getCurrentEditorInfo() { return info; }
            @Override public Context getContextForSettings() { return editor.getContext(); }
        });
        connection.resetCachesUponCursorMoveAndReturnSuccess(3, 3, false);
        connection.setComposingRegion(0, 4, "fork");
        connection.send();

        editor.setSelection(7);
        new CursorWordEdit("fork", 3).apply(connection, new WordComposer(), "n",
                CursorWordEditTests::coordinates);
        assertEquals("fork nenxt", editor.getText().toString());
        assertEquals(8, editor.getSelectionStart());
    }

    private static int[] coordinates(final int[] codePoints) {
        return new int[codePoints.length * 2];
    }
    @Test
    public void editsInMiddleOfWordWithoutMovingSuffix() {
        final CursorWordEdit original = new CursorWordEdit("fork", 3);
        final CursorWordEdit deleted = original.deleteBeforeCaret();
        assertEquals("fok", deleted.getWord());
        assertEquals(2, deleted.getCaretOffset());
        assertEquals(1, original.getCaretOffset() - deleted.getCaretOffset());

        final CursorWordEdit inserted = deleted.insert("n");
        assertEquals("fonk", inserted.getWord());
        assertEquals(3, inserted.getCaretOffset());
        assertEquals("fork", original.getWord());
        assertEquals(3, original.getCaretOffset());
    }

    @Test
    public void deleteAtWordStartReturnsSameState() {
        final CursorWordEdit original = new CursorWordEdit("fork", 0);
        assertSame(original, original.deleteBeforeCaret());
    }

    @Test
    public void supplementaryLetterUsesTwoUtf16Units() {
        final String letter = "\uD801\uDC00";
        final CursorWordEdit original = new CursorWordEdit("a" + letter + "b", 3);
        final CursorWordEdit deleted = original.deleteBeforeCaret();
        assertEquals("ab", deleted.getWord());
        assertEquals(1, deleted.getCaretOffset());
        assertEquals(2, original.getCaretOffset() - deleted.getCaretOffset());

        final CursorWordEdit inserted = deleted.insert(new StringBuilder(letter));
        assertEquals("a" + letter + "b", inserted.getWord());
        assertEquals(3, inserted.getCaretOffset());
        assertEquals(2, inserted.getCaretOffset() - deleted.getCaretOffset());
    }

    @Test
    public void deleteRemovesBaseLetterWithCombiningMark() {
        final CursorWordEdit original = new CursorWordEdit("ae\u0301b", 3);
        final CursorWordEdit deleted = original.deleteBeforeCaret();
        assertEquals("ab", deleted.getWord());
        assertEquals(1, deleted.getCaretOffset());
    }
}
