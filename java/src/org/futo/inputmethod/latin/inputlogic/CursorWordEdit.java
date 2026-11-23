package org.futo.inputmethod.latin.inputlogic;

import android.os.Build;
import android.text.TextUtils;
import android.view.inputmethod.SurroundingText;

import org.futo.inputmethod.latin.RichInputConnection;
import org.futo.inputmethod.latin.WordComposer;
import org.futo.inputmethod.latin.common.StringUtils;

import java.text.BreakIterator;
import java.util.function.Function;

/** An immutable token and its UTF-16 caret offset. Editor edit lengths are the differences between the old and new offsets. */
final class CursorWordEdit {
    private final String mWord;
    private final int mCaretOffset;

    CursorWordEdit(final String word, final int utf16CaretOffset) {
        if (utf16CaretOffset < 0 || utf16CaretOffset > word.length()) {
            throw new IllegalArgumentException("Caret offset is outside the word");
        }
        mWord = word;
        mCaretOffset = utf16CaretOffset;
    }

    String getWord() {
        return mWord;
    }

    int getCaretOffset() {
        return mCaretOffset;
    }

    CursorWordEdit deleteBeforeCaret() {
        if (mCaretOffset == 0) {
            return this;
        }

        // As in normal backspace, segment only the text before the caret.
        final BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(mWord.substring(0, mCaretOffset));
        iterator.last();
        final int start = iterator.previous();
        return new CursorWordEdit(mWord.substring(0, start) + mWord.substring(mCaretOffset),
                start);
    }

    CursorWordEdit insert(final CharSequence text) {
        final String inserted = text.toString();
        return new CursorWordEdit(
                mWord.substring(0, mCaretOffset) + inserted + mWord.substring(mCaretOffset),
                mCaretOffset + inserted.length());
    }

    /**
     * Edit relative to the editor's caret. The reconstructed word is for suggestions only, never
     * an absolute replacement range. A delete checks just the characters to remove, not all text.
     */
    CursorWordEdit apply(final RichInputConnection connection, final WordComposer composer,
            final CharSequence insertion, final Function<int[], int[]> coordinates) {
        final CursorWordEdit next = insertion == null ? deleteBeforeCaret() : insert(insertion);
        if (next == this) return null;
        boolean useEditorBackspace = false;
        if (insertion == null) {
            final int length = mCaretOffset - next.mCaretOffset;
            final String expected = mWord.substring(next.mCaretOffset, mCaretOffset);
            final SurroundingText surrounding = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? connection.readSurroundingTextForCursorEdit(length) : null;
            if (connection.hasSlowInputConnection()) return null;
            if (surrounding != null) {
                final int caret = surrounding.getSelectionStart();
                if (caret != surrounding.getSelectionEnd() || caret < length
                        || caret > surrounding.getText().length()
                        || (surrounding.getOffset() >= 0
                                && (long) surrounding.getOffset() + caret != connection.getExpectedSelectionStart())
                        || !TextUtils.equals(expected, surrounding.getText().subSequence(caret - length, caret))) {
                    return null;
                }
            } else {
                if (!TextUtils.equals(connection.readTextImmediatelyBeforeCursor(length), expected)
                        || connection.hasSlowInputConnection()) return null;
                final CharSequence selected = connection.readLiveSelectedText();
                if (connection.hasSlowInputConnection() || !TextUtils.isEmpty(selected)) return null;
                // Null is not proof of a collapsed selection. Keep the predicted word for suggestions only.
                useEditorBackspace = selected == null;
            }
        }
        connection.finishComposingTextForCursorEdit();
        if (useEditorBackspace) {
            connection.sendBackspaceToEditor();
        } else if (insertion == null) {
            connection.deleteTextBeforeCursor(mCaretOffset - next.mCaretOffset);
        } else {
            connection.commitText(insertion, 1);
        }
        final int[] codePoints = StringUtils.toCodePointArray(next.mWord);
        // Existing text has no touch coordinates. As with a reopened word, keep it ineligible
        // for automatic replacement; a separator must be inserted at the actual caret.
        composer.getInputPointers().reset();
        composer.setComposingWord(codePoints, coordinates.apply(codePoints));
        composer.setCursorPositionWithinWord(next.mWord.codePointCount(0, next.mCaretOffset));
        return next;
    }
}
