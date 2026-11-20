package org.futo.inputmethod.latin;

import static org.junit.Assert.assertEquals;

import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.SurroundingText;

import org.junit.Test;

import kotlin.Pair;

public class InputConnectionUtilTests {
    @Test
    public void usesBothSelectionEndpointsFromExtractedText() {
        assertEquals(new Pair<>(102, 108), InputConnectionUtil.INSTANCE.extractSelection(
                editorWithExtractedSelection(2, 8), 0));
    }

    @Test
    public void doesNotTurnMissingSelectionIntoCaretAtExtractedOffset() {
        assertEquals(new Pair<>(-1, -1), InputConnectionUtil.INSTANCE.extractSelection(
                editorWithExtractedSelection(-1, -1), 0));
    }

    private InputConnection editorWithExtractedSelection(final int start, final int end) {
        return new InputConnectionWrapper(null, false) {
            @Override
            public SurroundingText getSurroundingText(final int beforeLength, final int afterLength,
                    final int flags) {
                return null;
            }

            @Override
            public ExtractedText getExtractedText(final ExtractedTextRequest request, final int flags) {
                final ExtractedText text = new ExtractedText();
                text.text = "first second";
                text.startOffset = 100;
                text.selectionStart = start;
                text.selectionEnd = end;
                return text;
            }
        };
    }
}
